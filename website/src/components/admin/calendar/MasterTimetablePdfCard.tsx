import React, { useRef, useState } from 'react';
import { deleteField, doc, writeBatch } from 'firebase/firestore';
import {
  FileText,
  Upload,
  ExternalLink,
  Trash2,
  Loader2,
  RefreshCw,
  Link as LinkIcon,
} from 'lucide-react';
import { db } from '../../../lib/firebase';
import { RouteSchedule } from '../../../types/admin';
import { uploadSchedulePdf, validatePdfFile } from '../../../services/storageService';
import { DeleteConfirmModal } from '../routes/DeleteConfirmModal';

interface MasterTimetablePdfCardProps {
  schedule: RouteSchedule;
  updatedBy: string;
  onToast: (type: 'success' | 'error', text: string) => void;
}

export const MasterTimetablePdfCard: React.FC<MasterTimetablePdfCardProps> = ({
  schedule,
  updatedBy,
  onToast,
}) => {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isRemoveOpen, setIsRemoveOpen] = useState(false);
  const [externalUrl, setExternalUrl] = useState('');
  const [externalName, setExternalName] = useState('');

  const hasPdf = Boolean(schedule.allRoutesPdfUrl);

  const persistMasterPdf = async (url: string, name: string) => {
    const batch = writeBatch(db);
    const payload = {
      allRoutesPdfUrl: url,
      allRoutesPdfName: name,
      updatedAt: Date.now(),
      updatedBy,
    };
    batch.set(doc(db, 'calendar_exceptions', 'operating_schedule'), payload, { merge: true });
    batch.set(doc(db, 'operating_schedule', 'global'), payload, { merge: true });
    await batch.commit();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    const validationError = validatePdfFile(file);
    if (validationError) {
      onToast('error', validationError);
      return;
    }

    setIsSaving(true);
    try {
      const storagePath = `route_schedules/master_all_routes_${Date.now()}.pdf`;
      const { url, name } = await uploadSchedulePdf(storagePath, file);
      await persistMasterPdf(url, name);
      onToast('success', `${hasPdf ? 'Replaced' : 'Uploaded'} master all-routes timetable PDF.`);
    } catch (err) {
      onToast(
        'error',
        err instanceof Error ? err.message : 'Failed to upload master timetable PDF.'
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleSaveExternalUrl = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedUrl = externalUrl.trim();
    if (!trimmedUrl) {
      onToast('error', 'Please enter a valid PDF or Google Drive URL.');
      return;
    }

    let parsed: URL;
    try {
      parsed = new URL(trimmedUrl);
    } catch {
      onToast('error', 'Please enter a valid http:// or https:// URL.');
      return;
    }

    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      onToast('error', 'URL must start with http:// or https://.');
      return;
    }

    const displayName = externalName.trim() || 'Master All-Routes Timetable (Link)';

    setIsSaving(true);
    try {
      await persistMasterPdf(trimmedUrl, displayName);
      setExternalUrl('');
      setExternalName('');
      onToast('success', 'Saved master timetable URL.');
    } catch (err) {
      onToast(
        'error',
        err instanceof Error ? err.message : 'Failed to save master timetable URL.'
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveConfirm = async () => {
    const batch = writeBatch(db);
    const payload = {
      allRoutesPdfUrl: deleteField(),
      allRoutesPdfName: deleteField(),
      updatedAt: Date.now(),
      updatedBy,
    };
    batch.set(doc(db, 'calendar_exceptions', 'operating_schedule'), payload, { merge: true });
    batch.set(doc(db, 'operating_schedule', 'global'), payload, { merge: true });
    await batch.commit();
    onToast('success', 'Removed master all-routes timetable PDF.');
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-blue-50 text-blue-600 rounded-xl">
            <FileText className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900">
              Master All-Routes Timetable PDF
            </h3>
            <p className="text-xs text-slate-500">
              Upload a master PDF (max 15 MB) or link a Web / Google Drive URL synced to all students.
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
          disabled={isSaving}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 rounded-xl shadow-sm transition-colors"
        >
          {isSaving ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : hasPdf ? (
            <RefreshCw className="w-4 h-4" />
          ) : (
            <Upload className="w-4 h-4" />
          )}
          <span>{isSaving ? 'Saving...' : hasPdf ? 'Replace PDF' : 'Upload Master PDF'}</span>
        </button>
      </div>

      {hasPdf ? (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3.5 rounded-xl bg-slate-50 border border-slate-200">
          <div className="min-w-0 flex items-center gap-2.5">
            <FileText className="w-4 h-4 text-emerald-600 shrink-0" />
            <div className="min-w-0">
              <p className="text-xs font-semibold text-slate-800 truncate">
                {schedule.allRoutesPdfName || 'Master All-Routes Timetable.pdf'}
              </p>
              <p className="text-[11px] text-slate-500 truncate">
                {schedule.allRoutesPdfUrl}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <a
              href={schedule.allRoutesPdfUrl || '#'}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-blue-700 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              <span>Preview / Download</span>
            </a>
            <button
              type="button"
              onClick={() => setIsRemoveOpen(true)}
              disabled={isSaving}
              className="p-1.5 text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
              title="Remove master timetable PDF"
              aria-label="Remove master timetable PDF"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </div>
      ) : (
        <div className="p-3.5 rounded-xl bg-slate-50 border border-dashed border-slate-200 text-xs text-slate-500">
          No master timetable PDF or external link configured yet.
        </div>
      )}

      <form
        onSubmit={handleSaveExternalUrl}
        className="grid grid-cols-1 sm:grid-cols-12 gap-2.5 pt-2 border-t border-slate-100 text-xs"
      >
        <div className="sm:col-span-6">
          <input
            type="url"
            value={externalUrl}
            onChange={(e) => setExternalUrl(e.target.value)}
            placeholder="Or paste Web / Google Drive PDF URL (https://...)"
            disabled={isSaving}
            className="w-full border border-slate-200 rounded-xl px-3 py-2 bg-white"
          />
        </div>
        <div className="sm:col-span-4">
          <input
            type="text"
            value={externalName}
            onChange={(e) => setExternalName(e.target.value)}
            placeholder="Display name (optional)"
            disabled={isSaving}
            className="w-full border border-slate-200 rounded-xl px-3 py-2 bg-white"
          />
        </div>
        <div className="sm:col-span-2">
          <button
            type="submit"
            disabled={isSaving || !externalUrl.trim()}
            className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 bg-slate-800 hover:bg-slate-900 text-white font-semibold rounded-xl disabled:opacity-50 transition-colors"
          >
            <LinkIcon className="w-3.5 h-3.5" />
            <span>Save URL</span>
          </button>
        </div>
      </form>

      <DeleteConfirmModal
        isOpen={isRemoveOpen}
        title="Remove Master Timetable PDF"
        itemName={schedule.allRoutesPdfName || 'Master All-Routes Timetable'}
        description="This removes the master all-routes timetable PDF from both operating schedule documents while preserving morning and evening operating hours."
        confirmLabel="Remove PDF"
        onClose={() => setIsRemoveOpen(false)}
        onConfirm={handleRemoveConfirm}
      />
    </div>
  );
};
