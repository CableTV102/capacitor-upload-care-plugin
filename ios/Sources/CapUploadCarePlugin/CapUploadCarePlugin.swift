import AVFoundation
import Capacitor
import Foundation
import UIKit

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

    private var debugEnabled = false

    private static let imageMaxBytes = 8 * 1024 * 1024
    private static let imageMinSmallestDimension: CGFloat = 566

    private static let videoMaxBytes: Int64 = 512 * 1024 * 1024
    private static let videoMinSeconds: Double = 3
    private static let videoMaxSeconds: Double = 60
    private static let videoMinSmallestDimension: CGFloat = 608
    private static let videoMaxLargestDimension: CGFloat = 1080

    private static let allowedImageExts: Set<String> = [
        "jpg", "jpeg", "png", "heic", "heif", "avif", "bmp",
    ]
    private static let allowedVideoExts: Set<String> = [
        "mp4", "mov", "mpeg", "mpg", "3gp", "avi", "m4v",
    ]

    @objc func configure(_ call: CAPPluginCall) {
        guard let publicKey = call.getString("publicKey"), !publicKey.isEmpty else {
            call.reject("publicKey is required")
            return
        }

        let secretKey = call.getString("secretKey")
        let _ = call.getString("cdnBase")
        let _ = call.getString("apiBase")
        let debug = call.getBool("debug") ?? false

        debugEnabled = debug

        implementation.configure(
            publicKey: publicKey,
            secretKey: secretKey,
            debug: debug
        )

        call.resolve()
    }

    @objc func openUploader(_ call: CAPPluginCall) {
        if pendingCall != nil {
            call.reject("An upload is already in progress")
            return
        }

        let options = call.getObject("options") ?? [:]
        let allowedMimeTypes = options["allowedMimeTypes"] as? [String] ?? []
        let wantsVideo =
            allowedMimeTypes.contains { $0.lowercased().hasPrefix("video/") }
            || allowedMimeTypes.contains("video/*")

        DispatchQueue.main.async {
            guard let viewController = self.bridge?.viewController else {
                call.reject("No active view controller to present uploader")
                return
            }

            let picker = UIImagePickerController()
            picker.sourceType = .photoLibrary
            picker.delegate = self

            if wantsVideo {
                picker.mediaTypes = ["public.movie"]
            } else {
                picker.mediaTypes = ["public.image"]
            }

            self.pendingCall = call
            viewController.present(picker, animated: true)
        }
    }

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

        var mediaType = "image"
        if dataUri.starts(with: "data:") {
            if let headerEnd = dataUri.range(of: ";base64,")?.lowerBound {
                let header = String(
                    dataUri[dataUri.index(dataUri.startIndex, offsetBy: 5)..<headerEnd]
                ).lowercased()
                if header.hasPrefix("video/") {
                    mediaType = "video"
                }
            }
        }

        let uploadId = UUID().uuidString
        notifyListeners(
            "uploadProgress",
            data: [
                "uploadId": uploadId,
                "mediaType": mediaType,
                "progress": 0,
            ])

        implementation.upload(
            data: data,
            fileName: fileName,
            onProgress: { progress in
                let pct = Int(progress * 100)
                self.notifyListeners(
                    "uploadProgress",
                    data: [
                        "uploadId": uploadId,
                        "mediaType": mediaType,
                        "progress": pct,
                    ])
            },
            completion: { result in
                switch result {
                case .failure(let error):
                    call.reject(error.localizedDescription)

                case .success(let fileDict):
                    call.resolve([
                        "success": true,
                        "cancelled": false,
                        "uploadId": uploadId,
                        "files": [fileDict],
                    ])
                }
            }
        )
    }

    private func fileSizeBytes(for url: URL) -> Int64? {
        do {
            let values = try url.resourceValues(forKeys: [.fileSizeKey])
            if let size = values.fileSize {
                return Int64(size)
            }
        } catch {}
        return nil
    }

    private func extLowercased(for url: URL) -> String {
        return url.pathExtension.lowercased()
    }

    private func validateAndUploadImage(imageUrl: URL, call: CAPPluginCall) {
        let ext = extLowercased(for: imageUrl)
        if !Self.allowedImageExts.contains(ext) {
            call.reject("Unsupported image format: .\(ext)")
            return
        }

        guard let bytes = fileSizeBytes(for: imageUrl) else {
            call.reject("Could not read image file size")
            return
        }

        if bytes > Int64(Self.imageMaxBytes) {
            call.reject("Image is too large. Max size is 8MB.")
            return
        }

        guard let data = try? Data(contentsOf: imageUrl) else {
            call.reject("Could not read image data")
            return
        }

        guard let image = UIImage(data: data) else {
            call.reject("Could not decode image")
            return
        }

        let smallest = min(image.size.width, image.size.height)
        if smallest < Self.imageMinSmallestDimension {
            call.reject("Image resolution too small. Minimum smallest dimension is 566px.")
            return
        }

        let uploadId = UUID().uuidString
        notifyListeners(
            "uploadProgress",
            data: [
                "uploadId": uploadId,
                "mediaType": "image",
                "progress": 0,
            ])

        let fileName = "image-\(Int(Date().timeIntervalSince1970)).\(ext)"

        implementation.upload(
            fileUrl: imageUrl,
            fileName: fileName,
            onProgress: { progress in
                let pct = Int(progress * 100)
                self.notifyListeners(
                    "uploadProgress",
                    data: [
                        "uploadId": uploadId,
                        "mediaType": "image",
                        "progress": pct,
                    ])
            },
            completion: { result in
                switch result {
                case .failure(let error):
                    call.reject(error.localizedDescription)

                case .success(let fileDict):
                    call.resolve([
                        "success": true,
                        "cancelled": false,
                        "uploadId": uploadId,
                        "files": [fileDict],
                    ])
                }
            }
        )
    }

    private func assetDimensions(_ asset: AVAsset) -> CGSize? {
        guard let track = asset.tracks(withMediaType: .video).first else { return nil }
        let size = track.naturalSize.applying(track.preferredTransform)
        return CGSize(width: abs(size.width), height: abs(size.height))
    }

    private func transcodeTo1080IfNeeded(
        inputUrl: URL, completion: @escaping (Result<URL, Error>) -> Void
    ) {
        let asset = AVURLAsset(url: inputUrl)
        guard let dims = assetDimensions(asset) else {
            completion(
                .failure(
                    NSError(
                        domain: "CapUploadCare", code: 1,
                        userInfo: [NSLocalizedDescriptionKey: "Could not read video dimensions"])))
            return
        }

        let largest = max(dims.width, dims.height)
        if largest <= Self.videoMaxLargestDimension {
            completion(.success(inputUrl))
            return
        }

        guard
            let export = AVAssetExportSession(
                asset: asset, presetName: AVAssetExportPreset1920x1080)
        else {
            completion(
                .failure(
                    NSError(
                        domain: "CapUploadCare", code: 2,
                        userInfo: [
                            NSLocalizedDescriptionKey:
                                "Could not create export session for transcoding"
                        ])))
            return
        }

        let outUrl = FileManager.default.temporaryDirectory
            .appendingPathComponent("uploadcare-video-\(UUID().uuidString).mp4")

        export.outputURL = outUrl
        export.outputFileType = .mp4
        export.shouldOptimizeForNetworkUse = true

        export.exportAsynchronously {
            switch export.status {
            case .completed:
                completion(.success(outUrl))
            case .failed:
                completion(
                    .failure(
                        export.error
                            ?? NSError(
                                domain: "CapUploadCare", code: 3,
                                userInfo: [NSLocalizedDescriptionKey: "Transcoding failed"])))
            case .cancelled:
                completion(
                    .failure(
                        NSError(
                            domain: "CapUploadCare", code: 4,
                            userInfo: [NSLocalizedDescriptionKey: "Transcoding cancelled"])))
            default:
                completion(
                    .failure(
                        NSError(
                            domain: "CapUploadCare", code: 5,
                            userInfo: [NSLocalizedDescriptionKey: "Transcoding did not complete"])))
            }
        }
    }

    private func validateAndUploadVideo(videoUrl: URL, call: CAPPluginCall) {
        let ext = extLowercased(for: videoUrl)
        if !Self.allowedVideoExts.contains(ext) {
            call.reject("Unsupported video format: .\(ext)")
            return
        }

        let asset = AVURLAsset(url: videoUrl)
        let durationSeconds = CMTimeGetSeconds(asset.duration)

        if durationSeconds.isNaN || durationSeconds.isInfinite {
            call.reject("Could not read video duration")
            return
        }

        if durationSeconds < Self.videoMinSeconds {
            call.reject("Video is too short. Minimum length is 3 seconds.")
            return
        }

        if durationSeconds > Self.videoMaxSeconds {
            call.reject("Video is too long. Maximum length is 60 seconds.")
            return
        }

        guard let dims = assetDimensions(asset) else {
            call.reject("Could not read video dimensions")
            return
        }

        let smallest = min(dims.width, dims.height)
        if smallest < Self.videoMinSmallestDimension {
            call.reject("Video resolution too small. Minimum smallest dimension is 608px.")
            return
        }

        let uploadId = UUID().uuidString
        notifyListeners(
            "uploadProgress",
            data: [
                "uploadId": uploadId,
                "mediaType": "video",
                "progress": 0,
            ])

        transcodeTo1080IfNeeded(inputUrl: videoUrl) { result in
            switch result {
            case .failure(let error):
                call.reject(error.localizedDescription)

            case .success(let outputUrl):
                if let bytes = self.fileSizeBytes(for: outputUrl) {
                    if bytes > Self.videoMaxBytes {
                        call.reject("Video is too large. Max size is 512MB.")
                        return
                    }
                } else {
                    call.reject("Could not read transcoded video file size")
                    return
                }

                let fileName =
                    "video-\(Int(Date().timeIntervalSince1970)).\(self.extLowercased(for: outputUrl))"

                self.implementation.upload(
                    fileUrl: outputUrl,
                    fileName: fileName,
                    onProgress: { progress in
                        let pct = Int(progress * 100)
                        self.notifyListeners(
                            "uploadProgress",
                            data: [
                                "uploadId": uploadId,
                                "mediaType": "video",
                                "progress": pct,
                            ])
                    },
                    completion: { uploadResult in
                        switch uploadResult {
                        case .failure(let uploadError):
                            call.reject(uploadError.localizedDescription)

                        case .success(let fileDict):
                            call.resolve([
                                "success": true,
                                "cancelled": false,
                                "uploadId": uploadId,
                                "files": [fileDict],
                            ])
                        }
                    }
                )
            }
        }
    }
}

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

        guard let call = pendingCall else { return }
        pendingCall = nil

        if let imageUrl = info[.imageURL] as? URL {
            validateAndUploadImage(imageUrl: imageUrl, call: call)
            return
        }

        if let videoUrl = info[.mediaURL] as? URL {
            validateAndUploadVideo(videoUrl: videoUrl, call: call)
            return
        }

        call.reject("No media URL returned from picker")
    }
}
