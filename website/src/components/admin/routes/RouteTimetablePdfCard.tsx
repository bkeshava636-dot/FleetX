import React, { useRef, useState } from 'react';
import { doc, setDoc } from 'firebase/firestore';
import {
  FileText,
  Upload,
  ExternalLink,
  Trash2,
  Loader2,
  RefreshCw,
} from 'lucide-react';
import { db } from '../../../lib/firebase';
import { Route } from '../../../types/admin';
import { uploadSchedulePdf, validatePdfFile } from '../../../services/storageService';
import { DeleteConfirmModal } from './DeleteConfirmModal';

interface RouteTimetablePdfCardProps {
  route: Route;
  onToast: (type: 'success' | 'error', text: string) => void;
}

export const RouteTimetablePdfCard: React.FC<RouteTimetablePdfCardProps> = ({
  route,
  onToast,
}) => {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isRemoveOpen, setIsRemoveOpen] = useState(false);

  const hasPdf = Boolean(route.schedulePdfUrl);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    const validationError = validatePdfFile(file);
    if (validationError) {
      onToast('error', validationError);
      return;
    }

    setIsUploading(true);
    try {
      const storagePath = `route_schedules/${route.routeId}_${Date.now()}.pdf`;
      const { url, name } = await uploadSchedulePdf(storagePath, file);

      await setDoc(
        doc(db, 'routes', route.routeId),
        {
          schedulePdfUrl: url,
          schedulePdfName: name,
        },
        { merge: true }
      );

      onToast(
        'success',
        `${hasPdf ? 'Replaced' : 'Uploaded'} timetable PDF for ${route.name || route.routeId}.`
      );
    } catch (err) {
      onToast(
        'error',
        err instanceof Error ? err.message : 'Failed to upload route timetable PDF.'
      );
    } finally {
      setIsUploading(false);
    }
  };

  const handleRemoveConfirm = async () => {
    await setDoc(
      doc(db, 'routes', route.routeId),
      {
        schedulePdfUrl: null,
        schedulePdfName: null,
      },
      { merge: true }
    );
    onToast('success', `Removed timetable PDF for ${route.name || route.routeId}.`);
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 bg-blue-50 text-blue-600 rounded-xl">
            <FileText className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-xs font-bold text-slate-900">Route Timetable PDF</h4>
            <p className="text-[11px] text-slate-500">
              Upload a PDF schedule for {route.routeId} (max 15 MB).
            </p>
          </div>
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept="application/pdf,.pdf"
          onChange={handleFileChange}
          className="hidden"
        />

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={isUploading}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 rounded-xl shadow-sm transition-colors"
        >
          {isUploading ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
          ) : hasPdf ? (
            <RefreshCw className="w-3.5 h-3.5" />
          ) : (
            <Upload className="w-3.5 h-3.5" />
          )}
          <span>{isUploading ? 'Uploading...' : hasPdf ? 'Replace PDF' : 'Upload PDF'}</span>
        </button>
      </div>

      {hasPdf ? (
        <div className="flex items-center justify-between gap-3 p-3 rounded-xl bg-slate-50 border border-slate-200">
          <div className="min-w-0 flex items-center gap-2">
            <FileText className="w-4 h-4 text-emerald-600 shrink-0" />
            <span className="text-xs font-medium text-slate-800 truncate">
              {route.schedulePdfName || `${route.routeId}_timetable.pdf`}
            </span>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <a
              href={route.schedulePdfUrl || '#'}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-semibold text-blue-700 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              <span>Preview / Download</span>
            </a>
            <button
              type="button"
              onClick={() => setIsRemoveOpen(true)}
              disabled={isUploading}
              className="p-1.5 text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
              title="Remove timetable PDF"
              aria-label="Remove timetable PDF"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </div>
      ) : (
        <div className="p-3 rounded-xl bg-slate-50 border border-dashed border-slate-200 text-xs text-slate-500">
          No route-specific timetable PDF uploaded yet.
        </div>
      )}

      <DeleteConfirmModal
        isOpen={isRemoveOpen}
        title="Remove Route Timetable PDF"
        itemName={route.schedulePdfName || `${route.routeId}_timetable.pdf`}
        itemId={route.routeId}
        description="This clears the timetable PDF link from the route document. Students will no longer see this route PDF."
        confirmLabel="Remove PDF"
        onClose={() => setIsRemoveOpen(false)}
        onConfirm={handleRemoveConfirm}
      />
    </div>
  );
};
