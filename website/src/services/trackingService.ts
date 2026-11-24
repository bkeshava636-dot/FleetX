import { ref, onValue, off, DataSnapshot } from 'firebase/database';
import { collection, onSnapshot, DocumentData, QuerySnapshot } from 'firebase/firestore';
import { db, rtdb } from '../lib/firebase';
import { Route, Bus, BusStop } from '../types/admin';
import {
  FleetBus,
  LiveTelemetry,
  evaluateBusGpsState,
  speedMpsToKmh,
  bearingToCompassDirection,
} from '../types/tracking';
import { checkStopHasCoordinates, parseRouteDocument } from './routesService';

/**
 * Natural comparison for bus IDs (e.g., BUS-02 before BUS-10).
 */
export function naturalBusSort(a: FleetBus, b: FleetBus): number {
  const numA = parseInt(a.busNumber.replace(/\D/g, ''), 10) || parseInt(a.busId.replace(/\D/g, ''), 10) || 0;
  const numB = parseInt(b.busNumber.replace(/\D/g, ''), 10) || parseInt(b.busId.replace(/\D/g, ''), 10) || 0;
  if (numA !== numB) return numA - numB;
  return a.busNumber.localeCompare(b.busNumber);
}

export interface LiveFleetSubscriptionCallbacks {
  onFleetUpdate: (buses: FleetBus[], routesMap: Map<string, Route>) => void;
  onError: (error: Error) => void;
}

/**
 * Subscribes to real-time fleet operations.
 * Attaches exactly ONE parent-level RTDB listener to /live_locations,
 * along with Firestore listeners for bus and route metadata.
 * Caches routes in memory to eliminate redundant reads and re-renders.
 */
export function subscribeToLiveFleet({
  onFleetUpdate,
  onError,
}: LiveFleetSubscriptionCallbacks): () => void {
  let latestBuses: Bus[] = [];
  let latestRoutesMap = new Map<string, Route>();
  let latestTelemetryMap: Record<string, LiveTelemetry> = {};

  const notifyUpdate = () => {
    try {
      const mergedList: FleetBus[] = latestBuses.map((bus) => {
        const telemetry = latestTelemetryMap[bus.busId] || null;
        const assignedRoute = latestRoutesMap.get(bus.routeId);

        const speedMps = telemetry && typeof telemetry.speed === 'number' ? telemetry.speed : 0;
        const bearing = telemetry && typeof telemetry.bearing === 'number' ? telemetry.bearing : 0;
        const lastUpdated = telemetry ? Number(telemetry.lastUpdated) || 0 : 0;

        return {
          busId: bus.busId,
          busNumber: bus.busNumber || bus.busId,
          registrationNumber: bus.registrationNumber || 'Unregistered',
          capacity: bus.capacity || 0,
          active: bus.active !== false,
          assignedRouteId: bus.routeId || '',
          assignedRouteName: assignedRoute ? assignedRoute.name : (bus.routeId || 'Unassigned'),
          assignedDriverId: bus.driverId || null,
          assignedDriverName: bus.driverName || null,

          telemetry: telemetry,
          displayState: evaluateBusGpsState(telemetry),

          speedMps: speedMps,
          speedKmh: speedMpsToKmh(speedMps),
          bearingDegrees: bearing,
          compassDirection: bearingToCompassDirection(bearing),
          lastUpdatedMs: lastUpdated,
        };
      });

      // Priority sort: ACTIVE / ARRIVED first, then WAITING, STALE, INACTIVE; secondary sort: natural bus number
      const statePriority: Record<string, number> = {
        ACTIVE: 1,
        ARRIVED_AT_CAMPUS: 2,
        WAITING_FOR_GPS: 3,
        STALE: 4,
        INACTIVE: 5,
      };

      mergedList.sort((a, b) => {
        const pA = statePriority[a.displayState] || 99;
        const pB = statePriority[b.displayState] || 99;
        if (pA !== pB) return pA - pB;
        return naturalBusSort(a, b);
      });

      onFleetUpdate(mergedList, latestRoutesMap);
    } catch (err) {
      console.error('[trackingService] Error merging fleet telemetry:', err);
      if (err instanceof Error) onError(err);
    }
  };

  // 1. Single Parent RTDB Listener for /live_locations
  const liveLocationsRef = ref(rtdb, 'live_locations');
  const rtdbCallback = (snapshot: DataSnapshot) => {
    const val = snapshot.val();
    if (val && typeof val === 'object') {
      latestTelemetryMap = val as Record<string, LiveTelemetry>;
    } else {
      latestTelemetryMap = {};
    }
    notifyUpdate();
  };
  onValue(liveLocationsRef, rtdbCallback, (err) => {
    console.error('[trackingService] RTDB live_locations listener error:', err);
    onError(err);
  });

  // 2. Firestore Listener for buses collection
  const busesColRef = collection(db, 'buses');
  const unsubBuses = onSnapshot(
    busesColRef,
    (snapshot: QuerySnapshot<DocumentData>) => {
      latestBuses = snapshot.docs.map((d) => {
        const data = d.data();
        return {
          busId: data.busId || d.id,
          busNumber: data.busNumber || data.busId || d.id,
          registrationNumber: data.registrationNumber || '',
          routeId: data.routeId || '',
          driverId: data.driverId || null,
          driverName: data.driverName || null,
          capacity: Number(data.capacity) || 0,
          active: data.active !== false,
        };
      });
      notifyUpdate();
    },
    (err) => {
      console.error('[trackingService] Firestore buses listener error:', err);
      onError(err);
    }
  );

  // 3. Firestore Listener for routes collection (with in-memory cache)
  const routesColRef = collection(db, 'routes');
  const unsubRoutes = onSnapshot(
    routesColRef,
    (snapshot: QuerySnapshot<DocumentData>) => {
      const map = new Map<string, Route>();
      snapshot.docs.forEach((d) => {
        const parsed = parseRouteDocument(d.id, d.data());
        map.set(parsed.routeId, parsed);
      });
      latestRoutesMap = map;
      notifyUpdate();
    },
    (err) => {
      console.error('[trackingService] Firestore routes listener error:', err);
      onError(err);
    }
  );

  // Clean unmount teardown
  return () => {
    off(liveLocationsRef, 'value', rtdbCallback);
    unsubBuses();
    unsubRoutes();
  };
}

/**
 * Extracts verified stop coordinates for route polyline overlay.
 */
export function getRouteStopsPolylinePoints(
  route: Route | undefined | null
): { path: { lat: number; lng: number }[]; stops: BusStop[] } {
  if (!route || !Array.isArray(route.stops)) {
    return { path: [], stops: [] };
  }

  const validStops = route.stops
    .filter((s) => checkStopHasCoordinates(s))
    .sort((a, b) => a.order - b.order);

  const path = validStops.map((s) => ({
    lat: s.latitude,
    lng: s.longitude,
  }));

  return { path, stops: validStops };
}
