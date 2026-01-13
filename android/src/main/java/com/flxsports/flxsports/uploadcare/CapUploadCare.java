package com.flxsports.flxsports.uploadcare;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.getcapacitor.Logger;
import com.uploadcare.android.library.api.UploadcareClient;
import com.uploadcare.android.library.api.UploadcareFile;
import com.uploadcare.android.library.callbacks.UploadFileCallback;
import com.uploadcare.android.library.exceptions.UploadcareApiException;
import com.uploadcare.android.library.upload.FileUploader;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.io.IOException

public class CapUploadCare {

    private static final String TAG = "CapUploadCare";

    private static final long IMAGE_MAX_BYTES = 8L * 1024L * 1024L;          // 8mb
    private static final long VIDEO_MAX_BYTES = 512L * 1024L * 1024L;        // 512mb

    private static final int IMAGE_MIN_DIM_PX = 566;
    private static final int VIDEO_MIN_DIM_PX = 608;
    private static final int VIDEO_MAX_DIM_PX = 1080;

    private static final long VIDEO_MIN_MS = 3_000L;
    private static final long VIDEO_MAX_MS = 60_000L;

    private UploadcareClient client;
    private boolean debug = false;

    public interface UploadCallback {
        void onSuccess(Map<String, Object> fileMap);
        void onError(Exception error);
    }

    public interface ProgressCallback {
        void onProgress(long bytesWritten, long contentLength, int progressPercent);
    }

    public void configure(String publicKey, String secretKey, boolean debug) {
        this.debug = debug;

        if (secretKey != null && !secretKey.isEmpty()) {
            client = new UploadcareClient(publicKey, secretKey);
        } else {
            client = new UploadcareClient(publicKey);
        }

        if (debug) {
            Logger.info(TAG, "Configured Uploadcare with publicKey=" + publicKey);
        }
    }

    public static class ValidationResult {
        public final boolean ok;
        public final String errorMessage;
        public final String mimeType;
        public final String displayName;
        public final long sizeBytes;
        public final Integer width;
        public final Integer height;
        public final Long durationMs;

        public ValidationResult(
                boolean ok,
                String errorMessage,
                String mimeType,
                String displayName,
                long sizeBytes,
                Integer width,
                Integer height,
                Long durationMs
        ) {
            this.ok = ok;
            this.errorMessage = errorMessage;
            this.mimeType = mimeType;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
        }
    }

    private static boolean isAllowedImageMime(String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase(Locale.US);
        return m.equals("image/jpeg")
                || m.equals("image/jpg")
                || m.equals("image/png")
                || m.equals("image/heic")
                || m.equals("image/heif")
                || m.equals("image/avif")
                || m.equals("image/bmp");
    }

    private static boolean isAllowedVideoMime(String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase(Locale.US);
        return m.equals("video/mp4")
                || m.equals("video/quicktime")   // mov
                || m.equals("video/mpeg")
                || m.equals("video/3gpp")
                || m.equals("video/x-msvideo");  // avi
    }

    private static String getDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private static long getSizeBytes(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) {
                    long size = cursor.getLong(idx);
                    if (size >= 0) return size;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1L;
    }

    public ValidationResult validateImage(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (!isAllowedImageMime(mime)) {
            return new ValidationResult(false,
                    "Unsupported image format: " + (mime == null ? "unknown" : mime),
                    mime, null, -1, null, null, null);
        }

        long size = getSizeBytes(context, uri);
        if (size > IMAGE_MAX_BYTES) {
            return new ValidationResult(false, "Image exceeds max size of 8mb", mime, null, size, null, null, null);
        }

        Integer w = null;
        Integer h = null;

        InputStream is = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;

            is = context.getContentResolver().openInputStream(uri);
            if (is != null) {
                BitmapFactory.decodeStream(is, null, opts);
                w = opts.outWidth;
                h = opts.outHeight;
            }
        } catch (Exception e) {
            return new ValidationResult(false, "Could not read image metadata", mime, null, size, null, null, null);
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }

        if (w == null || h == null || w <= 0 || h <= 0) {
            return new ValidationResult(false, "Could not determine image dimensions", mime, null, size, null, null, null);
        }

        int minDim = Math.min(w, h);
        if (minDim < IMAGE_MIN_DIM_PX) {
            return new ValidationResult(false, "Image resolution too small (min smallest dimension: 566px)", mime, null, size, w, h, null);
        }

