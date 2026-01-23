import { registerPlugin } from '@capacitor/core';

import type {
  CapUploadCarePlugin,
  LocalPickedMedia,
  PickMediaOptions,
  UploadCareUploadOptions,
  UploadCareUploadResult,
  UploadCareDataUriOptions,
  UploadCareProgressEvent,
  UploadPickedOptions,
  UploadCareFile,
} from './definitions';

const CapUploadCare = registerPlugin<CapUploadCarePlugin>('CapUploadCare', {
  web: () => import('./web').then((m) => new m.CapUploadCareWeb()),
});

export * from './definitions';
export { CapUploadCare };

/**
 * - Opens the uploader
 * - Returns UploadCareUploadResult
 * - If user cancelled: { success: false, cancelled: true, files: [] }
 * - Throws on error
 */
export async function selectAndUploadImage(
  options?: UploadCareUploadOptions,
): Promise<UploadCareUploadResult> {
  const uploadId =
    options?.uploadId ?? (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`);

  const mergedOptions: UploadCareUploadOptions = {
    ...options,
    uploadId,
    mediaType: 'image',
    allowedMimeTypes: options?.allowedMimeTypes ?? ['image/*'],
  };

  const result = await CapUploadCare.openUploader(mergedOptions);

  if (result.cancelled) return { ...result, uploadId };

  if (!result.success || !result.files?.length) {
    throw new Error(result.errorMessage ?? 'Upload failed or no file returned');
  }

  return { ...result, uploadId: result.uploadId ?? uploadId };
}

export async function selectAndUploadSingleImageFile(
  options?: UploadCareUploadOptions,
): Promise<UploadCareFile | null> {
  const res = await selectAndUploadImage(options);
  if (res.cancelled) return null;
  if (!res.success || res.files.length === 0) throw new Error(res.errorMessage ?? 'Upload failed');
  return res.files[0];
}

export async function selectAndUploadVideo(
  options?: UploadCareUploadOptions,
): Promise<UploadCareUploadResult> {
  const uploadId =
    options?.uploadId ?? (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`);

  const mergedOptions: UploadCareUploadOptions = {
    ...options,
    uploadId,
    mediaType: 'video',
    allowedMimeTypes: options?.allowedMimeTypes ?? ['video/*'],
  };

  const result = await CapUploadCare.openUploader(mergedOptions);

  if (result.cancelled) return { ...result, uploadId };

  if (!result.success || !result.files?.length) {
    throw new Error(result.errorMessage ?? 'Upload failed or no file returned');
  }

  return { ...result, uploadId: result.uploadId ?? uploadId };
}

export async function uploadDataUriImage(
  options: UploadCareDataUriOptions,
): Promise<UploadCareUploadResult> {
  const uploadId =
    options.uploadId ?? (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`);

  const result: UploadCareUploadResult = await CapUploadCare.uploadDataUri({
    ...options,
    uploadId,
  });

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

  // guarantee uploadId is present for callers
  return {
    ...result,
    uploadId: result.uploadId ?? uploadId,
  };
}

export async function uploadDataUriVideo(
  options: UploadCareDataUriOptions,
): Promise<UploadCareUploadResult> {
  const uploadId =
    options.uploadId ?? (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`);

  const result: UploadCareUploadResult = await CapUploadCare.uploadDataUri({
    ...options,
    uploadId,
  });

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

  return {
    ...result,
    uploadId: result.uploadId ?? uploadId,
  };
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
  const uploadId =
    options.uploadId ?? (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`);

  return CapUploadCare.uploadPicked({
    ...options,
    uploadId,
  });
}

/**
 * Listen for native upload progress events (0..100).
 */
export async function addUploadProgressListener(
  listener: (e: UploadCareProgressEvent) => void,
) {
  return CapUploadCare.addListener('uploadProgress', listener);
}