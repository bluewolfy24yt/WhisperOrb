import Foundation
import Network
import Combine

public final class NetworkMonitor: ObservableObject {
    public static let shared = NetworkMonitor()

    @Published public var isConnected: Bool = false
    @Published public var connectionType: String = "Hors-Ligne"

    private let monitor: NWPathMonitor
    private let monitorQueue = DispatchQueue(label: "com.bluewolfy24yt.whisperorb.networkmonitor", qos: .utility)

    public init() {
        self.monitor = NWPathMonitor()
        startMonitoring()
    }

    deinit {
        monitor.cancel()
    }

    public func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            let connected = (path.status == .satisfied)
            let typeDescription: String

            if !connected {
                typeDescription = "Hors-Ligne"
            } else if path.usesInterfaceType(.wifi) {
                typeDescription = "Wi-Fi"
            } else if path.usesInterfaceType(.cellular) {
                typeDescription = "Cellulaire"
            } else if path.usesInterfaceType(.wiredEthernet) {
                typeDescription = "Ethernet"
            } else {
                typeDescription = "Réseau Actif"
            }

            DispatchQueue.main.async {
                self?.isConnected = connected
                self?.connectionType = typeDescription
            }
        }
        monitor.start(queue: monitorQueue)
    }

    public var bannerText: String {
        if !isConnected {
            return "🔒 MODE EXAMEN HORS-LIGNE ACTIF — Aucune connexion Internet détectée (100 % Local)"
        } else {
            return "📶 Connecté en \(connectionType) (Téléchargement des modèles actif) — Passez en Mode Avion pendant vos DST"
        }
    }
}
