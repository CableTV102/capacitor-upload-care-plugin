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
        '[CapUploadCareWeb] UploadClient is not configured or defined. Call CapUploadCare.configure(...) first on web.',
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

    // width/height are optional in your TS type; Uploadcare exposes them
    // via image info, but we can leave them undefined for now.

    return file;
  }

  async openUploader(options?: UploadCareUploadOptions): Promise<UploadCareUploadResult> {
    const client = this.ensureClient();

    return new Promise<UploadCareUploadResult>((resolve, reject) => {
      const input = document.createElement('input');
      input.type = 'file';

      if (options?.allowedMimeTypes && options.allowedMimeTypes.length > 0) {
        input.accept = options.allowedMimeTypes.join(',');
      }

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
            if (f) {
              selected.push(f);
            }
          }

          const uploaded: UploadCareFile[] = [];
          for (const file of selected) {
            const info = await client.uploadFile(file);
            uploaded.push(this.mapUploadcareFile(info));
          }

          cleanup();

          resolve({
            success: true,
            cancelled: false,
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

    const { dataUri, fileName } = options;

    if (!dataUri || dataUri.trim() === '') {
      throw new Error('dataUri is required');
    }

    if (!fileName || fileName.trim() === '') {
      throw new Error('fileName is required');
    }

    // Convert data URI to Blob and upload.
    // Pattern: fetch(dataUrl).then(r => r.blob()) then client.uploadFile(blob) 
    const response = await fetch(dataUri);
    const blob = await response.blob();

    const info = await client.uploadFile(blob, {
      fileName,
    });

    const file = this.mapUploadcareFile(info);

    return {
      success: true,
      cancelled: false,
      files: [file],
    };
  }
}