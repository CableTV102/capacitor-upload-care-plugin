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
    }

    /// Internal helper used by the plugin.
    /// Not @objc, only called from Swift (CapUploadCarePlugin).
    public func upload(
        data: Data,
        fileName: String,
        completion: @escaping (Result<[String: Any], Error>) -> Void
    ) {
        guard let uploadcare = self.uploadcare else {
            completion(.failure(UploadError.notConfigured))
            return
        }

        // Upload via Uploadcare Swift SDK
        _ = uploadcare.uploadFile(
            data,
            withName: fileName,
            store: .auto,
            metadata: nil,
            { progress in
                // progress is 0.0 ... 1.0
                print("[CapUploadCare] Upload progress: \(Int(progress * 100))%")
            },
            { result in
                switch result {
                case .failure(let error):
                    completion(.failure(error))

                case .success(let file):
                    let uuid = file.uuid

                    // Build CDN URL manually – UploadedFile has no `cdnUrl` property.
                    let cdnUrl = "https://ucarecdn.com/\(uuid)/"

                    var fileDict: [String: Any] = [
                        "uuid": uuid,
                        "cdnUrl": cdnUrl,
                    ]

                    // originalFilename is non-optional in the newer SDK
                    fileDict["filename"] = file.originalFilename

                    // size is a non-optional Int
                    fileDict["sizeBytes"] = file.size

                    // If you later want MIME type, width, height etc.,
                    // only add them if the properties actually exist on UploadedFile.

                    completion(.success(fileDict))
                }
            }
        )
    }
}
