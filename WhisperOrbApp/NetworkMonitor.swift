import Foundation
import Combine
import Network

public final class NetworkMonitor: ObservableObject {
    public static let shared = NetworkMonitor()

    @Published public var isConnected: Bool = true
    @Published public var connectionType: String = "Local"
    @Published public var isExpensive: Bool = false

    private let monitor = NWPathMonitor()

    public init() {
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.isConnected = (path.status == .satisfied)
                self?.isExpensive = path.isExpensive
                self?.connectionType = path.usesInterfaceType(.cellular) ? "Cellulaire" : "Wi-Fi"
            }
        }
        monitor.start(queue: DispatchQueue(label: "com.bluewolfy24yt.whisperorb.network"))
    }
}
