import Foundation
import IOBluetooth
import AirInputAudioPipeline

/// Bluetooth Classic RFCOMM server using IOBluetooth SDP + incoming channel notifications.
final class RfcommServer: NSObject {
    private let serviceUUID: String
    private var serviceRecord: IOBluetoothSDPServiceRecord?
    private var channelNotification: IOBluetoothUserNotification?
    private var channel: IOBluetoothRFCOMMChannel?
    private var state: String = "idle"
    private var phoneName: String = ""
    private let ipcSocketPath: String
    private var ipcServerFD: Int32 = -1
    private var pipeline: OpaquePointer?
    private var lastEmitPackets: UInt64 = 0

    init(serviceUUID: String) {
        self.serviceUUID = serviceUUID.lowercased()
        self.ipcSocketPath = FileManager.default.temporaryDirectory
            .appendingPathComponent("airinput-rfcomm-\(getpid()).sock")
            .path
        super.init()
        self.pipeline = airinput_pipeline_create(nil, 0)
    }

    deinit {
        if let pipeline {
            airinput_pipeline_destroy(pipeline)
        }
    }

    func start() {
        do {
            try startUnixIpc()
        } catch {
            emit(["state": "error", "detail": "ipc failed: \(error)"])
        }

        guard let serviceClassUUID = uuidFromString(serviceUUID) else {
            state = "error"
            emit(["state": state, "detail": "invalid service UUID"])
            return
        }
        let l2capUUID = IOBluetoothSDPUUID.uuid16(0x0100)
        let rfcommUUID = IOBluetoothSDPUUID.uuid16(0x0003)
        let publicBrowseRoot = IOBluetoothSDPUUID.uuid16(0x1002)
        let serviceDict: [String: Any] = [
            "0001 - ServiceClassIDList": [serviceClassUUID],
            "0004 - ProtocolDescriptorList": [
                [l2capUUID as Any],
                [rfcommUUID as Any, NSNumber(value: 0)], // 0 = auto-assign RFCOMM channel
            ],
            "0005 - BrowseGroupList": [publicBrowseRoot],
            "0100 - ServiceName*": "AirInput Audio",
            "LocalAttributes": [
                "Persistent": false,
            ],
        ]

        guard let record = IOBluetoothSDPServiceRecord.publishedServiceRecord(with: serviceDict) else {
            state = "error"
            emit(["state": state, "detail": "publishedServiceRecordWithDictionary failed"])
            return
        }
        serviceRecord = record

        var channelID: BluetoothRFCOMMChannelID = 0
        let getChannel = record.getRFCOMMChannelID(&channelID)
        if getChannel != kIOReturnSuccess {
            state = "error"
            emit(["state": state, "detail": "getRFCOMMChannelID failed \(getChannel)"])
            return
        }

        channelNotification = IOBluetoothRFCOMMChannel.register(
            forChannelOpenNotifications: self,
            selector: #selector(rfcommChannelOpened(_:channel:)),
            withChannelID: channelID,
            direction: kIOBluetoothUserNotificationChannelDirectionIncoming
        )

        state = "listening"
        emitStatus()
        log("listening on RFCOMM channel \(channelID) uuid=\(serviceUUID)")
    }

    func stop() {
        channel?.setDelegate(nil)
        channel?.close()
        channel = nil
        channelNotification?.unregister()
        channelNotification = nil
        serviceRecord?.remove()
        serviceRecord = nil
        if let pipeline {
            airinput_pipeline_on_disconnected(pipeline)
        }
        if ipcServerFD >= 0 {
            close(ipcServerFD)
            ipcServerFD = -1
        }
        try? FileManager.default.removeItem(atPath: ipcSocketPath)
        state = "stopped"
        emitStatus()
    }

    func emitStatus() {
        var stats = AirInputPipelineStats()
        if let pipeline {
            airinput_pipeline_get_stats(pipeline, &stats)
        }
        emit([
            "state": state,
            "phoneName": phoneName,
            "packets": stats.packets,
            "audioFrames": stats.audio_frames,
            "decodeErrors": stats.decode_errors,
            "authOk": stats.auth_ok,
            "authenticated": stats.authenticated != 0,
            "ringReady": stats.ring_ready != 0,
            "ipc": ipcSocketPath,
            "uuid": serviceUUID,
        ])
    }

