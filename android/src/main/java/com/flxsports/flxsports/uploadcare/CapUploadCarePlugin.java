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

@CapacitorPlugin(name = "CapUploadCare")
public class CapUploadCarePlugin extends Plugin {

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
        mediaType = mediaType.toLowerCase(Locale.US);

        Intent intent = new Intent(Intent.ACTION_PICK);

        if (mediaType.equals("image")) {
            intent.setType("image/*");
        } else if (mediaType.equals("video")) {
            intent.setType("video/*");
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "image/*", "video/*" });
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

        final String uploadId = UUID.randomUUID().toString();

        implementation.uploadDataBytes(bytes, fileName, mimeType, (bytesWritten, contentLength, percent) -> {
            JSObject evt = new JSObject();
            evt.put("uploadId", uploadId);
            evt.put("progress", percent);
            evt.put("bytesWritten", bytesWritten);
            evt.put("contentLength", contentLength);
            if (mimeType != null) {
                evt.put("mediaType", mimeType.toLowerCase(Locale.US).startsWith("video/") ? "video" : "image");
            }
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

        Context context = getContext();
        final String uploadId = UUID.randomUUID().toString();

        implementation.uploadSingle(context, uri, mediaType, (bytesWritten, contentLength, percent) -> {
            JSObject evt = new JSObject();
            evt.put("uploadId", uploadId);
            evt.put("progress", percent);
            evt.put("bytesWritten", bytesWritten);
            evt.put("contentLength", contentLength);

            String mt = mediaType.toLowerCase(Locale.US);
            if (mt.equals("image") || mt.equals("video")) {
                evt.put("mediaType", mt);
            }

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

                savedCall.resolve(ret);
            }

            @Override
            public void onError(Exception error) {
                savedCall.reject(error.getMessage(), error);
            }
        });
    }
}