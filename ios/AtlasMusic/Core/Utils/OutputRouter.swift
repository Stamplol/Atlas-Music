// Atlas iOS — Active audio output route enumeration and human-readable labels.
import AVFoundation
import Foundation

/// Mirrors Android `OutputDevice` with a stable String id (AVAudioSession UID).
struct AudioOutputDevice: Identifiable {
    let id: String
    let name: String
    let kind: OutputKind
}

/// Faithful port of Android `AudioOutput` using `AVAudioSession.currentRoute.outputs`.
enum OutputRouter {
    static func listOutputs() -> [AudioOutputDevice] {
        AVAudioSession.sharedInstance().currentRoute.outputs.map { desc in
            let kind = kind(of: desc.portType)
            return AudioOutputDevice(id: desc.uid, name: name(of: desc, kind: kind), kind: kind)
        }
        .sorted { order($0.kind) < order($1.kind) }
    }

    /// Preferred (non-speaker, non-earpiece) route label, mirroring Android's `currentLabel`.
    static func currentLabel() -> String {
        let outputs = listOutputs()
        let preferred = outputs.first { $0.kind != .speaker && $0.kind != .earpiece }
        return (preferred ?? outputs.first)?.name ?? "Speaker"
    }

    private static func kind(of port: AVAudioSession.Port) -> OutputKind {
        if port == .builtInSpeaker { return .speaker }
        if port == .headphones || port == .headsetMic || port == .lineOut {
            return .wired
        }
        if port == .bluetoothA2DP || port == .bluetoothHFP || port == .bluetoothLE {
            return .bluetooth
        }
        if port == .usbAudio { return .usbDAC }
        if port == .HDMI || port == .displayPort { return .hdmi }
        if port == .builtInReceiver { return .earpiece }
        return .other
    }

    private static func name(of desc: AVAudioSessionPortDescription, kind: OutputKind) -> String {
        let product = desc.portName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !product.isEmpty, product.lowercased() != "unknown", !isTechnical(product) {
            return product
        }
        switch kind {
        case .speaker: return "Phone speaker"
        case .wired: return "Wired headset"
        case .bluetooth: return "Bluetooth audio"
        case .usbDAC: return "USB DAC / audio"
        case .hdmi: return "HDMI audio"
        case .earpiece: return "Earpiece"
        case .other: return "Audio output"
        }
    }

    private static func isTechnical(_ name: String) -> Bool {
        let lower = name.lowercased()
        return lower.contains("sdk_") || lower.contains("gphone") || lower.contains("goldfish") ||
            lower.contains("ranchu") || lower.contains("vemu") || lower.contains("emu") ||
            lower.contains("generic") || lower.contains("_")
    }

    /// Matches Kotlin `OutputKind.ordinal` ordering (SPEAKER, WIRED, BLUETOOTH, USB_DAC, HDMI, EARPIECE, OTHER).
    private static func order(_ kind: OutputKind) -> Int {
        switch kind {
        case .speaker: return 0
        case .wired: return 1
        case .bluetooth: return 2
        case .usbDAC: return 3
        case .hdmi: return 4
        case .earpiece: return 5
        case .other: return 6
        }
    }
}
