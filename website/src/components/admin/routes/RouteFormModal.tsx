import React, { useEffect, useState } from 'react';
import { X, Route as RouteIcon, AlertCircle, Loader2 } from 'lucide-react';
import { Route } from '../../../types/admin';
import {
  createRoute,
  updateRouteDetails,
  validateRouteInput,
} from '../../../services/routesService';

interface RouteFormModalProps {
  isOpen: boolean;
  mode: 'create' | 'edit';
  initialRoute?: Route | null;
  onClose: () => void;
  onSuccess: (message: string, routeId?: string) => void;
}

function normalizeValidationResult(
  result: string | null | { valid: boolean; error?: string | null }
): string | null {
  if (typeof result === 'string') return result;
  if (result && typeof result === 'object' && 'valid' in result) {
    return result.valid ? null : result.error || 'Invalid route input.';
  }
  return null;
}

export const RouteFormModal: React.FC<RouteFormModalProps> = ({
  isOpen,
  mode,
  initialRoute,
  onClose,
  onSuccess,
}) => {
  const [routeId, setRouteId] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [active, setActive] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    if (mode === 'edit' && initialRoute) {
      setRouteId(initialRoute.routeId);
      setName(initialRoute.name);
      setDescription(initialRoute.description || '');
      setActive(initialRoute.active);
    } else {
      setRouteId('');
      setName('');
      setDescription('');
      setActive(true);
    }
  }, [isOpen, mode, initialRoute]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const cleanRouteId = routeId.trim();
    const cleanName = name.trim();
    const cleanDescription = description.trim();

    const validationErr = normalizeValidationResult(
      validateRouteInput({
        routeId: cleanRouteId,
        name: cleanName,
        description: cleanDescription,
        isEdit: mode === 'edit',
      })
    );

    if (validationErr) {
      setError(validationErr);
      return;
    }

    setIsSubmitting(true);
    try {
      if (mode === 'create') {
        await createRoute({
          routeId: cleanRouteId,
          name: cleanName,
          description: cleanDescription,
          active,
        });
        onSuccess(`Route "${cleanRouteId}" created successfully.`, cleanRouteId);
      } else if (initialRoute) {
        await updateRouteDetails(initialRoute.routeId, {
          name: cleanName,
          description: cleanDescription,
          active,
        });
        onSuccess(`Route "${initialRoute.routeId}" updated.`, initialRoute.routeId);
      }
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save route.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/70">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-indigo-100 text-indigo-700 rounded-xl">
              <RouteIcon className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {mode === 'create' ? 'Create New Route' : 'Edit Route Details'}
              </h2>
              <p className="text-xs text-slate-500">
                {mode === 'create'
                  ? 'Define a new transit route document in Firestore'
                  : `Updating metadata for ${initialRoute?.routeId}`}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="flex items-start gap-3 p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs font-medium">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Route ID <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              required
              placeholder="e.g. ROUTE-01"
              value={routeId}
              onChange={(e) => setRouteId(e.target.value)}
              disabled={isSubmitting || mode === 'edit'}
              className={`w-full px-3.5 py-2.5 border rounded-xl text-sm font-mono transition-all focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                mode === 'edit'
                  ? 'bg-slate-100 border-slate-200 text-slate-500 cursor-not-allowed'
                  : 'bg-white border-slate-300 text-slate-900 placeholder:text-slate-400'
              }`}
            />
            <p className="text-[11px] text-slate-500 mt-1">
              {mode === 'edit'
                ? 'Route ID is the canonical Firestore document key and cannot be changed.'
                : '2-64 characters: letters, numbers, hyphens, or underscores.'}
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Route Name <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              required
              placeholder="e.g. Route 1 - North Campus Express"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Description
            </label>
            <textarea
              rows={3}
              placeholder="Optional corridor overview or shift notes..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all resize-none"
            />
          </div>

          <label className="flex items-center justify-between p-3.5 bg-slate-50 border border-slate-200 rounded-xl cursor-pointer">
            <div>
              <span className="text-xs font-bold text-slate-800 block">Route Active</span>
              <span className="text-[11px] text-slate-500">
                Active routes are visible to drivers and students in the mobile app.
              </span>
            </div>
            <input
              type="checkbox"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
              disabled={isSubmitting}
              className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
            />
          </label>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 rounded-xl shadow-sm transition-colors"
            >
              {isSubmitting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
              <span>{mode === 'create' ? 'Create Route' : 'Save Changes'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
