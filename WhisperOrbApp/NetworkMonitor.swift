import Foundation
import Combine

public final class NetworkMonitor: ObservableObject {
    public static let shared = NetworkMonitor()

    @Published public var isConnected: Bool = true
    @Published public var connectionType: String = "Local"

    public init() {}
}
