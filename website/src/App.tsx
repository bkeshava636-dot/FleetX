import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Home } from './pages/Home';
import { RouteLoadingFallback } from './components/common/RouteLoadingFallback';

// Lazy-loaded routes and administration scope
// Ensures public landing page visitors download 0 KB of Firebase or Google Maps code
const SubmitStopCoordinates = lazy(() => import('./pages/student/SubmitStopCoordinates'));
const AdminRoot = lazy(() => import('./layouts/AdminRoot'));
const AdminLogin = lazy(() => import('./pages/admin/AdminLogin'));
const AdminLayout = lazy(() => import('./layouts/AdminLayout'));
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'));
const StopCoordinateSubmissions = lazy(() => import('./pages/admin/StopCoordinateSubmissions'));
const RoutesAndStops = lazy(() => import('./pages/admin/RoutesAndStops'));
const FleetManagement = lazy(() => import('./pages/admin/FleetManagement'));
const DriverManagement = lazy(() => import('./pages/admin/DriverManagement'));
const LiveFleetTracking = lazy(() => import('./pages/admin/LiveFleetTracking'));
const BusSwaps = lazy(() => import('./pages/admin/BusSwaps'));
const TemporaryAssignments = lazy(() => import('./pages/admin/TemporaryAssignments'));
const CollegeCalendar = lazy(() => import('./pages/admin/CollegeCalendar'));
const Announcements = lazy(() => import('./pages/admin/Announcements'));
const StudentManagement = lazy(() => import('./pages/admin/StudentManagement'));
const AnalyticsReports = lazy(() => import('./pages/admin/AnalyticsReports'));
const AuditLogs = lazy(() => import('./pages/admin/AuditLogs'));
const AdminSettings = lazy(() => import('./pages/admin/AdminSettings'));
const ProtectedRoute = lazy(() => import('./components/admin/ProtectedRoute').then((m) => ({ default: m.ProtectedRoute })));

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public Landing Page (Preserved 100%, fast initial paint without Firebase) */}
        <Route path="/" element={<Home />} />

        {/* Student Stop Coordinate Crowdsourcing (Lazy-loaded) */}
        <Route
          path="/submit-stop"
          element={
            <Suspense fallback={<RouteLoadingFallback message="Loading Stop Portal..." />}>
              <SubmitStopCoordinates />
            </Suspense>
          }
        />

        {/* Administration Portal Scope (Lazy-loaded Firebase & Auth context) */}
        <Route
          path="/admin"
          element={
            <Suspense fallback={<RouteLoadingFallback message="Loading Administrator Portal..." />}>
              <AdminRoot />
            </Suspense>
          }
        >
          {/* Admin Login Route */}
          <Route path="login" element={<AdminLogin />} />

          {/* Protected Management Routes */}
          <Route
            element={
              <ProtectedRoute>
                <AdminLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<AdminDashboard />} />
            <Route path="submissions" element={<StopCoordinateSubmissions />} />
            <Route path="routes" element={<RoutesAndStops />} />
            <Route path="fleet" element={<FleetManagement />} />
            <Route path="drivers" element={<DriverManagement />} />
            <Route path="live-tracking" element={<LiveFleetTracking />} />
            <Route path="bus-swaps" element={<BusSwaps />} />
            <Route path="temporary-assignments" element={<TemporaryAssignments />} />
            <Route path="calendar" element={<CollegeCalendar />} />
            <Route path="announcements" element={<Announcements />} />
            <Route path="students" element={<StudentManagement />} />
            <Route path="analytics" element={<AnalyticsReports />} />
            <Route path="audit" element={<AuditLogs />} />
            <Route path="settings" element={<AdminSettings />} />
          </Route>
        </Route>

        {/* Direct /admin/login alias */}
        <Route path="/admin/login" element={<Navigate to="/admin/login" replace />} />

        {/* Fallback to Home */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default App;
