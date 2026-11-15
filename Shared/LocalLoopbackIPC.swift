import Foundation
import Darwin

// MARK: - Protocole IPC Local Loopback (127.0.0.1) Universel Sideloading (Zéro App Group Requis)

public struct LoopbackIPCRequest: Codable {
    public enum Action: String, Codable {
        case getState
        case pushKeyboardSettings
        case consumePendingText
        case consumeDeleteSentence
        case clearLastSentenceLength
        case toggleRecording
        case getStyleMemory
    }

    public var action: Action
    public var clientPayload: WhisperSharedStatePayload?
    public var knownStyleMemoryUpdatedAt: TimeInterval?

    public init(
        action: Action,
        clientPayload: WhisperSharedStatePayload? = nil,
        knownStyleMemoryUpdatedAt: TimeInterval? = nil
    ) {
        self.action = action
        self.clientPayload = clientPayload
        self.knownStyleMemoryUpdatedAt = knownStyleMemoryUpdatedAt
    }
}

public struct LoopbackIPCResponse: Codable {
    public var ok: Bool
    public var payload: WhisperSharedStatePayload?
    public var consumedText: String?
    public var consumedDeleteLength: Int?
    public var styleMemoryUpdatedAt: TimeInterval?
    public var styleMemoryData: Data?

    public init(
        ok: Bool,
        payload: WhisperSharedStatePayload? = nil,
        consumedText: String? = nil,
        consumedDeleteLength: Int? = nil,
        styleMemoryUpdatedAt: TimeInterval? = nil,
        styleMemoryData: Data? = nil
    ) {
        self.ok = ok
        self.payload = payload
        self.consumedText = consumedText
        self.consumedDeleteLength = consumedDeleteLength
        self.styleMemoryUpdatedAt = styleMemoryUpdatedAt
        self.styleMemoryData = styleMemoryData
    }
}

/// Pont TCP local (`127.0.0.1`) ultra-léger (< 0,3 ms) permettant à l'application hôte `WhisperOrbApp`
/// et à l'extension clavier `WhisperKeyboard` (avec Accès Complet) de communiquer en temps réel sur iPadOS,
/// même lorsque l'IPA est installé via Sideloadly / AltStore / SideStore / Feather / ESign sans App Group ni Keychain partagé.
public final class LocalLoopbackIPC {
    public static let shared = LocalLoopbackIPC()

    public static let candidatePorts: [UInt16] = [49872, 49873, 49874]
    private static let maxFrameBytes: Int = 512 * 1024

    private let stateLock = NSLock()
    private var serverFD: Int32 = -1
    private var boundPort: UInt16 = 0
    private var lastWorkingClientPort: UInt16 = 49872
    private var isAcceptLoopActive: Bool = false

    private let acceptQueue = DispatchQueue(label: "com.bluewolfy24yt.whisperorb.loopback.accept", qos: .userInteractive)
    private let handlerQueue = DispatchQueue(label: "com.bluewolfy24yt.whisperorb.loopback.handler", qos: .userInteractive)

    public var requestHandler: ((LoopbackIPCRequest) -> LoopbackIPCResponse)?

    private var isExtensionProcess: Bool {
        Bundle.main.bundlePath.hasSuffix(".appex")
    }

    private init() {}

    // MARK: - Serveur Local (Exécuté uniquement dans WhisperOrbApp)

    public func ensureServerRunning() {
        guard !isExtensionProcess else { return }

        stateLock.lock()
        if serverFD >= 0 && isAcceptLoopActive {
            stateLock.unlock()
            return
        }
        if serverFD >= 0 {
            Darwin.close(serverFD)
            serverFD = -1
        }

        var chosenFD: Int32 = -1
        var chosenPort: UInt16 = 0

        for port in Self.candidatePorts {
            let fd = Darwin.socket(AF_INET, SOCK_STREAM, 0)
            guard fd >= 0 else { continue }

            var reuse: Int32 = 1
            _ = Darwin.setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &reuse, socklen_t(MemoryLayout<Int32>.size))
            var noSigPipe: Int32 = 1
            _ = Darwin.setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &noSigPipe, socklen_t(MemoryLayout<Int32>.size))

