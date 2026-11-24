import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AdminAuthProvider } from './context/AdminAuthContext';
import { Home } from './pages/Home';
import { AdminLogin } from './pages/admin/AdminLogin';
import { AdminLayout } from './layouts/AdminLayout';
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { StopCoordinateSubmissions } from './pages/admin/StopCoordinateSubmissions';
import { RoutesAndStops } from './pages/admin/RoutesAndStops';
import { SubmitStopCoordinates } from './pages/student/SubmitStopCoordinates';
import { ProtectedRoute } from './components/admin/ProtectedRoute';

export const App: React.FC = () => {
  return (
    <AdminAuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Landing Page (Preserved 100%) */}
          <Route path="/" element={<Home />} />

          {/* Student Stop Coordinate Crowdsourcing */}
          <Route path="/submit-stop" element={<SubmitStopCoordinates />} />

          {/* Admin Login */}
          <Route path="/admin/login" element={<AdminLogin />} />

          {/* Protected Admin Console */}
          <Route
            path="/admin"
            element={
              <ProtectedRoute>
                <AdminLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<AdminDashboard />} />
            <Route path="submissions" element={<StopCoordinateSubmissions />} />
            <Route path="routes" element={<RoutesAndStops />} />
          </Route>

          {/* Fallback to Home */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AdminAuthProvider>
  );
};

export default App;
