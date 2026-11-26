export type UserRole = 'ADMIN' | 'DRIVER' | 'STUDENT';

export interface UserProfile {
  uid: string;
  customId: string;
  name: string;
  email: string;
  role: UserRole;
  assignedBusId?: string | null;
  active: boolean;
}

export interface BusStop {
  stopId: string;
  name: string;
  latitude: number;
  longitude: number;
  order: number;
  active: boolean;
  visibleOnMap: boolean;
}

export interface RouteScheduleWindow {
  startTime: string;
  endTime: string;
  enabled: boolean;
}

export interface RouteSchedule {
  morning: RouteScheduleWindow;
  evening: RouteScheduleWindow;
  allRoutesPdfUrl?: string | null;
  allRoutesPdfName?: string | null;
}

export interface Route {
  routeId: string;
  name: string;
  description: string;
  active: boolean;
  busId: string;
  stops: BusStop[];
  schedules?: RouteSchedule;
  schedulePdfUrl?: string | null;
  schedulePdfName?: string | null;
}

export interface Bus {
  busId: string;
  busNumber: string;
  registrationNumber: string;
  routeId: string;
  driverId?: string | null;
  driverName?: string | null;
  capacity: number;
  active: boolean;
}

export interface LiveLocationTelemetry {
  busId: string;
  latitude: number;
  longitude: number;
  trackingStatus: 'ACTIVE' | 'INACTIVE' | 'WAITING_FOR_GPS' | 'STALE' | 'ARRIVED_AT_CAMPUS';
  lastUpdated: number;
}

export interface DashboardStats {
  totalBuses: number;
  activeBuses: number;
  totalDrivers: number;
  activeRoutes: number;
  totalStops: number;
  stopsWithCoordinates: number;
  stopsMissingCoordinates: number;
  transmittingBuses: number;
  pendingStopSubmissions: number;
  pendingDriverRequests: number;
  lastUpdatedMs: number;
}
