import { getDownloadURL, ref, uploadBytes } from 'firebase/storage';
import { storage } from '../lib/firebase';

const MAX_PDF_BYTES = 15 * 1024 * 1024; // 15 MB

export function validatePdfFile(file: File): string | null {
  const nameLower = file.name.toLowerCase();
  const isPdfType = file.type === 'application/pdf' || nameLower.endsWith('.pdf');
  if (!isPdfType) {
    return 'Only PDF files (.pdf) are allowed.';
  }
  if (file.size <= 0) {
    return 'Selected PDF file is empty.';
  }
  if (file.size > MAX_PDF_BYTES) {
    return 'PDF file size must be 15 MB or smaller.';
  }
  return null;
}

export async function uploadSchedulePdf(
  storagePath: string,
  file: File
): Promise<{ url: string; name: string }> {
  const err = validatePdfFile(file);
  if (err) throw new Error(err);

  const storageRef = ref(storage, storagePath);
  await uploadBytes(storageRef, file, { contentType: 'application/pdf' });
  const url = await getDownloadURL(storageRef);
  return { url, name: file.name };
}
