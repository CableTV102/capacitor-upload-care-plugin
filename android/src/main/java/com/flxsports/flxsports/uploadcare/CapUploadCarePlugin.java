package com.flxsports.flxsports.uploadcare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(name = "CapUploadCare")
public class CapUploadCarePlugin extends Plugin {
    private final Map<String, Uri> pickedById = new ConcurrentHashMap<>();
    private final Map<String, String> pickedTypeById = new ConcurrentHashMap<>();
    private final CapUploadCare implementation = new CapUploadCare();
    private PluginCall pendingCall;

    @PluginMethod
    public void configure(PluginCall call) {
        String publicKey = call.getString("publicKey");
        if (publicKey == null || publicKey.isEmpty()) {
            call.reject("publicKey is required");
            return;
        }

        String secretKey = call.getString("secretKey");
        Boolean debugFlag = call.getBoolean("debug");
        boolean debug = debugFlag != null && debugFlag;

        implementation.configure(publicKey, secretKey, debug);
        call.resolve();
    }

    @PluginMethod
    public void openUploader(PluginCall call) {
        if (pendingCall != null) {
            call.reject("An upload is already in progress");
            return;
        }

        String mediaType = null;

        JSObject options = call.getObject("options");
        if (options != null) {
            mediaType = options.getString("mediaType");
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            mediaType = call.getString("mediaType");
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            mediaType = "any";
        }

        mediaType = mediaType.toLowerCase(Locale.US);

        Intent intent = new Intent(Intent.ACTION_PICK);

        // Helps some picker UIs avoid offering non-openable stuff
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (mediaType.equals("image")) {
            intent.setType("image/*");
        } else if (mediaType.equals("video")) {
            intent.setType("video/*");
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }

        pendingCall = call;
        startActivityForResult(call, intent, "handlePickerResult");
    }

    @PluginMethod
    public void uploadDataUri(PluginCall call) {
        String dataUri = call.getString("dataUri");
        String fileName = call.getString("fileName");

        if (dataUri == null || dataUri.isEmpty()) {
            call.reject("dataUri is required");
            return;
        }

        if (fileName == null || fileName.isEmpty()) {
            call.reject("fileName is required");
            return;
        }

        String mimeType = null;
        int headerEnd = dataUri.indexOf(";base64,");
        if (dataUri.startsWith("data:") && headerEnd > 5) {
            mimeType = dataUri.substring(5, headerEnd);
        }

        int commaIndex = dataUri.indexOf(',');
        if (commaIndex == -1) {
            call.reject("Invalid dataUri format");
            return;
        }

        byte[] bytes;
        try {
            String base64 = dataUri.substring(commaIndex + 1);
            bytes = Base64.decode(base64, Base64.DEFAULT);
        } catch (Exception e) {
            call.reject("Invalid base64 data", e);
            return;
        }

        // Allow JS to provide uploadId so UI can track progress immediately
        String providedUploadId = call.getString("uploadId");
        final String uploadId = (providedUploadId != null && !providedUploadId.isEmpty())
                ? providedUploadId
                : UUID.randomUUID().toString();

        final String finalMimeTypeLocal = mimeType;

        implementation.uploadDataBytes(bytes, fileName, finalMimeTypeLocal, (bytesWritten, contentLength, percent) -> {
            JSObject evt = new JSObject();
            evt.put("uploadId", uploadId);
            evt.put("progress", percent);
            evt.put("bytesWritten", bytesWritten);
            evt.put("contentLength", contentLength);

            String mt = "image";
            if (finalMimeTypeLocal != null) {
                mt = finalMimeTypeLocal.toLowerCase(Locale.US).startsWith("video/") ? "video" : "image";
            }
            evt.put("mediaType", mt);

            notifyListeners("uploadProgress", evt);
        }, new CapUploadCare.UploadCallback() {
            @Override
            public void onSuccess(Map<String, Object> fileMap) {
                JSObject fileObj = new JSObject();
                fileObj.put("uuid", fileMap.get("uuid"));
                fileObj.put("cdnUrl", fileMap.get("cdnUrl"));

                if (fileMap.containsKey("filename")) fileObj.put("filename", fileMap.get("filename"));
                if (fileMap.containsKey("sizeBytes")) fileObj.put("sizeBytes", fileMap.get("sizeBytes"));
                if (fileMap.containsKey("mimeType")) fileObj.put("mimeType", fileMap.get("mimeType"));
                if (fileMap.containsKey("width")) fileObj.put("width", fileMap.get("width"));
                if (fileMap.containsKey("height")) fileObj.put("height", fileMap.get("height"));

                JSArray files = new JSArray();
                files.put(fileObj);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("cancelled", false);
                ret.put("uploadId", uploadId);
                ret.put("files", files);

                call.resolve(ret);
            }

            @Override
            public void onError(Exception error) {
                call.reject(error.getMessage(), error);
            }
        });
    }

