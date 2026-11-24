import { collection, onSnapshot, QuerySnapshot, DocumentData } from 'firebase/firestore';
import { db } from '../lib/firebase';
import { Route, BusStop } from '../types/admin';

/**
 * Validates whether a bus stop possesses valid, renderable geographic coordinates.
 * Strictly mirrors the Android BusStop.hasCoordinates logic:
 * visibleOnMap && (latitude != 0.0 || longitude != 0.0) && latitude in -90.0..90.0 && longitude in -180.0..180.0
 */
export function checkStopHasCoordinates(stop: Partial<BusStop> | null | undefined): boolean {
  if (!stop) return false;
  if (stop.visibleOnMap === false) return false;
  const lat = stop.latitude;
  const lng = stop.longitude;
  if (typeof lat !== 'number' || typeof lng !== 'number') return false;
  if (isNaN(lat) || isNaN(lng)) return false;
  if (lat === 0 && lng === 0) return false;
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

/**
 * Natural comparison function for bus & route identifiers (e.g. ROUTE-02 before ROUTE-10).
 */
export function naturalRouteSort(a: Route, b: Route): number {
  const numA = parseInt(a.routeId.replace(/\D/g, ''), 10) || 0;
  const numB = parseInt(b.routeId.replace(/\D/g, ''), 10) || 0;
  if (numA !== numB) return numA - numB;
  return a.name.localeCompare(b.name);
}

/**
 * Parses raw Firestore document data into a strongly-typed Route object,
 * ensuring nested stops are sanitized and sorted by stop order.
 */
export function parseRouteDocument(docId: string, data: DocumentData): Route {
  const rawStops: unknown[] = Array.isArray(data.stops) ? data.stops : [];

  const stops: BusStop[] = rawStops.map((item, index) => {
    const s = (typeof item === 'object' && item !== null) ? (item as Record<string, unknown>) : {};
    const lat = typeof s.latitude === 'number' ? s.latitude : Number(s.latitude) || 0.0;
    const lng = typeof s.longitude === 'number' ? s.longitude : Number(s.longitude) || 0.0;
    const order = typeof s.order === 'number' ? s.order : (typeof s.stopOrder === 'number' ? s.stopOrder : index + 1);

    return {
      stopId: String(s.stopId || `STOP-${index + 1}`),
      name: String(s.name || s.stopName || `Stop #${index + 1}`),
      latitude: lat,
      longitude: lng,
      order: order,
      active: s.active !== false,
      visibleOnMap: s.visibleOnMap !== false,
    };
  }).sort((a, b) => a.order - b.order);

  return {
    routeId: data.routeId || docId,
    name: data.name || docId,
    description: data.description || '',
    active: data.active !== false,
    busId: data.busId || '',
    stops: stops,
    schedules: data.schedules,
  };
}

/**
 * Subscribes to real-time updates for the entire routes collection.
 * Cleans up the listener when the caller invokes the returned unsubscribe function.
 */
export function subscribeToRoutes(
  onData: (routes: Route[]) => void,
  onError?: (err: Error) => void
): () => void {
  const routesRef = collection(db, 'routes');

  const unsubscribe = onSnapshot(
    routesRef,
    (snapshot: QuerySnapshot<DocumentData>) => {
      try {
        const routes: Route[] = snapshot.docs
          .map((doc) => parseRouteDocument(doc.id, doc.data()))
          .sort(naturalRouteSort);
        onData(routes);
      } catch (err) {
        console.error('[routesService] Failed parsing routes snapshot:', err);
        if (onError && err instanceof Error) onError(err);
      }
    },
    (error) => {
      console.error('[routesService] Firestore listener error:', error);
      if (onError) onError(error);
    }
  );

  return unsubscribe;
}

/**
 * Calculates aggregate stop statistics from a list of routes.
 */
export function computeRouteStopStats(routes: Route[]) {
  let totalStops = 0;
  let mappedStops = 0;
  let missingStops = 0;
  let activeRoutes = 0;

  for (const r of routes) {
    if (r.active) {
      activeRoutes++;
      for (const s of r.stops) {
        if (s.active) {
          totalStops++;
          if (checkStopHasCoordinates(s)) {
            mappedStops++;
          } else {
            missingStops++;
          }
        }
      }
    }
  }

  const coveragePercent = totalStops > 0 ? Math.round((mappedStops / totalStops) * 100) : 0;

  return {
    totalStops,
    mappedStops,
    missingStops,
    activeRoutes,
    coveragePercent,
  };
}
