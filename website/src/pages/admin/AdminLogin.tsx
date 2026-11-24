import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { Shield, Lock, User, ArrowLeft, AlertCircle, Eye, EyeOff, Loader2 } from 'lucide-react';
import logoUrl from '../../assets/logo/fleetx-logo.png';
import { useAdminAuth } from '../../context/AdminAuthContext';

export const AdminLogin: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { loginWithCredentials, isAdmin, isLoading: authLoading } = useAdminAuth();

  const [idOrEmail, setIdOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // If already logged in as Admin, redirect immediately
  React.useEffect(() => {
    if (!authLoading && isAdmin) {
      const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/admin';
      navigate(from, { replace: true });
    }
  }, [isAdmin, authLoading, navigate, location]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!idOrEmail.trim() || !password.trim()) {
      setErrorMessage('Please enter both Staff ID / Email and password.');
      return;
    }

    setIsSubmitting(true);
    try {
      await loginWithCredentials(idOrEmail, password);
      const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/admin';
      navigate(from, { replace: true });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Authentication failed. Please verify your credentials.';
      setErrorMessage(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col justify-center py-12 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* Background ambient gradient */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-blue-900/20 via-slate-900 to-slate-950 pointer-events-none" />

      {/* Top back button */}
      <div className="absolute top-6 left-6 z-10">
        <Link
          to="/"
          className="inline-flex items-center gap-2 text-xs font-medium text-slate-400 hover:text-white bg-slate-800/80 hover:bg-slate-800 px-3 py-2 rounded-lg border border-slate-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to FleetX</span>
        </Link>
      </div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md z-10 px-4">
        {/* Brand Header */}
        <div className="text-center">
          <div className="inline-flex items-center justify-center p-2 bg-white rounded-2xl shadow-xl shadow-blue-500/10 mb-4 border border-slate-200">
            <img src={logoUrl} alt="FleetX Logo" className="w-12 h-12 object-contain" />
          </div>
          <h2 className="text-2xl font-bold tracking-tight text-white">
            Fleet<span className="text-blue-400">X</span> Administrator Portal
          </h2>
          <p className="mt-1 text-xs text-slate-400">
            Secure management console for college bus tracking and stop coordinates
          </p>
        </div>

        {/* Login Card */}
        <div className="mt-8 bg-slate-800/90 backdrop-blur-md py-8 px-6 shadow-2xl rounded-2xl sm:px-10 border border-slate-700/80">
          <form className="space-y-5" onSubmit={handleSubmit}>
            {errorMessage && (
              <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-xl flex items-start gap-2.5 text-red-300 text-xs leading-relaxed animate-fade-in">
                <AlertCircle className="w-4 h-4 text-red-400 flex-shrink-0 mt-0.5" />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* Staff ID or Email Input */}
            <div>
              <label htmlFor="staff-id" className="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">
                Staff ID or Email
              </label>
              <div className="relative rounded-xl shadow-sm">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <User className="h-4 w-4" />
                </div>
                <input
                  id="staff-id"
                  name="idOrEmail"
                  type="text"
                  autoComplete="username"
                  required
                  value={idOrEmail}
                  onChange={(e) => setIdOrEmail(e.target.value)}
                  placeholder="e.g. admin26 or admin@college.edu"
                  className="block w-full pl-10 pr-4 py-2.5 bg-slate-900/80 border border-slate-700 rounded-xl text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* Password Input */}
            <div>
              <label htmlFor="password" className="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">
                Password
              </label>
              <div className="relative rounded-xl shadow-sm">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Lock className="h-4 w-4" />
                </div>
                <input
                  id="password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="block w-full pl-10 pr-10 py-2.5 bg-slate-900/80 border border-slate-700 rounded-xl text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-200 focus:outline-none"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {/* Submit Button */}
            <div className="pt-2">
              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full flex justify-center items-center py-2.5 px-4 border border-transparent rounded-xl shadow-lg shadow-blue-500/20 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    Authenticating Admin...
                  </>
                ) : (
                  'Sign In to Dashboard'
                )}
              </button>
            </div>
          </form>

          {/* Security notice footer */}
          <div className="mt-6 pt-5 border-t border-slate-700/60 flex items-center justify-center gap-2 text-[11px] text-slate-400">
            <Shield className="w-3.5 h-3.5 text-blue-400" />
            <span>Authorized transport administration personnel only</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminLogin;