    @PluginMethod
    public void uploadPicked(PluginCall call) {
        String localId = call.getString("localId");
        String fileName = call.getString("fileName");

        if (localId == null || localId.isEmpty()) {
            call.reject("localId is required");
            return;
        }
        if (fileName == null || fileName.isEmpty()) {
            call.reject("fileName is required");
            return;
        }

        Uri uri = pickedById.get(localId);
        String mediaType = pickedTypeById.get(localId);

        if (uri == null || mediaType == null) {
            call.reject("Unknown localId (maybe app restarted). Pick media again.");
            return;
        }

        // Allow JS to provide uploadId so UI can track progress immediately
        String providedUploadId = call.getString("uploadId");
        final String uploadId = (providedUploadId != null && !providedUploadId.isEmpty())
                ? providedUploadId
                : UUID.randomUUID().toString();

        final String finalMediaTypeLocal = mediaType;

        implementation.uploadUri(
                getContext(),
                uri,
                finalMediaTypeLocal,
                fileName,
                (bytesWritten, contentLength, percent) -> {
                    JSObject evt = new JSObject();
                    evt.put("uploadId", uploadId);
                    evt.put("progress", percent);
                    evt.put("bytesWritten", bytesWritten);
                    evt.put("contentLength", contentLength);
                    evt.put("mediaType", finalMediaTypeLocal);
                    notifyListeners("uploadProgress", evt);
                },
                new CapUploadCare.UploadCallback() {
                    @Override
                    public void onSuccess(Map<String, Object> fileMap) {
                        JSObject fileObj = new JSObject();
                        fileObj.put("uuid", fileMap.get("uuid"));
                        fileObj.put("cdnUrl", fileMap.get("cdnUrl"));
                        if (fileMap.containsKey("filename")) fileObj.put("filename", fileMap.get("filename"));
                        if (fileMap.containsKey("sizeBytes")) fileObj.put("sizeBytes", fileMap.get("sizeBytes"));
                        if (fileMap.containsKey("mimeType")) fileObj.put("mimeType", fileMap.get("mimeType"));
                        if (fileMap.containsKey("width")) fileObj.put("width", fileMap.get("width"));
                        if (fileMap.containsKey("height")) fileObj.put("height", fileMap.get("height"));

                        JSArray files = new JSArray();
                        files.put(fileObj);

                        JSObject ret = new JSObject();
                        ret.put("success", true);
                        ret.put("cancelled", false);
                        ret.put("uploadId", uploadId);
                        ret.put("files", files);

                        call.resolve(ret);

                        pickedById.remove(localId);
                        pickedTypeById.remove(localId);
                    }

                    @Override
                    public void onError(Exception error) {
                        call.reject(error.getMessage(), error);
                    }
                }
        );
    }

    @PluginMethod
    public void pickMedia(PluginCall call) {
        if (pendingCall != null) {
            call.reject("A picker flow is already in progress");
            return;
        }

        String mediaType = null;

        JSObject options = call.getObject("options");
        if (options != null) {
            mediaType = options.getString("mediaType");
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            mediaType = call.getString("mediaType"); // top-level (how TS calls it)
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            mediaType = "any";
        }

        mediaType = mediaType.toLowerCase(Locale.US);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (mediaType.equals("image")) {
            intent.setType("image/*");
        } else if (mediaType.equals("video")) {
            intent.setType("video/*");
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }

        pendingCall = call;
        startActivityForResult(call, intent, "handlePickMediaResult");
    }

    @ActivityCallback
    private void handlePickerResult(PluginCall call, ActivityResult result) {
        if (pendingCall == null) return;

        PluginCall savedCall = pendingCall;
        pendingCall = null;

        if (result.getResultCode() == Activity.RESULT_CANCELED || result.getData() == null) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("cancelled", true);
            ret.put("files", new JSArray());
            savedCall.resolve(ret);
            return;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            savedCall.reject("No file selected");
            return;
        }

        // Read from the original call payload (options OR top-level)
        String mediaType = null;

        JSObject options = savedCall.getObject("options");
        if (options != null) {
            mediaType = options.getString("mediaType");
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            mediaType = savedCall.getString("mediaType");
        }

