import AVFoundation
import Foundation
import Uploadcare

@objc public class CapUploadCare: NSObject {
    private var uploadcare: Uploadcare?

    @objc public func configure(
        publicKey: String,
        secretKey: String? = nil,
        debug: Bool = false
    ) {
        if let secret = secretKey, !secret.isEmpty {
            self.uploadcare = Uploadcare(withPublicKey: publicKey, secretKey: secret)
        } else {
            self.uploadcare = Uploadcare(withPublicKey: publicKey)
        }

        if debug {
            print("[CapUploadCare] Configured Uploadcare with publicKey: \(publicKey)")
        }
    }

    enum UploadError: Error {
        case notConfigured
        case invalidFileUrl
    }

    public func upload(
        data: Data,
        fileName: String,
        onProgress: @escaping (Double) -> Void,
        completion: @escaping (Result<[String: Any], Error>) -> Void
    ) {
        guard let uploadcare = self.uploadcare else {
            completion(.failure(UploadError.notConfigured))
            return
        }

        _ = uploadcare.uploadFile(
            data,
            withName: fileName,
            store: .auto,
            metadata: nil,
            { progress in
                onProgress(progress)
            },
            { result in
                switch result {
                case .failure(let error):
                    completion(.failure(error))

                case .success(let file):
                    let uuid = file.uuid
                    let cdnUrl = "https://ucarecdn.com/\(uuid)/"

                    var fileDict: [String: Any] = [
                        "uuid": uuid,
                        "cdnUrl": cdnUrl,
                    ]

                    fileDict["filename"] = file.originalFilename
                    fileDict["sizeBytes"] = file.size

                    completion(.success(fileDict))
                }
            }
        )
    }

    public func upload(
        fileUrl: URL,
        fileName: String,
        onProgress: @escaping (Double) -> Void,
        completion: @escaping (Result<[String: Any], Error>) -> Void
    ) {
        guard let uploadcare = self.uploadcare else {
            completion(.failure(UploadError.notConfigured))
            return
        }

        guard fileUrl.isFileURL else {
            completion(.failure(UploadError.invalidFileUrl))
            return
        }

        guard let fileForUploading = uploadcare.file(withContentsOf: fileUrl) else {
            completion(.failure(UploadError.invalidFileUrl))
            return
        }

        _ = fileForUploading.upload(
            withName: fileName,
            store: .auto,
            metadata: nil,
            { progress in
                onProgress(progress)
            },
            { result in
                switch result {
                case .failure(let error):
                    completion(.failure(error))

                case .success(let file):
                    let uuid = file.uuid
                    let cdnUrl = "https://ucarecdn.com/\(uuid)/"

                    var fileDict: [String: Any] = [
                        "uuid": uuid,
                        "cdnUrl": cdnUrl,
                    ]

                    fileDict["filename"] = file.originalFilename
                    fileDict["sizeBytes"] = file.size

                    completion(.success(fileDict))
                }
            }
        )
    }
}
