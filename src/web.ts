import { WebPlugin } from '@capacitor/core';
import { UploadClient } from '@uploadcare/upload-client';

import type {
  CapUploadCarePlugin,
  UploadCareConfig,
  UploadCareUploadOptions,
  UploadCareUploadResult,
  UploadCareDataUriOptions,
  UploadCareFile,
} from './definitions';

const IMAGE_MAX_BYTES = 8 * 1024 * 1024; // 8mb
const VIDEO_MAX_BYTES = 512 * 1024 * 1024; // 512mb

const IMAGE_MIME_ALLOW = new Set([
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/heic',
  'image/heif',
  'image/avif',
  'image/bmp',
]);

const VIDEO_MIME_ALLOW = new Set([
  'video/mp4',
  'video/quicktime', // mov
  'video/mpeg',
  'video/3gpp',
  'video/x-msvideo', // avi
]);

export class CapUploadCareWeb extends WebPlugin implements CapUploadCarePlugin {
  private client: UploadClient | null = null;

  async configure(options: UploadCareConfig): Promise<void> {
    if (!options.publicKey || options.publicKey.trim() === '') {
      throw new Error('publicKey is required for Uploadcare web integration');
    }

    this.client = new UploadClient({
      publicKey: options.publicKey,
      // baseCDN: options.cdnBase,
      // baseURL: options.apiBase,
    });

    if (options.debug) {
      console.info('[CapUploadCareWeb] Configured UploadClient for web');
    }
  }

  private ensureClient(): UploadClient {
    if (!this.client) {
      throw new Error(
        '[CapUploadCareWeb] UploadClient is not configured. Call CapUploadCare.configure(...) first on web.',
      );
    }
    return this.client;
  }

  private mapUploadcareFile(info: any): UploadCareFile {
    const file: UploadCareFile = {
      uuid: info.uuid,
      cdnUrl: info.cdnUrl,
    };

    if (info.originalFilename) {
      file.filename = info.originalFilename;
    }
    if (typeof info.size === 'number') {
      file.sizeBytes = info.size;
    }
    if (info.mimeType) {
      file.mimeType = info.mimeType;
    }

    return file;
  }

  private validatePickedFile(file: File, mediaType: 'image' | 'video'): void {
    const mime = (file.type || '').toLowerCase();

    if (mediaType === 'image') {
      if (!IMAGE_MIME_ALLOW.has(mime)) {
        throw new Error(`Unsupported image format: ${mime || 'unknown'}`);
      }
      if (file.size > IMAGE_MAX_BYTES) {
        throw new Error('Image exceeds max size of 8mb');
      }
      // Resolution checks on web require decoding; if you want it, we can add it,
      // but it’s async and slightly heavier. Native platforms will enforce strictly.
      return;
    }

    if (mediaType === 'video') {
      if (!VIDEO_MIME_ALLOW.has(mime)) {
        throw new Error(`Unsupported video format: ${mime || 'unknown'}`);
      }
      if (file.size > VIDEO_MAX_BYTES) {
        throw new Error('Video exceeds max size of 512mb');
      }
      // Duration/resolution checks on web require loading metadata; can be added if you want.
    }
  }

  async openUploader(options?: UploadCareUploadOptions): Promise<UploadCareUploadResult> {
    const client = this.ensureClient();
    const uploadId = crypto.randomUUID();

    const requested = options?.mediaType ?? 'any';
    const accept =
      options?.allowedMimeTypes?.length
        ? options.allowedMimeTypes.join(',')
        : requested === 'image'
          ? 'image/*'
          : requested === 'video'
            ? 'video/*'
            : 'image/*,video/*';

    return new Promise<UploadCareUploadResult>((resolve, reject) => {
      const input = document.createElement('input');
      input.type = 'file';
      input.accept = accept;

      if (options?.multiple) {
        input.multiple = true;
      }

      input.style.position = 'fixed';
      input.style.left = '-9999px';
      input.style.top = '-9999px';

      document.body.appendChild(input);

      const cleanup = () => {
        document.body.removeChild(input);
      };

      input.onchange = async () => {
        try {
          const files = input.files;
          if (!files || files.length === 0) {
            cleanup();
            resolve({
              success: false,
              cancelled: true,
              errorMessage: 'No file selected',
              uploadId,
              files: [],
            });
            return;
          }

          const maxFiles =
            options?.maxFiles && options.maxFiles > 0
              ? Math.min(files.length, options.maxFiles)
              : files.length;

          const selected: File[] = [];
          for (let i = 0; i < maxFiles; i++) {
            const f = files.item(i);
            if (f) selected.push(f);
          }

          const uploaded: UploadCareFile[] = [];
          for (const file of selected) {
            const mime = (file.type || '').toLowerCase();
            const inferredMediaType: 'image' | 'video' =
              mime.startsWith('video/') ? 'video' : 'image';

            const effectiveMediaType: 'image' | 'video' =
              requested === 'any' ? inferredMediaType : requested;

            this.validatePickedFile(file, effectiveMediaType);

            const info = await client.uploadFile(file, {
              onProgress: (progress) => {
                if (!progress.isComputable || typeof progress.value !== 'number') return;
                const pct = Math.max(0, Math.min(100, Math.round(progress.value * 100)));

                this.notifyListeners('uploadProgress', {
                  uploadId,
                  progress: pct,
                  mediaType: effectiveMediaType,
                });
              },
            });

            uploaded.push(this.mapUploadcareFile(info));
          }

          cleanup();

          resolve({
            success: true,
            cancelled: false,
            uploadId,
            files: uploaded,
          });
        } catch (err: any) {
          cleanup();
          const message = err?.message ?? String(err);
          reject(new Error(message));
        }
      };

      input.click();
    });
  }

  async uploadDataUri(options: UploadCareDataUriOptions): Promise<UploadCareUploadResult> {
    const client = this.ensureClient();
    const uploadId = crypto.randomUUID();

    const { dataUri, fileName } = options;

    if (!dataUri || dataUri.trim() === '') {
      throw new Error('dataUri is required');
    }

    if (!fileName || fileName.trim() === '') {
      throw new Error('fileName is required');
    }

    const mimeMatch = /^data:([^;]+);base64,/.exec(dataUri);
    const mime = (mimeMatch?.[1] ?? '').toLowerCase();

    const mediaType: 'image' | 'video' = mime.startsWith('video/') ? 'video' : 'image';

    // Convert data URI to Blob and upload.
    const response = await fetch(dataUri);
    const blob = await response.blob();

    // Size rules
    if (mediaType === 'image' && blob.size > IMAGE_MAX_BYTES) {
      throw new Error('Image exceeds max size of 8mb');
    }
    if (mediaType === 'video' && blob.size > VIDEO_MAX_BYTES) {
      throw new Error('Video exceeds max size of 512mb');
    }

    const info = await client.uploadFile(blob, {
      fileName,
      contentType: mime || undefined,
      onProgress: (progress) => {
        if (!progress.isComputable || typeof progress.value !== 'number') return;
        const pct = Math.max(0, Math.min(100, Math.round(progress.value * 100)));
        this.notifyListeners('uploadProgress', { uploadId, progress: pct, mediaType });
      },
    });

    const file = this.mapUploadcareFile(info);

    return {
      success: true,
      cancelled: false,
      uploadId,
      files: [file],
    };
  }
}