        String displayName = getDisplayName(context, uri);
        return new ValidationResult(true, null, mime, displayName, size, w, h, null);
    }

    public ValidationResult validateVideo(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (!isAllowedVideoMime(mime)) {
            return new ValidationResult(false,
                    "Unsupported video format: " + (mime == null ? "unknown" : mime),
                    mime, null, -1, null, null, null);
        }

        long size = getSizeBytes(context, uri);
        if (size > VIDEO_MAX_BYTES) {
            return new ValidationResult(false, "Video exceeds max size of 512mb", mime, null, size, null, null, null);
        }

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        Integer w = null;
        Integer h = null;
        Long durationMs = null;

        try {
            mmr.setDataSource(context, uri);

            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durStr != null) {
                durationMs = Long.parseLong(durStr);
            }

            String wStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (wStr != null) w = Integer.parseInt(wStr);
            if (hStr != null) h = Integer.parseInt(hStr);

        } catch (Exception e) {
            return new ValidationResult(false, "Could not read video metadata", mime, null, size, null, null, null);
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }

        if (durationMs == null) {
            return new ValidationResult(false, "Could not determine video duration", mime, null, size, w, h, null);
        }

        if (durationMs < VIDEO_MIN_MS) {
            return new ValidationResult(false, "Video too short (min 3 seconds)", mime, null, size, w, h, durationMs);
        }

        if (durationMs > VIDEO_MAX_MS) {
            return new ValidationResult(false, "Video too long (max 60 seconds)", mime, null, size, w, h, durationMs);
        }

        if (w == null || h == null || w <= 0 || h <= 0) {
            return new ValidationResult(false, "Could not determine video resolution", mime, null, size, null, null, durationMs);
        }

        int minDim = Math.min(w, h);
        int maxDim = Math.max(w, h);

        if (minDim < VIDEO_MIN_DIM_PX) {
            return new ValidationResult(false, "Video resolution too small (min smallest dimension: 608px)", mime, null, size, w, h, durationMs);
        }

        if (maxDim > VIDEO_MAX_DIM_PX) {
            return new ValidationResult(false, "Video resolution too large (max dimension: 1080px)", mime, null, size, w, h, durationMs);
        }

        String displayName = getDisplayName(context, uri);
        return new ValidationResult(true, null, mime, displayName, size, w, h, durationMs);
    }

    public void uploadDataBytes(
            byte[] bytes,
            String fileName,
            String mimeType,
            ProgressCallback progressCallback,
            UploadCallback callback
    ) {
        if (client == null) {
            callback.onError(new IllegalStateException("Uploadcare client is not configured"));
            return;
        }

        if (mimeType != null) {
            String m = mimeType.toLowerCase(Locale.US);
            if (m.startsWith("image/")) {
                if (!isAllowedImageMime(m)) {
                    callback.onError(new IllegalArgumentException("Unsupported image format: " + m));
                    return;
                }
                if (bytes.length > IMAGE_MAX_BYTES) {
                    callback.onError(new IllegalArgumentException("Image exceeds max size of 8mb"));
                    return;
                }
            } else if (m.startsWith("video/")) {
                if (!isAllowedVideoMime(m)) {
                    callback.onError(new IllegalArgumentException("Unsupported video format: " + m));
                    return;
                }
                if (bytes.length > VIDEO_MAX_BYTES) {
                    callback.onError(new IllegalArgumentException("Video exceeds max size of 512mb"));
                    return;
                }
            }
        }

        FileUploader uploader = new FileUploader(client, bytes, fileName).store(true);

        uploader.uploadAsync(new UploadFileCallback() {
            @Override
            public void onFailure(UploadcareApiException e) {
                if (debug) {
                    Logger.error(TAG, "Upload failed: " + e.getMessage(), e);
                }
                callback.onError(e);
            }

            @Override
            public void onProgressUpdate(long bytesWritten, long contentLength, double progress) {
                if (contentLength > 0) {
                    int percent = (int) Math.round(progress * 100.0);
                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesWritten, contentLength, percent);
                    }
                    if (debug) {
                        Logger.debug(TAG, "Upload progress: " + percent + "%");
                    }
                }
            }

            @Override
            public void onSuccess(UploadcareFile file) {
                String uuid = file.getUuid();
                String cdnUrl = (file.getOriginalFileUrl() != null)
                        ? file.getOriginalFileUrl().toString()
                        : "https://ucarecdn.com/" + uuid + "/";

                Map<String, Object> map = new HashMap<>();
                map.put("uuid", uuid);
                map.put("cdnUrl", cdnUrl);

                if (file.getOriginalFilename() != null) map.put("filename", file.getOriginalFilename());

                int size = file.getSize();
                if (size > 0) map.put("sizeBytes", size);

                if (file.getMimeType() != null) map.put("mimeType", file.getMimeType());

                callback.onSuccess(map);
            }
        });
    }

    public void uploadUri(
            Context context,
            Uri uri,
            String mediaType,
            String fileName,
            ProgressCallback progressCallback,
            UploadCallback callback
    ) {
        if (client == null) {
            callback.onError(new IllegalStateException("Uploadcare client is not configured"));
            return;
        }

        String mt = (mediaType == null ? "any" : mediaType.toLowerCase(Locale.US));
        ValidationResult vr;

        if (mt.equals("image")) {
            vr = validateImage(context, uri);
        } else if (mt.equals("video")) {
            vr = validateVideo(context, uri);
        } else {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) {
                vr = validateVideo(context, uri);
            } else {
                vr = validateImage(context, uri);
            }
        }

        if (!vr.ok) {
            callback.onError(new IllegalArgumentException(vr.errorMessage));
            return;
        }

        // Use InputStream constructor (available in your SDK), and pass fileName as the "name"
        final InputStream stream;
        try {
            stream = context.getContentResolver().openInputStream(uri);
            if (stream == null) {
                callback.onError(new IOException("Could not open input stream for uri"));
                return;
            }
        } catch (Exception e) {
            callback.onError(new IOException("Failed to open input stream: " + e.getMessage(), e));
            return;
        }

        FileUploader uploader = new FileUploader(client, stream, fileName).store(true);

        uploader.uploadAsync(new UploadFileCallback() {
            @Override
            public void onFailure(UploadcareApiException e) {
                try { stream.close(); } catch (Exception ignored) {}
                if (debug) Logger.error(TAG, "Upload failed: " + e.getMessage(), e);
                callback.onError(e);
            }

            @Override
            public void onProgressUpdate(long bytesWritten, long contentLength, double progress) {
                if (contentLength > 0) {
                    int percent = (int) Math.round(progress * 100.0);
                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesWritten, contentLength, percent);
                    }
                }
            }

            @Override
            public void onSuccess(UploadcareFile file) {
                try { stream.close(); } catch (Exception ignored) {}

                String uuid = file.getUuid();
                String cdnUrl = (file.getOriginalFileUrl() != null)
                        ? file.getOriginalFileUrl().toString()
                        : "https://ucarecdn.com/" + uuid + "/";

                Map<String, Object> map = new HashMap<>();
                map.put("uuid", uuid);
                map.put("cdnUrl", cdnUrl);

                if (file.getOriginalFilename() != null) map.put("filename", file.getOriginalFilename());

                int size = file.getSize();
                if (size > 0) map.put("sizeBytes", size);

                if (file.getMimeType() != null) map.put("mimeType", file.getMimeType());

                if (vr.width != null) map.put("width", vr.width);
                if (vr.height != null) map.put("height", vr.height);

                callback.onSuccess(map);
            }
        });
    }

    public void uploadSingle(
            Context context,
            Uri uri,
            String mediaType,
            ProgressCallback progressCallback,
            UploadCallback callback
    ) {
        if (client == null) {
            callback.onError(new IllegalStateException("Uploadcare client is not configured"));
            return;
        }

        String mt = (mediaType == null ? "any" : mediaType.toLowerCase(Locale.US));
        ValidationResult vr;

        if (mt.equals("image")) {
            vr = validateImage(context, uri);
        } else if (mt.equals("video")) {
            vr = validateVideo(context, uri);
        } else {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) {
                vr = validateVideo(context, uri);
            } else {
                vr = validateImage(context, uri);
            }
        }

        if (!vr.ok) {
            callback.onError(new IllegalArgumentException(vr.errorMessage));
            return;
        }

        FileUploader uploader = new FileUploader(client, uri, context).store(true);

        uploader.uploadAsync(new UploadFileCallback() {
            @Override
            public void onFailure(UploadcareApiException e) {
                if (debug) {
                    Logger.error(TAG, "Upload failed: " + e.getMessage(), e);
                }
                callback.onError(e);
            }

            @Override
            public void onProgressUpdate(long bytesWritten, long contentLength, double progress) {
                if (contentLength > 0) {
                    int percent = (int) Math.round(progress * 100.0);
                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesWritten, contentLength, percent);
                    }
                    if (debug) {
                        Logger.debug(TAG, "Upload progress: " + percent + "%");
                    }
                }
            }

            @Override
            public void onSuccess(UploadcareFile file) {
                String uuid = file.getUuid();
                String cdnUrl = (file.getOriginalFileUrl() != null)
                        ? file.getOriginalFileUrl().toString()
                        : "https://ucarecdn.com/" + uuid + "/";

                Map<String, Object> map = new HashMap<>();
                map.put("uuid", uuid);
                map.put("cdnUrl", cdnUrl);

                if (file.getOriginalFilename() != null) map.put("filename", file.getOriginalFilename());

                int size = file.getSize();
                if (size > 0) map.put("sizeBytes", size);

                if (file.getMimeType() != null) map.put("mimeType", file.getMimeType());

                if (vr.width != null) map.put("width", vr.width);
                if (vr.height != null) map.put("height", vr.height);

                callback.onSuccess(map);
            }
        });
    }
}