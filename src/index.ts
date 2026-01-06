import { registerPlugin } from '@capacitor/core';

import type {
  CapUploadCarePlugin,
  UploadCareUploadOptions,
  UploadCareUploadResult,
  UploadCareFile,
  UploadCareDataUriOptions,
  UploadCareProgressEvent
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
  const mergedOptions: UploadCareUploadOptions = {
    ...options,
    mediaType: 'image',
    allowedMimeTypes: options?.allowedMimeTypes ?? ['image/*'],
  };

  const result = await CapUploadCare.openUploader(mergedOptions);

  if (result.cancelled) return null;

  if (!result.success || !result.files?.length) {
    throw new Error(result.errorMessage ?? 'Upload failed or no file returned');
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

/**
 * Convenience helper:
 * - Forces the native picker into video mode via allowedMimeTypes
 * - Returns the first uploaded file
 * - Returns null if the user cancelled
 * - Throws on error
 */
export async function selectAndUploadVideo(
  options?: UploadCareUploadOptions,
): Promise<UploadCareFile | null> {
  const mergedOptions: UploadCareUploadOptions = {
    ...options,
    mediaType: 'video',
    allowedMimeTypes: options?.allowedMimeTypes ?? ['video/*'],
  };

  const result = await CapUploadCare.openUploader(mergedOptions);

  if (result.cancelled) return null;

  if (!result.success || !result.files?.length) {
    throw new Error(result.errorMessage ?? 'Upload failed or no file returned');
  }

  return result.files[0];
}

/**
 * Convenience helper:
 * - Upload an existing base64 data URI (works for both images and videos)
 * - Named "Video" so your app code reads cleanly where you use it
 */
export async function uploadDataUriVideo(
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

/**
 * Convenience helper:
 * Listen for native upload progress events (0..100).
 * Your native side emits: notifyListeners("uploadProgress", { uploadId, progress })
 */
export async function addUploadProgressListener(
  listener: (e: UploadCareProgressEvent) => void,
) {
  return CapUploadCare.addListener('uploadProgress', listener);
}