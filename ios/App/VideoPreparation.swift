import UIKit
import AVFoundation
import CoreMedia
import UniformTypeIdentifiers

@MainActor
final class VideoPreparation {
    private var export: AVAssetExportSession?
    private var output: URL?
    private var progressTimer: Timer?
    static var folder: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("prepared-media", isDirectory: true)
    }
    static func prepareFolder() throws {
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let files = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        for url in files {
            let date = try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate
            if let date = date, Date().timeIntervalSince(date) > 86400 { try? FileManager.default.removeItem(at: url) }
        }
    }
    // Copy inside the provider callback: its temporary URL is not valid afterward.
    nonisolated static func copyFromProvider(_ provider: NSItemProvider, movie: Bool) async throws -> URL {
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("prepared-media", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return try await withCheckedThrowingContinuation { continuation in
            provider.loadFileRepresentation(forTypeIdentifier: movie ? UTType.movie.identifier : UTType.image.identifier) { source, error in
                do {
                    guard let source = source else { throw error ?? AppFailure.message("Video nav pieejams. / Selected file is unavailable.") }
                    let suffix = source.pathExtension.isEmpty ? (movie ? "mov" : "jpg") : source.pathExtension
                    let destination = directory.appendingPathComponent(UUID().uuidString).appendingPathExtension(suffix)
                    try FileManager.default.copyItem(at: source, to: destination)
                    continuation.resume(returning: destination)
                } catch { continuation.resume(throwing: error) }
            }
        }
    }
    func cancel() {
        export?.cancelExport()
        progressTimer?.invalidate()
        progressTimer = nil
    }
    func compress(_ source: URL, progress: @escaping (Float) -> Void) async throws -> URL {
        try Task.checkCancellation()
        let asset = AVURLAsset(url: source)
        guard let session = AVAssetExportSession(asset: asset, presetName: AVAssetExportPreset1920x1080), session.supportedFileTypes.contains(.mp4) else {
            throw AppFailure.message("Šo video nevar saspiest. / This video cannot be compressed.")
        }
        let destination = Self.folder.appendingPathComponent("piipibubus-" + UUID().uuidString).appendingPathExtension("mp4")
        export = session
        output = destination
        session.outputURL = destination
        session.outputFileType = .mp4
        session.shouldOptimizeForNetworkUse = true
        UIApplication.shared.isIdleTimerDisabled = true
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: true) { _ in
            Task { @MainActor in progress(session.progress) }
        }
        defer {
            progressTimer?.invalidate(); progressTimer = nil
            export = nil; output = nil
            UIApplication.shared.isIdleTimerDisabled = false
        }
        do {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                session.exportAsynchronously {
                    switch session.status {
                    case .completed: continuation.resume()
                    case .cancelled: continuation.resume(throwing: CancellationError())
                    default: continuation.resume(throwing: session.error ?? AppFailure.message("Video saspiešana neizdevās. / Video compression failed."))
                    }
                }
            }
            try Task.checkCancellation()
            let outputSize = try destination.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard outputSize > 0 else { throw AppFailure.message("Video fails ir tukšs. / Empty exported video.") }
            let sourceSize = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            let tracks = try await asset.loadTracks(withMediaType: .video)
            var h264 = false
            if let track = tracks.first {
                let descriptions = try await track.load(.formatDescriptions)
                h264 = descriptions.first.map { CMFormatDescriptionGetMediaSubType($0) == kCMVideoCodecType_H264 } ?? false
            }
            let naturalSize = try await tracks.first?.load(.naturalSize)
            let transform = try await tracks.first?.load(.preferredTransform)
            var withinTarget = false
            if let naturalSize = naturalSize, let transform = transform {
                let displayed = naturalSize.applying(transform)
                withinTarget = max(abs(displayed.width), abs(displayed.height)) <= 1920 && min(abs(displayed.width), abs(displayed.height)) <= 1080
            }
            if source.pathExtension.lowercased() == "mp4", h264, withinTarget, sourceSize > 0, sourceSize <= outputSize {
                try? FileManager.default.removeItem(at: destination)
                return source
            }
            try? FileManager.default.removeItem(at: source) // This is our private copy, never the Photos original.
            return destination
        } catch {
            try? FileManager.default.removeItem(at: destination)
            throw error
        }
    }
}

