import UIKit
import SwiftUI
import Combine

public final class KeyboardViewController: UIInputViewController {
    private var hostingController: UIHostingController<KeyboardLayoutView>?
    private var heightConstraint: NSLayoutConstraint?
    private var cancellables = Set<AnyCancellable>()

    private var desiredKeyboardHeight: CGFloat {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        if AppGroupManager.shared.isKeyboardCollapsed {
            return isPad ? 68.0 : 62.0
        }
        return isPad ? 328.0 : 286.0
    }

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupKeyboardHostingView()
        bindAppGroupStreams()
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        updateKeyboardHeightConstraint()
        AppGroupManager.shared.reloadStateFromChannels()
        AppGroupManager.shared.startFastPolling()
        processPendingActionsIfNeeded()
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        AppGroupManager.shared.stopFastPolling()
        AppGroupManager.shared.persistAndBroadcastState(postDarwinNotification: false)
    }

    public override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        updateKeyboardHeightConstraint()
    }

    // MARK: - Montage UIHostingController + Contraintes AutoLayout (320 pt iPad / 275 pt iPhone)

    private func setupKeyboardHostingView() {
        let rootView = KeyboardLayoutView(
            onInsertText: { [weak self] text in
                self?.insertText(text)
            },
            onDeleteBackward: { [weak self] in
                self?.deleteBackward()
            },
            onDeleteLastDictatedSentence: { [weak self] length in
                self?.deleteLastDictatedSentence(length: length)
            },
            onAdvanceToNextKeyboard: { [weak self] in
                self?.advanceToNextKeyboard()
            },
            contextBeforeCursorProvider: { [weak self] in
                self?.textDocumentProxy.documentContextBeforeInput ?? ""
            }
        )

        let host = UIHostingController(rootView: rootView)
        host.view.translatesAutoresizingMaskIntoConstraints = false
        host.view.backgroundColor = .clear

        addChild(host)
        view.addSubview(host.view)
        host.didMove(toParent: self)

        let height = view.heightAnchor.constraint(equalToConstant: desiredKeyboardHeight)
        height.priority = UILayoutPriority(999)
        heightConstraint = height

        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            height
        ])
    }

    private func updateKeyboardHeightConstraint(animated: Bool = false) {
        let targetHeight = desiredKeyboardHeight
        guard heightConstraint?.constant != targetHeight else { return }
        heightConstraint?.constant = targetHeight
        if animated {
            UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseInOut, .allowUserInteraction]) {
                self.view.layoutIfNeeded()
            }
        }
    }

    // MARK: - Synchronisation Temps Réel avec AppGroupManager (Insertion, Suppression & Mode Réduit)

    private func bindAppGroupStreams() {
        AppGroupManager.shared.$isKeyboardCollapsed
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateKeyboardHeightConstraint(animated: true)
            }
            .store(in: &cancellables)

        AppGroupManager.shared.$pendingTranscribedText
            .receive(on: DispatchQueue.main)
            .sink { [weak self] pending in
                guard !pending.isEmpty else { return }
                self?.processPendingActionsIfNeeded()
            }
            .store(in: &cancellables)

        AppGroupManager.shared.$shouldDeleteLastSentence
            .receive(on: DispatchQueue.main)
            .sink { [weak self] shouldDelete in
                guard shouldDelete else { return }
                self?.processPendingActionsIfNeeded()
            }
            .store(in: &cancellables)
    }

    private func processPendingActionsIfNeeded() {
        // 1. Suppression de la dernière phrase dictée si demandée (par commande vocale ou bouton)
        if let deleteCount = AppGroupManager.shared.consumeDeleteLastSentenceLength(), deleteCount > 0 {
            deleteLastDictatedSentence(length: deleteCount)
        }

        // 2. Insertion du texte fraîchement transcrit par Whisper dans Goodnotes
        if let textToInsert = AppGroupManager.shared.consumePendingTranscribedText(), !textToInsert.isEmpty {
            insertText(textToInsert)
        }
    }

    // MARK: - Callbacks vers textDocumentProxy

    public func insertText(_ text: String) {
        textDocumentProxy.insertText(text)
    }

    public func deleteBackward() {
        textDocumentProxy.deleteBackward()
    }

    public func deleteLastDictatedSentence(length: Int) {
        guard length > 0 else { return }
        for _ in 0..<length {
            textDocumentProxy.deleteBackward()
        }
    }

    public func advanceToNextKeyboard() {
        self.advanceToNextInputMode()
    }
}
