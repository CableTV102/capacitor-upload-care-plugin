import AVFoundation
import Capacitor
import Foundation
import UIKit
import UniformTypeIdentifiers

@objc(CapUploadCarePlugin)
public class CapUploadCarePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapUploadCarePlugin"
    public let jsName = "CapUploadCare"

    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openUploader", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "uploadDataUri", returnType: CAPPluginReturnPromise),

        // New staged workflow
        CAPPluginMethod(name: "pickMedia", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "uploadPicked", returnType: CAPPluginReturnPromise),
    ]

    private let implementation = CapUploadCare()
    private var pendingCall: CAPPluginCall?

    private enum PendingMode {
        case openUploader(options: [String: Any])
        case pickMedia(options: [String: Any])
    }
    private var pendingMode: PendingMode?

    // localId -> temp file URL we control (stable for later upload)
    private var pickedById: [String: URL] = [:]
    private var pickedTypeById: [String: String] = [:]  // "image" | "video"

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

    // MARK: - Public API

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

    // Existing: pick + upload immediately (kept for backcompat)
    @objc func openUploader(_ call: CAPPluginCall) {
        if pendingCall != nil {
            call.reject("An upload is already in progress")
            return
        }
        var options = call.getObject("options") ?? [:]
        if options.isEmpty {
            options = call.options
        }

        pendingCall = call
        pendingMode = .openUploader(options: options)
        presentPicker(options: options, call: call)
    }

    // New: pick without upload
    @objc func pickMedia(_ call: CAPPluginCall) {
        if pendingCall != nil {
            call.reject("A picker is already in progress")
            return
        }
        var options = call.getObject("options") ?? [:]
        if options.isEmpty {
            options = call.options
        }
        pendingCall = call
        pendingMode = .pickMedia(options: options)
        presentPicker(options: options, call: call)
    }

    // New: upload later using localId from pickMedia
    @objc func uploadPicked(_ call: CAPPluginCall) {
        guard let localId = call.getString("localId"), !localId.isEmpty else {
            call.reject("localId is required")
            return
        }

        guard let fileName = call.getString("fileName"), !fileName.isEmpty else {
            call.reject("fileName is required")
            return
        }

        guard let url = pickedById[localId],
            let mediaType = pickedTypeById[localId]
        else {
            call.reject("No picked media found for localId: \(localId)")
            return
        }

        let providedUploadId = call.getString("uploadId")
        let uploadId =
            (providedUploadId != nil && !(providedUploadId!.isEmpty))
            ? providedUploadId!
            : UUID().uuidString

        notifyListeners(
            "uploadProgress",
            data: [
                "uploadId": uploadId,
                "mediaType": mediaType,
                "progress": 0,
            ])

        if mediaType == "image" {
            validateAndUploadImage(
                imageUrl: url, fileName: fileName, call: call, uploadId: uploadId)
        } else {
            validateAndUploadVideo(
                videoUrl: url, fileName: fileName, call: call, uploadId: uploadId)
        }
    }

    // Existing: upload a base64 data URI
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

        // Allow JS to provide uploadId so UI can track progress immediately
        let providedUploadId = call.getString("uploadId")
        let uploadId =
            (providedUploadId != nil && !(providedUploadId!.isEmpty))
            ? providedUploadId!
            : UUID().uuidString

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

    // MARK: - Picker presentation

    private func presentPicker(options: [String: Any], call: CAPPluginCall) {
        let mediaTypeOpt =
            (options["mediaType"] as? String)?.lowercased()
            ?? call.getString("mediaType")?.lowercased()
            ?? "any"

        DispatchQueue.main.async {
            guard let viewController = self.bridge?.viewController else {
                call.reject("No active view controller to present picker")
                self.pendingCall = nil
                self.pendingMode = nil
                return
            }

            let picker = UIImagePickerController()
            picker.sourceType = .photoLibrary
            picker.delegate = self

            if mediaTypeOpt == "video" {
                picker.mediaTypes = ["public.movie"]
            } else if mediaTypeOpt == "image" {
                picker.mediaTypes = ["public.image"]
            } else {
                picker.mediaTypes = ["public.image", "public.movie"]
            }

            viewController.present(picker, animated: true)
        }
    }

    // MARK: - Local staging helpers

    private func copyToTemp(originalUrl: URL) throws -> URL {
        let ext = originalUrl.pathExtension.isEmpty ? "bin" : originalUrl.pathExtension
        let outUrl = FileManager.default.temporaryDirectory
            .appendingPathComponent("capuploadcare-\(UUID().uuidString).\(ext)")

        if FileManager.default.fileExists(atPath: outUrl.path) {
            try FileManager.default.removeItem(at: outUrl)
        }

        try FileManager.default.copyItem(at: originalUrl, to: outUrl)
        return outUrl
    }

    private func mimeTypeForUrl(_ url: URL) -> String? {
        if #available(iOS 14.0, *) {
            let ext = url.pathExtension
            if let ut = UTType(filenameExtension: ext),
                let mime = ut.preferredMIMEType
            {
                return mime
            }
        }
        return nil
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

    private func imageDimensions(for url: URL) -> (w: Int, h: Int)? {
        guard let data = try? Data(contentsOf: url),
            let image = UIImage(data: data)
        else { return nil }
        return (Int(image.size.width), Int(image.size.height))
    }

    private func videoMetadata(for url: URL) -> (w: Int?, h: Int?, durationMs: Int?) {
        let asset = AVURLAsset(url: url)
        let durationSeconds = CMTimeGetSeconds(asset.duration)
        let durationMs: Int? =
            (durationSeconds.isFinite && !durationSeconds.isNaN)
            ? Int(durationSeconds * 1000.0)
            : nil

        var dims: CGSize?
        if let track = asset.tracks(withMediaType: .video).first {
            let size = track.naturalSize.applying(track.preferredTransform)
            dims = CGSize(width: abs(size.width), height: abs(size.height))
        }

        return (dims.map { Int($0.width) }, dims.map { Int($0.height) }, durationMs)
    }

    // MARK: - Validation + upload (now accepts caller-provided fileName)

    private func validateAndUploadImage(
        imageUrl: URL, fileName: String, call: CAPPluginCall, uploadId: String
    ) {
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

        // Emit initial progress using the SAME uploadId
        notifyListeners(
            "uploadProgress",
            data: [
                "uploadId": uploadId,
                "mediaType": "image",
                "progress": 0,
            ])

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
        inputUrl: URL,
        completion: @escaping (Result<URL, Error>) -> Void
    ) {
        let asset = AVURLAsset(url: inputUrl)
        guard let dims = assetDimensions(asset) else {
            completion(
                .failure(
                    NSError(
                        domain: "CapUploadCare",
                        code: 1,
                        userInfo: [NSLocalizedDescriptionKey: "Could not read video dimensions"]
                    )))
            return
        }

        let largest = max(dims.width, dims.height)
        if largest <= Self.videoMaxLargestDimension {
            completion(.success(inputUrl))
            return
        }

        guard
            let export = AVAssetExportSession(
                asset: asset,
                presetName: AVAssetExportPreset1920x1080
            )
        else {
            completion(
                .failure(
                    NSError(
                        domain: "CapUploadCare",
                        code: 2,
                        userInfo: [
                            NSLocalizedDescriptionKey:
                                "Could not create export session for transcoding"
                        ]
                    )))
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
                                domain: "CapUploadCare",
                                code: 3,
                                userInfo: [NSLocalizedDescriptionKey: "Transcoding failed"]
                            )))
            case .cancelled:
                completion(
                    .failure(
                        NSError(
                            domain: "CapUploadCare",
                            code: 4,
                            userInfo: [NSLocalizedDescriptionKey: "Transcoding cancelled"]
                        )))
            default:
                completion(
                    .failure(
                        NSError(
                            domain: "CapUploadCare",
                            code: 5,
                            userInfo: [NSLocalizedDescriptionKey: "Transcoding did not complete"]
                        )))
            }
        }
    }

    private func validateAndUploadVideo(
        videoUrl: URL, fileName: String, call: CAPPluginCall, uploadId: String
    ) {
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
                guard let bytes = self.fileSizeBytes(for: outputUrl) else {
                    call.reject("Could not read transcoded video file size")
                    return
                }
                if bytes > Self.videoMaxBytes {
                    call.reject("Video is too large. Max size is 512MB.")
                    return
                }

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

// MARK: - Picker Delegate

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
        pendingMode = nil
    }

    public func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true)

        guard let call = pendingCall,
            let mode = pendingMode
        else {
            return
        }

        pendingCall = nil
        pendingMode = nil

        if let imageUrl = info[.imageURL] as? URL {
            handlePicked(url: imageUrl, mediaType: "image", call: call, mode: mode)
            return
        }

        if let videoUrl = info[.mediaURL] as? URL {
            handlePicked(url: videoUrl, mediaType: "video", call: call, mode: mode)
            return
        }

        if let image = info[.originalImage] as? UIImage {
            let tmp = FileManager.default.temporaryDirectory
                .appendingPathComponent("capuploadcare-\(UUID().uuidString).jpg")
            if let data = image.jpegData(compressionQuality: 0.95) {
                do {
                    try data.write(to: tmp)
                    handlePicked(url: tmp, mediaType: "image", call: call, mode: mode)
                    return
                } catch {
                    call.reject(
                        "Failed to write picked image to temp: \(error.localizedDescription)")
                    return
                }
            }
        }

        call.reject("No media URL returned from picker")
    }

    private func handlePicked(url: URL, mediaType: String, call: CAPPluginCall, mode: PendingMode) {
        do {
            let tempUrl = try copyToTemp(originalUrl: url)

            switch mode {
            case .pickMedia:
                let localId = UUID().uuidString

                pickedById[localId] = tempUrl
                pickedTypeById[localId] = mediaType

                var payload: [String: Any] = [
                    "localId": localId,
                    "uri": tempUrl.absoluteString,  // file://...
                    "mediaType": mediaType,
                ]

                if let mime = mimeTypeForUrl(tempUrl) {
                    payload["mimeType"] = mime
                }
                if let size = fileSizeBytes(for: tempUrl) {
                    payload["sizeBytes"] = size
                }

                if mediaType == "image" {
                    if let dims = imageDimensions(for: tempUrl) {
                        payload["width"] = dims.w
                        payload["height"] = dims.h
                    }
                } else {
                    let meta = videoMetadata(for: tempUrl)
                    if let w = meta.w { payload["width"] = w }
                    if let h = meta.h { payload["height"] = h }
                    if let d = meta.durationMs { payload["durationMs"] = d }
                }

                call.resolve(payload)

            case .openUploader(let options):
                // Use provided uploadId if JS passed it (so UI can track immediately)
                let optUploadId = options["uploadId"] as? String
                let uploadId =
                    (optUploadId != nil && !(optUploadId!.isEmpty))
                    ? optUploadId!
                    : UUID().uuidString

                // Maintain old flow: upload immediately with generated name
                let ext = extLowercased(for: tempUrl)
                let generatedName = "\(mediaType)-\(Int(Date().timeIntervalSince1970)).\(ext)"

                if mediaType == "image" {
                    validateAndUploadImage(
                        imageUrl: tempUrl,
                        fileName: generatedName,
                        call: call,
                        uploadId: uploadId
                    )
                } else {
                    validateAndUploadVideo(
                        videoUrl: tempUrl,
                        fileName: generatedName,
                        call: call,
                        uploadId: uploadId
                    )
                }
            }
        } catch {
            call.reject(error.localizedDescription)
        }
    }
}