    @objc private func rfcommChannelOpened(
        _ notification: IOBluetoothUserNotification,
        channel: IOBluetoothRFCOMMChannel
    ) {
        self.channel = channel
        channel.setDelegate(self)
        phoneName = channel.getDevice()?.name ?? "Android"
        state = "connected"
        emitStatus()
        log("incoming RFCOMM from \(phoneName); delaying challenge until link settles")
        // Writing immediately during Android connect() often yields
        // "read failed, socket might closed" on the phone side.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { [weak self] in
            guard let self, self.channel === channel else { return }
            if let pipeline = self.pipeline {
                let ctx = Unmanaged.passUnretained(self).toOpaque()
                airinput_pipeline_on_connected(pipeline, rfcommSendThunk, ctx)
            }
            self.emitStatus()
            self.log("challenge sent to \(self.phoneName)")
        }
    }

    fileprivate func sendBytes(_ data: UnsafePointer<UInt8>, length: Int) {
        guard let channel, length > 0 else { return }
        var offset = 0
        while offset < length {
            let chunk = min(length - offset, Int(UInt16.max))
            var mutable = Array(UnsafeBufferPointer(start: data.advanced(by: offset), count: chunk))
            mutable.withUnsafeMutableBytes { raw in
                _ = channel.writeSync(raw.baseAddress, length: UInt16(raw.count))
            }
            offset += chunk
        }
    }

    private func emit(_ obj: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: obj),
              let line = String(data: data, encoding: .utf8) else { return }
        fputs(line + "\n", stdout)
        fflush(stdout)
    }

    private func log(_ message: String) {
        fputs("[AirInputRfcomm] \(message)\n", stderr)
        fflush(stderr)
    }

    private func startUnixIpc() throws {
        try? FileManager.default.removeItem(atPath: ipcSocketPath)
        let fd = socket(AF_UNIX, SOCK_STREAM, 0)
        guard fd >= 0 else { throw NSError(domain: "ipc", code: 1) }
        var addr = sockaddr_un()
        addr.sun_family = sa_family_t(AF_UNIX)
        ipcSocketPath.withCString { src in
            withUnsafeMutablePointer(to: &addr.sun_path) { dst in
                _ = dst.withMemoryRebound(to: CChar.self, capacity: 104) {
                    strncpy($0, src, 103)
                }
            }
        }
        let bindOk = withUnsafePointer(to: &addr) {
            $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.bind(fd, $0, socklen_t(MemoryLayout<sockaddr_un>.size))
            }
        }
        guard bindOk == 0, listen(fd, 1) == 0 else {
            close(fd)
            throw NSError(domain: "ipc", code: 2)
        }
        ipcServerFD = fd
        DispatchQueue.global(qos: .utility).async { [weak self] in
            self?.acceptIpcLoop(serverFD: fd)
        }
    }

    private func acceptIpcLoop(serverFD: Int32) {
        while true {
            let client = accept(serverFD, nil, nil)
            if client < 0 { break }
            close(client)
        }
    }

    private func uuidFromString(_ text: String) -> IOBluetoothSDPUUID? {
        guard let nsuuid = UUID(uuidString: text) else { return nil }
        var bytes = nsuuid.uuid
        return withUnsafeBytes(of: &bytes) { raw in
            IOBluetoothSDPUUID(bytes: raw.baseAddress, length: 16)
        }
    }
}

private func rfcommSendThunk(_ data: UnsafePointer<UInt8>?, _ len: Int, _ ctx: UnsafeMutableRawPointer?) {
    guard let data, let ctx, len > 0 else { return }
    let server = Unmanaged<RfcommServer>.fromOpaque(ctx).takeUnretainedValue()
    server.sendBytes(data, length: len)
}

extension RfcommServer: IOBluetoothRFCOMMChannelDelegate {
    func rfcommChannelData(
        _ rfcommChannel: IOBluetoothRFCOMMChannel!,
        data dataPointer: UnsafeMutableRawPointer!,
        length dataLength: Int
    ) {
        guard let dataPointer, dataLength > 0, let pipeline else { return }
        let ctx = Unmanaged.passUnretained(self).toOpaque()
        airinput_pipeline_push(pipeline, dataPointer.assumingMemoryBound(to: UInt8.self), dataLength,
                               rfcommSendThunk, ctx)

        var stats = AirInputPipelineStats()
        airinput_pipeline_get_stats(pipeline, &stats)
        if stats.packets == 1 || stats.packets - lastEmitPackets >= 25 || stats.authenticated != 0 {
            lastEmitPackets = stats.packets
            if stats.authenticated != 0 && state == "connected" {
                state = "authenticated"
            }
            if stats.audio_frames > 0 {
                state = "streaming"
            }
            emitStatus()
        }
        _ = rfcommChannel
    }

    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        _ = rfcommChannel
        if let pipeline {
            airinput_pipeline_on_disconnected(pipeline)
        }
        state = "listening"
        phoneName = ""
        emitStatus()
        log("RFCOMM closed")
    }
}
