import UIKit
import SwiftUI
import Combine

public final class KeyboardViewController: UIInputViewController {
    public static let textDidChangeNotification = Notification.Name("com.bluewolfy24yt.whisperorb.keyboardTextDidChange")

    private var hostingController: UIHostingController<KeyboardLayoutView>?
    private var heightConstraint: NSLayoutConstraint?
    private var cancellables = Set<AnyCancellable>()
    private var isKeyboardVisible = false
    private var lastDictatedText = ""
    private var lastKnownWidth: CGFloat = 0

    private var currentFieldTraits: KeyboardFieldTraits {
        let proxy = textDocumentProxy
        return KeyboardFieldTraits(
            keyboardType: proxy.keyboardType ?? .default,
            returnKeyType: proxy.returnKeyType ?? .default,
            allowsAutocorrection: proxy.autocorrectionType != UITextAutocorrectionType.no,
            allowsAutocapitalization: proxy.autocapitalizationType != UITextAutocapitalizationType.none
        )
    }

    private var desiredKeyboardHeight: CGFloat {
        // La largeur réelle compte : sur iPad, le clavier peut être plein écran (≥ 500 pt),
        // en Slide Over (~320-375 pt) ou en mode Clavier Flottant iPadOS (< 450 pt, plafonné à ~254 pt par le système).
        let width = view.bounds.width
        let isPadIdiom = UIDevice.current.userInterfaceIdiom == .pad
        let isWidePad = isPadIdiom && (width == 0 || width >= 500)
        let isFloatingPad = isPadIdiom && width > 0 && width < 450
        if AppGroupManager.shared.isKeyboardCollapsed {
            if isFloatingPad { return 56.0 }
            return isWidePad ? 68.0 : 62.0
        }
        if isFloatingPad {
            return 248.0
        }
        return isWidePad ? 328.0 : 286.0
    }

    public override func viewDidLoad() {
        super.viewDidLoad()
        setupKeyboardHostingView()
        bindAppGroupStreams()
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        isKeyboardVisible = true
        updateKeyboardHeightConstraint()
        AppGroupManager.shared.reloadStateFromChannels()
        AppGroupManager.shared.startFastPolling()
        processPendingActionsIfNeeded()
        NotificationCenter.default.post(name: Self.textDidChangeNotification, object: nil)
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        isKeyboardVisible = false
        AppGroupManager.shared.stopFastPolling()
    }

    public override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        NotificationCenter.default.post(name: Self.textDidChangeNotification, object: nil)
    }

    public override func selectionDidChange(_ textInput: UITextInput?) {
        super.selectionDidChange(textInput)
        NotificationCenter.default.post(name: Self.textDidChangeNotification, object: nil)
    }

    public override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let currentWidth = view.bounds.width
        if abs(currentWidth - lastKnownWidth) > 0.5 {
            lastKnownWidth = currentWidth
            updateKeyboardHeightConstraint()
        }
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
            onOpenParentApp: { [weak self] in
                self?.openParentApp()
            },
            onDismissKeyboard: { [weak self] in
                self?.dismissKeyboard()
            },
            contextBeforeCursorProvider: { [weak self] in
                self?.textDocumentProxy.documentContextBeforeInput ?? ""
            },
            fieldTraitsProvider: { [weak self] in
                self?.currentFieldTraits ?? KeyboardFieldTraits()
            },
            hasFullAccessProvider: { [weak self] in
                self?.hasFullAccess ?? false
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
        // Clavier masqué : le texte attend son retour au lieu de partir dans le vide
        guard isKeyboardVisible || view.window != nil else { return }

        // 1. Suppression de la dernière phrase dictée si demandée (par commande vocale ou bouton)
        if let deleteCount = AppGroupManager.shared.consumeDeleteLastSentenceLength(), deleteCount > 0 {
            deleteLastDictatedSentence(length: deleteCount)
        }

        // 2. Insertion du texte fraîchement transcrit par Whisper
        if let rawTextToInsert = AppGroupManager.shared.consumePendingTranscribedText(), !rawTextToInsert.isEmpty {
            let contextBefore = textDocumentProxy.documentContextBeforeInput ?? ""
            let needsLeadingSpace: Bool = {
                guard let lastChar = contextBefore.last, let firstNew = rawTextToInsert.first else { return false }
                if lastChar.isWhitespace || lastChar.isNewline { return false }
                if ["'", "’", "(", "[", "{", "«", "/", "-"].contains(String(lastChar)) { return false }
                return firstNew.isLetter || firstNew.isNumber
            }()
            let finalInsert = needsLeadingSpace ? (" " + rawTextToInsert) : rawTextToInsert
            textDocumentProxy.insertText(finalInsert)
            lastDictatedText = finalInsert
            if needsLeadingSpace {
                AppGroupManager.shared.lastInsertedSentenceLength = finalInsert.count
            }
            NotificationCenter.default.post(name: Self.textDidChangeNotification, object: finalInsert)
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
        // Le curseur ou le champ a changé : la phrase dictée n'est plus juste avant le curseur
        let context = textDocumentProxy.documentContextBeforeInput ?? ""
        let isStillBeforeCursor = !lastDictatedText.isEmpty && !context.isEmpty
            && (context.hasSuffix(lastDictatedText) || lastDictatedText.hasSuffix(context))
        if isStillBeforeCursor {
            for _ in 0..<length {
                textDocumentProxy.deleteBackward()
            }
        }
        lastDictatedText = ""
        AppGroupManager.shared.clearLastInsertedSentenceLength()
        NotificationCenter.default.post(name: Self.textDidChangeNotification, object: nil)
    }

    public func advanceToNextKeyboard() {
        self.advanceToNextInputMode()
    }

    public func openParentApp() {
        guard let url = URL(string: "whisperorb://activate-mic") else { return }
        // iOS 18+ : `openURL:` est devenu un no-op. Il faut appeler `open(_:options:completionHandler:)`
        // sur l'objet UIApplication trouvé dans la chaîne des responders (sans UIApplication.shared,
        // interdit en extension). `openURL:` reste le secours pour iOS 16–17.
        let modernSelector = sel_registerName("openURL:options:completionHandler:")
        let legacySelector = sel_registerName("openURL:")
        var responder: UIResponder? = self
        while let current = responder {
            if let application = current as? UIApplication {
                if application.responds(to: modernSelector),
                   let method = class_getInstanceMethod(type(of: application), modernSelector) {
                    // Appel dynamique : `UIApplication.open(_:options:completionHandler:)` est marqué
                    // indisponible pour les extensions au niveau du compilateur.
                    typealias OpenURLImp = @convention(c) (
                        AnyObject, Selector, NSURL, NSDictionary, (@convention(block) (Bool) -> Void)?
                    ) -> Void
                    let imp = unsafeBitCast(method_getImplementation(method), to: OpenURLImp.self)
                    imp(application, modernSelector, url as NSURL, NSDictionary(), nil)
                    return
                }
                if application.responds(to: legacySelector) {
                    _ = application.perform(legacySelector, with: url)
                    return
                }
            } else if current.responds(to: legacySelector) {
                _ = current.perform(legacySelector, with: url)
                return
            }
            responder = current.next
        }
        extensionContext?.open(url, completionHandler: nil)
    }
}
