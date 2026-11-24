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
const LiveFleetTracking = lazy(() => import('./pages/admin/LiveFleetTracking'));
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
            <Route path="live-tracking" element={<LiveFleetTracking />} />
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
