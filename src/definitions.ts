export interface CapUploadCarePlugin {
  /**
   * Configure the Uploadcare SDK.
   * Call this early in your app (e.g., app initialization).
   */
  configure(options: UploadCareConfig): Promise<void>;

  /**
   * Open the native Uploadcare picker/uploader.
   * You will call this from Ionic components (IonButton, IonItem, etc.).
   */
  openUploader(options?: UploadCareUploadOptions): Promise<UploadCareUploadResult>;

  /**
   * Upload a base64 data URI directly (no native picker).
   * Expects a full data URI like: data:image/jpeg;base64,/9j/4AAQSk...
   */
  uploadDataUri(options: UploadCareDataUriOptions): Promise<UploadCareUploadResult>;
}

/**
 * Configuration options for Uploadcare SDK.
 * These map to the public/private (secret) keys you will pass from the host app.
 */
export interface UploadCareConfig {
  /**
   * Your Uploadcare public key (required for client-side uploading).
   * Example: 'demopublickey'
   */
  publicKey: string;

  /**
   * Optional: Secret key if your mobile SDK usage requires it.
   * Usually used for signed uploads / secure operations.
   * Handle with care and never expose this in web builds.
   */
  secretKey?: string;

  /**
   * Optional: custom CDN base or API base if your Uploadcare project uses them.
   */
  cdnBase?: string;
  apiBase?: string;

  /**
   * Whether to enable console logging on native side for debugging.
   */
  debug?: boolean;
}

/**
 * Options for an individual upload interaction.
 */
export interface UploadCareUploadOptions {
  /**
   * Allow selecting multiple files.
   */
  multiple?: boolean;

  /**
   * Restrict to images, videos, etc. If omitted, use Uploadcare defaults.
   * We’ll map this to native Uploadcare type restrictions.
   */
  allowedMimeTypes?: string[];

  /**
   * Max number of files when `multiple` is true.
   */
  maxFiles?: number;

  /**
   * Whether to enable cropping UI (if supported by the SDK).
   */
  enableCrop?: boolean;

  /**
   * Preferred crop aspect ratio, like '3:2', '1:1', etc.
   * We’ll parse and map this on native side if Uploadcare supports it.
   */
  cropRatio?: string;

  /**
   * Max file size in bytes (we can enforce in native before uploading).
   */
  maxFileSizeBytes?: number;
}

/**
 * Options for uploading a pre-existing base64 data URI.
 * The `dataUri` must be a full data URI string:
 *   data:<mime>;base64,<payload>
 */
export interface UploadCareDataUriOptions {
  dataUri: string;
  fileName: string;
}

/**
 * Result from an Uploadcare upload session.
 */
export interface UploadCareUploadResult {
  /**
   * Whether the upload interaction completed successfully.
   */
  success: boolean;

  /**
   * If the user cancelled the picker.
   */
  cancelled?: boolean;

  /**
   * Error message, if any.
   */
  errorMessage?: string;

  /**
   * Array of uploaded file descriptors.
   * Even when `multiple` is false, we’ll return an array of length 1.
   */
  files: UploadCareFile[];
}

/**
 * Metadata about a single uploaded file as returned by Uploadcare.
 */
export interface UploadCareFile {
  /**
   * Uploadcare file UUID.
   */
  uuid: string;

  /**
   * Uploadcare CDN URL for this file (what you probably care about most).
   */
  cdnUrl: string;

  /**
   * Original filename, if available.
   */
  filename?: string;

  /**
   * File size in bytes.
   */
  sizeBytes?: number;

  /**
   * MIME type, e.g. 'image/jpeg'.
   */
  mimeType?: string;

  /**
   * Width & height if it’s an image (when available).
   */
  width?: number;
  height?: number;
}