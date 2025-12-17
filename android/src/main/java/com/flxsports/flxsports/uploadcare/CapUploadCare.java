package com.flxsports.flxsports.uploadcare;

import android.content.Context;
import android.net.Uri;

import com.getcapacitor.Logger;
import com.uploadcare.android.library.api.UploadcareClient;
import com.uploadcare.android.library.callbacks.UploadFileCallback;
import com.uploadcare.android.library.exceptions.UploadcareApiException;
import com.uploadcare.android.library.upload.FileUploader;
import com.uploadcare.android.library.api.UploadcareFile;

import java.util.HashMap;
import java.util.Map;

public class CapUploadCare {

    private static final String TAG = "CapUploadCare";

    private UploadcareClient client;
    private boolean debug = false;

    // Configure Uploadcare client from JS
    public void configure(String publicKey, String secretKey, boolean debug) {
        this.debug = debug;

        if (secretKey != null && !secretKey.isEmpty()) {
            client = new UploadcareClient(publicKey, secretKey);
        } else {
            // For Upload API–only usage, secret key can be omitted
            client = new UploadcareClient(publicKey, null);
        }

        if (debug) {
            Logger.info(TAG, "Configured Uploadcare with publicKey=" + publicKey);
        }
    }

    public interface UploadCallback {
        void onSuccess(Map<String, Object> fileMap);
        void onError(Throwable error);
    }

    // Upload from raw bytes (for dataUri path)
    public void uploadDataBytes(byte[] bytes, String fileName, UploadCallback callback) {
        if (client == null) {
            callback.onError(new IllegalStateException("Uploadcare client is not configured"));
            return;
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
                if (debug && contentLength > 0) {
                    int percent = (int) (progress * 100);
                    Logger.debug(TAG, "Upload progress: " + percent + "%");
                }
            }

            @Override
            public void onSuccess(UploadcareFile file) {
                String uuid = file.getUuid();
                String cdnUrl;

                if (file.getOriginalFileUrl() != null) {
                    cdnUrl = file.getOriginalFileUrl().toString();
                } else {
                    cdnUrl = "https://ucarecdn.com/" + uuid + "/";
                }

                Map<String, Object> map = new HashMap<>();
                map.put("uuid", uuid);
                map.put("cdnUrl", cdnUrl);

                if (file.getOriginalFilename() != null) {
                    map.put("filename", file.getOriginalFilename());
                }
                if (file.getSize() != null) {
                    map.put("sizeBytes", file.getSize());
                }
                if (file.getMimeType() != null) {
                    map.put("mimeType", file.getMimeType());
                }

                // width/height are optional in your TS type; we can add later
                callback.onSuccess(map);
            }
        });
    }

    public void uploadSingle(Context context, Uri uri, UploadCallback callback) {
        if (client == null) {
            callback.onError(new IllegalStateException("Uploadcare client is not configured"));
            return;
        }

        // Use Uploadcare Android SDK FileUploader, as in their Kotlin docs
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
                if (debug && contentLength > 0) {
                    int percent = (int) (progress * 100);
                    Logger.debug(TAG, "Upload progress: " + percent + "%");
                }
            }

            @Override
            public void onSuccess(UploadcareFile file) {
                // Mirror TS UploadCareFile as closely as possible
                String uuid = file.getUuid();
                String cdnUrl;

                if (file.getOriginalFileUrl() != null) {
                    cdnUrl = file.getOriginalFileUrl().toString();
                } else {
                    cdnUrl = "https://ucarecdn.com/" + uuid + "/";
                }

                Map<String, Object> map = new HashMap<>();
                map.put("uuid", uuid);
                map.put("cdnUrl", cdnUrl);

                if (file.getOriginalFilename() != null) {
                    map.put("filename", file.getOriginalFilename());
                }
                if (file.getSize() != null) {
                    map.put("sizeBytes", file.getSize());
                }
                if (file.getMimeType() != null) {
                    map.put("mimeType", file.getMimeType());
                }

                // width/height are optional in your TS type; we can add later
                callback.onSuccess(map);
            }
        });
    }
}