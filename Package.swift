// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Cabletv102CapacitorUploadCarePlugin",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "Cabletv102CapacitorUploadCarePlugin",
            targets: ["CapUploadCarePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "CapUploadCarePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CapUploadCarePlugin"),
        .testTarget(
            name: "CapUploadCarePluginTests",
            dependencies: ["CapUploadCarePlugin"],
            path: "ios/Tests/CapUploadCarePluginTests")
    ]
)