        if (mediaType == null || mediaType.trim().isEmpty()) {
            mediaType = "any";
        }

        mediaType = mediaType.toLowerCase(Locale.US);

        // Allow JS to provide uploadId so UI can track progress immediately
        String providedUploadId = savedCall.getString("uploadId");
        final String uploadId = (providedUploadId != null && !providedUploadId.isEmpty())
                ? providedUploadId
                : UUID.randomUUID().toString();

        Context context = getContext();

        // If caller passed "any", infer actual type from the picked Uri so progress always includes "image" or "video"
        String inferredType = "image";
        try {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) {
                inferredType = "video";
            }
        } catch (Exception ignored) {}

        final String progressMediaType = mediaType.equals("any") ? inferredType : mediaType;

        implementation.uploadSingle(
                context,
                uri,
                mediaType, // keep original requested behavior ("any" still works inside implementation)
                (bytesWritten, contentLength, percent) -> {
                    JSObject evt = new JSObject();
                    evt.put("uploadId", uploadId);
                    evt.put("progress", percent);
                    evt.put("bytesWritten", bytesWritten);
                    evt.put("contentLength", contentLength);
                    evt.put("mediaType", progressMediaType); // always "image" or "video"
                    notifyListeners("uploadProgress", evt);
                },
                new CapUploadCare.UploadCallback() {
                    @Override
                    public void onSuccess(Map<String, Object> fileMap) {
                        JSObject fileObj = new JSObject();
                        fileObj.put("uuid", fileMap.get("uuid"));
                        fileObj.put("cdnUrl", fileMap.get("cdnUrl"));

                        if (fileMap.containsKey("filename")) fileObj.put("filename", fileMap.get("filename"));
                        if (fileMap.containsKey("sizeBytes")) fileObj.put("sizeBytes", fileMap.get("sizeBytes"));
                        if (fileMap.containsKey("mimeType")) fileObj.put("mimeType", fileMap.get("mimeType"));
                        if (fileMap.containsKey("width")) fileObj.put("width", fileMap.get("width"));
                        if (fileMap.containsKey("height")) fileObj.put("height", fileMap.get("height"));

                        JSArray files = new JSArray();
                        files.put(fileObj);

                        JSObject ret = new JSObject();
                        ret.put("success", true);
                        ret.put("cancelled", false);
                        ret.put("uploadId", uploadId);
                        ret.put("files", files);

                        savedCall.resolve(ret);
                    }

                    @Override
                    public void onError(Exception error) {
                        savedCall.reject(error.getMessage(), error);
                    }
                }
        );
    }

    @ActivityCallback
    private void handlePickMediaResult(PluginCall call, ActivityResult result) {
        if (pendingCall == null) return;

        PluginCall savedCall = pendingCall;
        pendingCall = null;

        if (result.getResultCode() == Activity.RESULT_CANCELED || result.getData() == null) {
            savedCall.reject("User cancelled");
            return;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            savedCall.reject("No file selected");
            return;
        }

        try {
            final int takeFlags = result.getData().getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) { }

        Context context = getContext();
        String mime = context.getContentResolver().getType(uri);
        String inferredType = (mime != null && mime.toLowerCase(Locale.US).startsWith("video/")) ? "video" : "image";

        CapUploadCare.ValidationResult vr;
        if (inferredType.equals("video")) {
            vr = implementation.validateVideo(context, uri);
        } else {
            vr = implementation.validateImage(context, uri);
        }
        if (!vr.ok) {
            savedCall.reject(vr.errorMessage);
            return;
        }

        String localId = UUID.randomUUID().toString();
        pickedById.put(localId, uri);
        pickedTypeById.put(localId, inferredType);

        JSObject ret = new JSObject();
        ret.put("localId", localId);
        ret.put("uri", uri.toString());
        ret.put("mediaType", inferredType);
        if (vr.mimeType != null) ret.put("mimeType", vr.mimeType);
        if (vr.displayName != null) ret.put("displayName", vr.displayName);
        if (vr.sizeBytes >= 0) ret.put("sizeBytes", vr.sizeBytes);
        if ("image".equals(inferredType)) {
            if (vr.width != null) ret.put("width", vr.width);
            if (vr.height != null) ret.put("height", vr.height);
        }
        if ("video".equals(inferredType)) {
            if (vr.durationMs != null) ret.put("durationMs", vr.durationMs);
        }

        savedCall.resolve(ret);
    }
}