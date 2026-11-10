import Foundation

let args = CommandLine.arguments
var uuid = "8d53dc1d-1db7-4cd3-868b-8a527460aa84"
if let idx = args.firstIndex(of: "--uuid"), args.index(after: idx) < args.count {
    uuid = args[args.index(after: idx)]
}

let server = RfcommServer(serviceUUID: uuid)
server.start()

// Control plane: JSON lines on stdin.
DispatchQueue.global(qos: .userInitiated).async {
    while let line = readLine(strippingNewline: true) {
        guard let data = line.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let cmd = obj["cmd"] as? String else {
            continue
        }
        if cmd == "stop" {
            server.stop()
            exit(0)
        }
        if cmd == "status" {
            server.emitStatus()
        }
    }
}

RunLoop.main.run()