            var addr = sockaddr_in()
            addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = port.bigEndian
            addr.sin_addr = in_addr(s_addr: UInt32(0x7F000001).bigEndian)

            let bindRC = withUnsafePointer(to: &addr) { ptr in
                ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                    Darwin.bind(fd, sockPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
                }
            }

            if bindRC == 0 && Darwin.listen(fd, 16) == 0 {
                chosenFD = fd
                chosenPort = port
                break
            }
            Darwin.close(fd)
        }

        guard chosenFD >= 0 else {
            stateLock.unlock()
            return
        }

        serverFD = chosenFD
        boundPort = chosenPort
        isAcceptLoopActive = true
        stateLock.unlock()

        let activeListenFD = chosenFD
        acceptQueue.async { [weak self] in
            self?.runAcceptLoop(listenFD: activeListenFD)
        }
    }

    private func runAcceptLoop(listenFD: Int32) {
        while true {
            var clientAddr = sockaddr_in()
            var clientLen = socklen_t(MemoryLayout<sockaddr_in>.size)
            let clientFD = withUnsafeMutablePointer(to: &clientAddr) { ptr in
                ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                    Darwin.accept(listenFD, sockPtr, &clientLen)
                }
            }

            if clientFD < 0 {
                let err = errno
                if err == EINTR {
                    continue
                }
                stateLock.lock()
                if serverFD == listenFD {
                    Darwin.close(serverFD)
                    serverFD = -1
                    boundPort = 0
                    isAcceptLoopActive = false
                }
                stateLock.unlock()
                break
            }

            handlerQueue.async { [weak self] in
                self?.handleClientSocket(clientFD)
            }
        }
    }

    private func handleClientSocket(_ clientFD: Int32) {
        defer { Darwin.close(clientFD) }

        var noSigPipe: Int32 = 1
        _ = Darwin.setsockopt(clientFD, SOL_SOCKET, SO_NOSIGPIPE, &noSigPipe, socklen_t(MemoryLayout<Int32>.size))
        Self.setNonBlocking(clientFD)

        guard let requestData = Self.readFrame(from: clientFD, timeoutMs: 120),
              let request = try? JSONDecoder().decode(LoopbackIPCRequest.self, from: requestData) else {
            return
        }

        let response: LoopbackIPCResponse
        if let handler = requestHandler {
            response = handler(request)
        } else {
            response = LoopbackIPCResponse(ok: false)
        }

        guard let responseData = try? JSONEncoder().encode(response) else { return }
        _ = Self.writeFrame(responseData, to: clientFD, timeoutMs: 120)
    }

    // MARK: - Client Local (Exécuté depuis WhisperKeyboard)

    public func sendRequest(_ request: LoopbackIPCRequest) -> LoopbackIPCResponse? {
        guard let requestData = try? JSONEncoder().encode(request) else { return nil }

        stateLock.lock()
        let preferred = lastWorkingClientPort
        stateLock.unlock()

        var orderedPorts = [preferred]
        for p in Self.candidatePorts where p != preferred {
            orderedPorts.append(p)
        }

        for port in orderedPorts {
            guard let fd = Self.connectLoopback(port: port, timeoutMs: 55) else {
                continue
            }
            defer { Darwin.close(fd) }

            guard Self.writeFrame(requestData, to: fd, timeoutMs: 90),
                  let responseData = Self.readFrame(from: fd, timeoutMs: 110),
                  let decoded = try? JSONDecoder().decode(LoopbackIPCResponse.self, from: responseData) else {
                continue
            }

            stateLock.lock()
            lastWorkingClientPort = port
            stateLock.unlock()
            return decoded
        }

        return nil
    }

    // MARK: - Helpers Socket Non-Bloquants avec Timeout Strict

    private static func setNonBlocking(_ fd: Int32) {
        let flags = Darwin.fcntl(fd, F_GETFL, 0)
        if flags >= 0 {
            _ = Darwin.fcntl(fd, F_SETFL, flags | O_NONBLOCK)
        }
    }

    private static func connectLoopback(port: UInt16, timeoutMs: Int32) -> Int32? {
        let fd = Darwin.socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { return nil }

        var noSigPipe: Int32 = 1
        _ = Darwin.setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &noSigPipe, socklen_t(MemoryLayout<Int32>.size))
        setNonBlocking(fd)

        var addr = sockaddr_in()
        addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = port.bigEndian
        addr.sin_addr = in_addr(s_addr: UInt32(0x7F000001).bigEndian)

        let rc = withUnsafePointer(to: &addr) { ptr in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockPtr in
                Darwin.connect(fd, sockPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }

        if rc == 0 {
            return fd
        }

        if errno != EINPROGRESS {
            Darwin.close(fd)
            return nil
        }

        var pfd = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
        let pollRC = Darwin.poll(&pfd, 1, timeoutMs)
        guard pollRC > 0, (pfd.revents & Int16(POLLERR | POLLHUP)) == 0 else {
            Darwin.close(fd)
            return nil
        }

        var sockErr: Int32 = 0
        var errLen = socklen_t(MemoryLayout<Int32>.size)
        if Darwin.getsockopt(fd, SOL_SOCKET, SO_ERROR, &sockErr, &errLen) != 0 || sockErr != 0 {
            Darwin.close(fd)
            return nil
        }

        return fd
    }

    private static func writeFrame(_ data: Data, to fd: Int32, timeoutMs: Int32) -> Bool {
        guard data.count <= maxFrameBytes else { return false }
        var lengthPrefix = UInt32(data.count).bigEndian
        var packet = Data(bytes: &lengthPrefix, count: 4)
        packet.append(data)

        return packet.withUnsafeBytes { rawBuf -> Bool in
            guard let base = rawBuf.baseAddress else { return false }
            var totalSent = 0
            let totalCount = rawBuf.count

            while totalSent < totalCount {
                var pfd = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
                let ready = Darwin.poll(&pfd, 1, timeoutMs)
                if ready <= 0 || (pfd.revents & Int16(POLLERR | POLLHUP)) != 0 {
                    return false
                }
                let sent = Darwin.send(fd, base.advanced(by: totalSent), totalCount - totalSent, 0)
                if sent > 0 {
                    totalSent += sent
                } else if sent < 0 && (errno == EINTR || errno == EAGAIN) {
                    continue
                } else {
                    return false
                }
            }
            return true
        }
    }

    private static func readExactBytes(count: Int, from fd: Int32, timeoutMs: Int32) -> Data? {
        guard count > 0, count <= maxFrameBytes else { return count == 0 ? Data() : nil }
        var buffer = Data(count: count)
        let ok = buffer.withUnsafeMutableBytes { rawBuf -> Bool in
            guard let base = rawBuf.baseAddress else { return false }
            var totalRead = 0
            while totalRead < count {
                var pfd = pollfd(fd: fd, events: Int16(POLLIN), revents: 0)
                let ready = Darwin.poll(&pfd, 1, timeoutMs)
                if ready <= 0 {
                    return false
                }
                let n = Darwin.recv(fd, base.advanced(by: totalRead), count - totalRead, 0)
                if n > 0 {
                    totalRead += n
                } else if n < 0 && (errno == EINTR || errno == EAGAIN) {
                    continue
                } else {
                    return false
                }
            }
            return true
        }
        return ok ? buffer : nil
    }

    private static func readFrame(from fd: Int32, timeoutMs: Int32) -> Data? {
        guard let header = readExactBytes(count: 4, from: fd, timeoutMs: timeoutMs), header.count == 4 else {
            return nil
        }
        let bytes = [UInt8](header)
        let frameLength = (Int(bytes[0]) << 24) | (Int(bytes[1]) << 16) | (Int(bytes[2]) << 8) | Int(bytes[3])
        guard frameLength >= 0, frameLength <= maxFrameBytes else { return nil }
        if frameLength == 0 { return Data() }
        return readExactBytes(count: frameLength, from: fd, timeoutMs: timeoutMs)
    }
}
