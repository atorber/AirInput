// swift-tools-version: 5.9
import PackageDescription

let opusInclude = "/opt/homebrew/opt/opus/include"
let opusLib = "/opt/homebrew/opt/opus/lib"

let package = Package(
    name: "AirInputRfcommServer",
    platforms: [
        .macOS(.v13),
    ],
    products: [
        .executable(name: "AirInputRfcommServer", targets: ["AirInputRfcommServer"]),
    ],
    targets: [
        .target(
            name: "AirInputAudioPipeline",
            path: "Native",
            exclude: [],
            publicHeadersPath: "include",
            cxxSettings: [
                .headerSearchPath("."),
                .headerSearchPath("include"),
                .unsafeFlags([
                    "-I\(opusInclude)/opus",
                    "-I\(opusInclude)",
                    "-std=c++17",
                ]),
            ],
            linkerSettings: [
                .linkedFramework("Security"),
                .unsafeFlags([
                    "-L\(opusLib)",
                    "-lopus",
                    "-lc++",
                ]),
            ]
        ),
        .executableTarget(
            name: "AirInputRfcommServer",
            dependencies: ["AirInputAudioPipeline"],
            path: "Sources",
            linkerSettings: [
                .linkedFramework("IOBluetooth"),
                .linkedFramework("Foundation"),
            ]
        ),
    ],
    cxxLanguageStandard: .cxx17
)
