import {
  collection,
  onSnapshot,
  query,
  where,
  QuerySnapshot,
  DocumentData,
} from 'firebase/firestore';
import { ref, onValue, DataSnapshot } from 'firebase/database';
import { db, rtdb } from '../lib/firebase';
import { DashboardStats, LiveLocationTelemetry } from '../types/admin';
import { parseRouteDocument, computeRouteStopStats, naturalRouteSort } from './routesService';

// Real-time GPS freshness threshold: 90 seconds (synchronized with Android LiveLocation.isStale)
export const GPS_STALE_THRESHOLD_MS = 90_000;

export interface DashboardListenerCallbacks {
  onData: (stats: DashboardStats) => void;
  onError?: (err: Error) => void;
}

/**
 * Subscribes to all operational streams required by the Transit Dashboard.
 * Efficiently aggregates metrics in real-time and returns a cleanup function
 * that disposes all 6 listeners upon component unmount.
 */
export function subscribeToDashboardMetrics(callbacks: DashboardListenerCallbacks): () => void {
  const { onData, onError } = callbacks;

  // Intermediate state store
  const state = {
    totalBuses: 0,
    activeBuses: 0,
    totalDrivers: 0,
    activeRoutes: 0,
    totalStops: 0,
    stopsWithCoordinates: 0,
    stopsMissingCoordinates: 0,
    transmittingBuses: 0,
    pendingStopSubmissions: 0,
    pendingDriverRequests: 0,
    lastUpdatedMs: Date.now(),
  };

  const notify = () => {
    state.lastUpdatedMs = Date.now();
    onData({ ...state });
  };

  const unsubscribes: Array<() => void> = [];

  // 1. Listen to buses collection
  try {
    const busesRef = collection(db, 'buses');
    const unsubBuses = onSnapshot(
      busesRef,
      (snapshot: QuerySnapshot<DocumentData>) => {
        state.totalBuses = snapshot.size;
        state.activeBuses = snapshot.docs.filter((d) => {
          const data = d.data();
          return data.active !== false && data.isActive !== false;
        }).length;
        notify();
      },
      (err) => {
        console.warn('[dashboardService] Error listening to buses:', err);
        if (onError) onError(err);
      }
    );
    unsubscribes.push(unsubBuses);
  } catch (err) {
    console.error('[dashboardService] Failed to attach buses listener:', err);
  }

  // 2. Listen to active drivers in users collection
  try {
    const driversQuery = query(
      collection(db, 'users'),
      where('role', '==', 'DRIVER'),
      where('active', '==', true)
    );
    const unsubDrivers = onSnapshot(
      driversQuery,
      (snapshot: QuerySnapshot<DocumentData>) => {
        state.totalDrivers = snapshot.size;
        notify();
      },
      (err) => {
        console.warn('[dashboardService] Error listening to drivers:', err);
        // Do not crash the entire dashboard if users collection has restricted read
      }
    );
    unsubscribes.push(unsubDrivers);
  } catch (err) {
    console.warn('[dashboardService] Failed to attach drivers listener:', err);
  }

  // 3. Listen to routes collection (calculates stops and coverage dynamically)
  try {
    const routesRef = collection(db, 'routes');
    const unsubRoutes = onSnapshot(
      routesRef,
      (snapshot: QuerySnapshot<DocumentData>) => {
        const routes = snapshot.docs
          .map((doc) => parseRouteDocument(doc.id, doc.data()))
          .sort(naturalRouteSort);
        const routeStats = computeRouteStopStats(routes);

        state.activeRoutes = routeStats.activeRoutes;
        state.totalStops = routeStats.totalStops;
        state.stopsWithCoordinates = routeStats.mappedStops;
        state.stopsMissingCoordinates = routeStats.missingStops;
        notify();
      },
      (err) => {
        console.warn('[dashboardService] Error listening to routes:', err);
        if (onError) onError(err);
      }
    );
    unsubscribes.push(unsubRoutes);
  } catch (err) {
    console.error('[dashboardService] Failed to attach routes listener:', err);
  }

  // 4. Listen to pending stop coordinate submissions
  try {
    const submissionsQuery = query(
      collection(db, 'stop_location_submissions'),
      where('status', '==', 'PENDING')
    );
    const unsubSubmissions = onSnapshot(
      submissionsQuery,
      (snapshot: QuerySnapshot<DocumentData>) => {
        state.pendingStopSubmissions = snapshot.size;
        notify();
      },
      (err) => {
        // In initial phase if collection does not yet exist or has no documents
        console.warn('[dashboardService] Submissions listener note:', err.message);
        state.pendingStopSubmissions = 0;
        notify();
      }
    );
    unsubscribes.push(unsubSubmissions);
  } catch (err) {
    console.warn('[dashboardService] Failed to attach submissions listener:', err);
  }

  // 5. Listen to pending driver stop requests
  try {
    const stopRequestsQuery = query(
      collection(db, 'stop_requests'),
      where('status', '==', 'PENDING')
    );
    const unsubStopRequests = onSnapshot(
      stopRequestsQuery,
      (snapshot: QuerySnapshot<DocumentData>) => {
        state.pendingDriverRequests = snapshot.size;
        notify();
      },
      (err) => {
        console.warn('[dashboardService] Stop requests listener note:', err.message);
        state.pendingDriverRequests = 0;
        notify();
      }
    );
    unsubscribes.push(unsubStopRequests);
  } catch (err) {
    console.warn('[dashboardService] Failed to attach stop_requests listener:', err);
  }

  // 6. Listen to RTDB live_locations for real-time GPS telemetry
  try {
    const liveLocationsRef = ref(rtdb, 'live_locations');
    const unsubRtdb = onValue(
      liveLocationsRef,
      (snapshot: DataSnapshot) => {
        const val = snapshot.val();
        if (!val || typeof val !== 'object') {
          state.transmittingBuses = 0;
          notify();
          return;
        }

        const now = Date.now();
        let activeGpsCount = 0;

        Object.keys(val).forEach((key) => {
          const loc = val[key] as Partial<LiveLocationTelemetry>;
          if (loc && loc.trackingStatus === 'ACTIVE') {
            const lastUpdated = typeof loc.lastUpdated === 'number' ? loc.lastUpdated : 0;
            const hasCoords = (loc.latitude !== 0 || loc.longitude !== 0) &&
              typeof loc.latitude === 'number' && typeof loc.longitude === 'number';
            const isFresh = (now - lastUpdated) <= GPS_STALE_THRESHOLD_MS;

            if (hasCoords && isFresh) {
              activeGpsCount++;
            }
          }
        });

        state.transmittingBuses = activeGpsCount;
        notify();
      },
      (err) => {
        console.warn('[dashboardService] RTDB live_locations listener note:', err);
        state.transmittingBuses = 0;
        notify();
      }
    );

    unsubscribes.push(() => unsubRtdb());
  } catch (err) {
    console.warn('[dashboardService] Failed to attach RTDB listener:', err);
  }

  // Return master cleanup callback
  return () => {
    unsubscribes.forEach((unsub) => {
      try {
        unsub();
      } catch (e) {
        console.warn('[dashboardService] Error during listener cleanup:', e);
      }
    });
  };
}
