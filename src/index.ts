import { registerPlugin } from '@capacitor/core';

import type {
  CapUploadCarePlugin,
  UploadCareUploadOptions,
  UploadCareUploadResult,
  UploadCareFile,
  UploadCareDataUriOptions,
} from './definitions';

const CapUploadCare = registerPlugin<CapUploadCarePlugin>('CapUploadCare', {
  web: () => import('./web').then(m => new m.CapUploadCareWeb()),
});

export * from './definitions';
export { CapUploadCare };

/**
 * Convenience helper for most Ionic use cases:
 * - Opens the native uploader
 * - Returns the first uploaded file
 * - Returns null if the user cancelled
 * - Throws on error
 */
export async function selectAndUploadImage(
  options?: UploadCareUploadOptions,
): Promise<UploadCareFile | null> {
  const result: UploadCareUploadResult = await CapUploadCare.openUploader(options);

  const cancelled = result.cancelled ?? false;
  const hasFiles = Array.isArray(result.files) && result.files.length > 0;

  const success =
    typeof result.success === 'boolean'
      ? result.success
      : !cancelled && hasFiles;

  if (cancelled) {
    return null;
  }

  if (!success || !hasFiles) {
    const message = result.errorMessage ?? 'Upload failed or no file returned';
    throw new Error(message);
  }

  return result.files[0];
}

/**
 * Convenience helper to upload an existing base64 data URI.
 * - Expects a full data URI: data:image/jpeg;base64,...
 * - Returns the first uploaded file
 * - Throws on error or if no file is returned
 */
export async function uploadDataUriImage(
  options: UploadCareDataUriOptions,
): Promise<UploadCareFile> {
  const result: UploadCareUploadResult = await CapUploadCare.uploadDataUri(options);

  const cancelled = result.cancelled ?? false;
  const hasFiles = Array.isArray(result.files) && result.files.length > 0;

  const success =
    typeof result.success === 'boolean'
      ? result.success
      : !cancelled && hasFiles;

  if (cancelled) {
    throw new Error('Upload was cancelled');
  }

  if (!success || !hasFiles) {
    const message = result.errorMessage ?? 'Upload has failed or no file returned';
    throw new Error(message);
  }

  return result.files[0];
}