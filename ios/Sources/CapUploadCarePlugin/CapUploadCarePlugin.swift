import Capacitor
import Foundation
import UIKit

/// Please read the Capacitor iOS Plugin Development Guide
/// here: https://capacitorjs.com/docs/plugins/ios
@objc(CapUploadCarePlugin)
public class CapUploadCarePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapUploadCarePlugin"
    public let jsName = "CapUploadCare"

    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openUploader", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "uploadDataUri", returnType: CAPPluginReturnPromise),
    ]

    private let implementation = CapUploadCare()
    private var pendingCall: CAPPluginCall?

    // MARK: - JS API

    /// Configure Uploadcare with the keys passed from JS
    /// args: { publicKey: string; secretKey?: string; cdnBase?: string; apiBase?: string; debug?: boolean }
    @objc func configure(_ call: CAPPluginCall) {
        guard let publicKey = call.getString("publicKey"), !publicKey.isEmpty else {
            call.reject("publicKey is required")
            return
        }

        let secretKey = call.getString("secretKey")
        // You can still read these if you like, we just won't pass them down yet.
        let _ = call.getString("cdnBase")
        let _ = call.getString("apiBase")
        let debug = call.getBool("debug") ?? false

        implementation.configure(
            publicKey: publicKey,
            secretKey: secretKey,
            debug: debug  // only 3 args now
        )

        call.resolve()
    }

    /// Open the native image picker, upload to Uploadcare, and return the result.
    /// args: { options?: UploadCareUploadOptions } – currently ignored on iOS, but
    /// wired so we can later support multiple, cropping, etc.
    @objc func openUploader(_ call: CAPPluginCall) {
        // Prevent multiple simultaneous uploads
        if pendingCall != nil {
            call.reject("An upload is already in progress")
            return
        }

        DispatchQueue.main.async {
            guard let viewController = self.bridge?.viewController else {
                call.reject("No active view controller to present uploader")
                return
            }

            let picker = UIImagePickerController()
            picker.sourceType = .photoLibrary
            picker.mediaTypes = ["public.image"]
            picker.delegate = self

            self.pendingCall = call
            viewController.present(picker, animated: true)
        }
    }

    /// Upload a base64 data URI directly (no native picker).
    /// args: { dataUri: string; fileName: string }
    @objc func uploadDataUri(_ call: CAPPluginCall) {
        guard let dataUri = call.getString("dataUri"), !dataUri.isEmpty else {
            call.reject("dataUri is required")
            return
        }

        guard let fileName = call.getString("fileName"), !fileName.isEmpty else {
            call.reject("fileName is required")
            return
        }

        guard let commaIndex = dataUri.firstIndex(of: ",") else {
            call.reject("Invalid dataUri format, missing comma")
            return
        }

        let base64Start = dataUri.index(after: commaIndex)
        let base64String = String(dataUri[base64Start...])

        guard let data = Data(base64Encoded: base64String) else {
            call.reject("Invalid base64 data in dataUri")
            return
        }

        implementation.upload(data: data, fileName: fileName) { result in
            switch result {
            case .failure(let error):
                call.reject(error.localizedDescription)

            case .success(let fileDict):
                call.resolve([
                    "success": true,
                    "cancelled": false,
                    "files": [fileDict],
                ])
            }
        }
    }
}

// MARK: - UIImagePickerControllerDelegate

extension CapUploadCarePlugin: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)

        guard let call = pendingCall else { return }
        call.resolve([
            "success": false,
            "cancelled": true,
            "files": [],
        ])
        pendingCall = nil
    }

    public func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true)

        guard
            let image = info[.originalImage] as? UIImage,
            let data = image.jpegData(compressionQuality: 0.9)
        else {
            pendingCall?.reject("Could not get image data from picker")
            pendingCall = nil
            return
        }

        let fileName = "image-\(Int(Date().timeIntervalSince1970)).jpg"

        implementation.upload(data: data, fileName: fileName) { result in
            guard let call = self.pendingCall else { return }

            switch result {
            case .failure(let error):
                call.reject(error.localizedDescription)

            case .success(let fileDict):
                call.resolve([
                    "success": true,
                    "cancelled": false,
                    "files": [fileDict],
                ])
            }

            self.pendingCall = nil
        }
    }
}
