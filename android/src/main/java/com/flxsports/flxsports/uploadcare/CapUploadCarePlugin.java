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

import java.util.Map;

@CapacitorPlugin(name = "CapUploadCare")
public class CapUploadCarePlugin extends Plugin {

    private final CapUploadCare implementation = new CapUploadCare();
    private PluginCall pendingCall;

    // JS: CapUploadCare.configure({ publicKey, secretKey?, debug? })
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

    // JS: CapUploadCare.openUploader(options?)
    @PluginMethod
    public void openUploader(PluginCall call) {
        // Prevent multiple concurrent uploads
        if (pendingCall != null) {
            call.reject("An upload is already in progress");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");

        pendingCall = call;

        // This will call handleImagePickerResult once the user finishes selection
        startActivityForResult(call, intent, "handleImagePickerResult");
    }

    // JS: CapUploadCare.uploadDataUri({ dataUri, fileName })
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

        int commaIndex = dataUri.indexOf(",");
        if (commaIndex == -1) {
            call.reject("Invalid dataUri format, missing comma");
            return;
        }

        String base64Part = dataUri.substring(commaIndex + 1);

        byte[] bytes;
        try {
            bytes = Base64.decode(base64Part, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            call.reject("Invalid base64 data in dataUri", e);
            return;
        }

        implementation.uploadDataBytes(bytes, fileName, new CapUploadCare.UploadCallback() {
            @Override
            public void onSuccess(Map<String, Object> fileMap) {
                JSObject fileObj = new JSObject();
                fileObj.put("uuid", fileMap.get("uuid"));
                fileObj.put("cdnUrl", fileMap.get("cdnUrl"));

                if (fileMap.containsKey("filename")) {
                    fileObj.put("filename", fileMap.get("filename"));
                }
                if (fileMap.containsKey("sizeBytes")) {
                    fileObj.put("sizeBytes", fileMap.get("sizeBytes"));
                }
                if (fileMap.containsKey("mimeType")) {
                    fileObj.put("mimeType", fileMap.get("mimeType"));
                }

                JSArray filesArray = new JSArray();
                filesArray.put(fileObj);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("cancelled", false);
                ret.put("files", filesArray);

                call.resolve(ret);
            }

            @Override
            public void onError(Exception error) {
            savedCall.reject(
                error.getMessage() != null ? error.getMessage() : "Upload failed",
                error
            );
            }
        });
    }

    @ActivityCallback
    private void handleImagePickerResult(PluginCall call, ActivityResult result) {
        if (pendingCall == null) {
            // Nothing to do
            return;
        }

        PluginCall savedCall = pendingCall;
        pendingCall = null;

        int resultCode = result.getResultCode();
        Intent data = result.getData();

        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("cancelled", true);
            ret.put("files", new JSArray());
            savedCall.resolve(ret);
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            savedCall.reject("No image selected");
            return;
        }

        Context context = getContext();

        // Delegate upload to our implementation (Uploadcare SDK)
        implementation.uploadSingle(context, uri, new CapUploadCare.UploadCallback() {
            @Override
            public void onSuccess(Map<String, Object> fileMap) {
                JSObject fileObj = new JSObject();
                fileObj.put("uuid", fileMap.get("uuid"));
                fileObj.put("cdnUrl", fileMap.get("cdnUrl"));

                if (fileMap.containsKey("filename")) {
                    fileObj.put("filename", fileMap.get("filename"));
                }
                if (fileMap.containsKey("sizeBytes")) {
                    fileObj.put("sizeBytes", fileMap.get("sizeBytes"));
                }
                if (fileMap.containsKey("mimeType")) {
                    fileObj.put("mimeType", fileMap.get("mimeType"));
                }

                JSArray filesArray = new JSArray();
                filesArray.put(fileObj);

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("cancelled", false);
                ret.put("files", filesArray);

                savedCall.resolve(ret);
            }

            @Override
            public void onError(Exception error) {
            savedCall.reject(
                error.getMessage() != null ? error.getMessage() : "Upload failed",
                error
            );
            }
        });
    }
}