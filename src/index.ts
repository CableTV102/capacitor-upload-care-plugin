import { registerPlugin } from '@capacitor/core';

import type {
  CapUploadCarePlugin,
  LocalPickedMedia,
  PickMediaOptions,
  UploadCareUploadOptions,
  UploadCareUploadResult,
  UploadCareFile,
  UploadCareDataUriOptions,
  UploadCareProgressEvent,
  UploadPickedOptions,
} from './definitions';

const CapUploadCare = registerPlugin<CapUploadCarePlugin>('CapUploadCare', {
  web: () => import('./web').then((m) => new m.CapUploadCareWeb()),
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
    throw new Error(result.errorMessage ?? 'Upload has failed or no file returned');
  }

  return result.files[0];
}

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
    throw new Error(result.errorMessage ?? 'Upload has failed or no file returned');
  }

  return result.files[0];
}

/**
 * New flow: pick now, upload later.
 * - Returns LocalPickedMedia (localId + local uri for preview)
 */
export async function pickMedia(
  options?: PickMediaOptions,
): Promise<LocalPickedMedia> {
  return CapUploadCare.pickMedia(options);
}

/**
 * New flow: upload something you picked earlier, using localId
 */
export async function uploadPicked(
  options: UploadPickedOptions,
): Promise<UploadCareUploadResult> {
  return CapUploadCare.uploadPicked(options);
}

/**
 * Listen for native upload progress events (0..100).
 */
export async function addUploadProgressListener(
  listener: (e: UploadCareProgressEvent) => void,
) {
  return CapUploadCare.addListener('uploadProgress', listener);
}