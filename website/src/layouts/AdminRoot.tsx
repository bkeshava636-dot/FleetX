import React from 'react';
import { Outlet } from 'react-router-dom';
import { AdminAuthProvider } from '../context/AdminAuthContext';

/**
 * Route-level scope provider for all administrator modules.
 * Isolates Firebase Authentication state and initialization so that
 * public landing page visitors never download Firebase JavaScript.
 */
export const AdminRoot: React.FC = () => {
  return (
    <AdminAuthProvider>
      <Outlet />
    </AdminAuthProvider>
  );
};

export default AdminRoot;
