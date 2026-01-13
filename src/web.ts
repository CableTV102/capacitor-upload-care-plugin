import { WebPlugin } from '@capacitor/core';
import { UploadClient } from '@uploadcare/upload-client';

import type {
  CapUploadCarePlugin,
  LocalPickedMedia,
  PickMediaOptions,
  UploadCareConfig,
  UploadCareDataUriOptions,
  UploadCareFile,
  UploadCareUploadOptions,
  UploadCareUploadResult,
  UploadPickedOptions,
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

type MediaType = 'image' | 'video';

export class CapUploadCareWeb extends WebPlugin implements CapUploadCarePlugin {
  private client: UploadClient | null = null;

  // For web deferred uploads: store the picked File in memory by localId.
  private pickedFiles = new Map<
    string,
    { file: File; mediaType: MediaType; objectUrl: string; mimeType: string }
  >();

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

  private validatePickedFile(file: File, mediaType: MediaType): void {
    const mime = (file.type || '').toLowerCase();

    if (mediaType === 'image') {
      if (!IMAGE_MIME_ALLOW.has(mime)) {
        throw new Error(`Unsupported image format: ${mime || 'unknown'}`);
      }
      if (file.size > IMAGE_MAX_BYTES) {
        throw new Error('Image exceeds max size of 8mb');
      }
      return;
    }

    if (mediaType === 'video') {
      if (!VIDEO_MIME_ALLOW.has(mime)) {
        throw new Error(`Unsupported video format: ${mime || 'unknown'}`);
      }
      if (file.size > VIDEO_MAX_BYTES) {
        throw new Error('Video exceeds max size of 512mb');
      }
    }
  }

  private createHiddenFileInput(accept: string, multiple: boolean): HTMLInputElement {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = multiple;

    input.style.position = 'fixed';
    input.style.left = '-9999px';
    input.style.top = '-9999px';

    document.body.appendChild(input);
    return input;
  }

  private cleanupHiddenFileInput(input: HTMLInputElement) {
    try {
      if (input.parentNode) input.parentNode.removeChild(input);
    } catch {}
  }

  private inferMediaTypeFromMime(mime: string): MediaType {
    return mime.startsWith('video/') ? 'video' : 'image';
  }

  /**
   * Pick WITHOUT upload (web).
   * Returns a LocalPickedMedia with a blob: object URL for preview.
   * Stores the File in-memory keyed by localId so uploadPicked can upload later.
   */
  async pickMedia(options?: PickMediaOptions): Promise<LocalPickedMedia> {
    const requested = options?.mediaType ?? 'any';

    const accept =
      requested === 'image'
        ? 'image/*'
        : requested === 'video'
          ? 'video/*'
          : 'image/*,video/*';

    return new Promise<LocalPickedMedia>((resolve, reject) => {
      const input = this.createHiddenFileInput(accept, false);

      input.onchange = async () => {
        try {
          const files = input.files;
          if (!files || files.length === 0) {
            this.cleanupHiddenFileInput(input);
            reject(new Error('No file selected'));
            return;
          }

          const file = files.item(0);
          if (!file) {
            this.cleanupHiddenFileInput(input);
            reject(new Error('No file selected'));
            return;
          }

          const mime = (file.type || '').toLowerCase();
          const inferred: MediaType = this.inferMediaTypeFromMime(mime);

          const effective: MediaType =
            requested === 'any' ? inferred : (requested as MediaType);

          this.validatePickedFile(file, effective);

          const localId = crypto.randomUUID();
          const objectUrl = URL.createObjectURL(file);

          // Keep for later upload
          this.pickedFiles.set(localId, {
            file,
            mediaType: effective,
            objectUrl,
            mimeType: mime,
          });

          this.cleanupHiddenFileInput(input);

          resolve({
            localId,
            uri: objectUrl, // web preview URL
            mediaType: effective,
            mimeType: mime || undefined,
            displayName: file.name || undefined,
            sizeBytes: file.size,
          });
        } catch (err: any) {
          this.cleanupHiddenFileInput(input);
          reject(new Error(err?.message ?? String(err)));
        }
      };

      input.click();
    });
  }

  /**
   * Upload later (web).
   * Uses the previously picked File stored in-memory.
   */
  async uploadPicked(options: UploadPickedOptions): Promise<UploadCareUploadResult> {
    const client = this.ensureClient();

    const { localId, fileName } = options;

    if (!localId || localId.trim() === '') {
      throw new Error('localId is required');
    }
    if (!fileName || fileName.trim() === '') {
      throw new Error('fileName is required');
    }

    const picked = this.pickedFiles.get(localId);
    if (!picked) {
      throw new Error('Unknown localId (maybe page refreshed). Pick media again.');
    }

    const uploadId = crypto.randomUUID();
    const mediaType = picked.mediaType;

    const info = await client.uploadFile(picked.file, {
      fileName,
      contentType: picked.mimeType || undefined,
      onProgress: (progress) => {
        if (!progress.isComputable || typeof progress.value !== 'number') return;
        const pct = Math.max(0, Math.min(100, Math.round(progress.value * 100)));

        this.notifyListeners('uploadProgress', {
          uploadId,
          progress: pct,
          mediaType,
        });
      },
    });

    // Optional cleanup: free memory + object URL once uploaded
    try {
      URL.revokeObjectURL(picked.objectUrl);
    } catch {}
    this.pickedFiles.delete(localId);

    const file = this.mapUploadcareFile(info);

    return {
      success: true,
      cancelled: false,
      uploadId,
      files: [file],
    };
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
      const input = this.createHiddenFileInput(accept, Boolean(options?.multiple));

      input.onchange = async () => {
        try {
          const files = input.files;
          if (!files || files.length === 0) {
            this.cleanupHiddenFileInput(input);
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
            const inferredMediaType: MediaType = this.inferMediaTypeFromMime(mime);

            const effectiveMediaType: MediaType =
              requested === 'any' ? inferredMediaType : (requested as MediaType);

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

          this.cleanupHiddenFileInput(input);

          resolve({
            success: true,
            cancelled: false,
            uploadId,
            files: uploaded,
          });
        } catch (err: any) {
          this.cleanupHiddenFileInput(input);
          reject(new Error(err?.message ?? String(err)));
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

    const mediaType: MediaType = mime.startsWith('video/') ? 'video' : 'image';

    const response = await fetch(dataUri);
    const blob = await response.blob();

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