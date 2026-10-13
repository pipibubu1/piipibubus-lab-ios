import UIKit
import WebKit
import AuthenticationServices
import PhotosUI
import Photos
import UniformTypeIdentifiers

@MainActor
final class MainViewController: UIViewController, WKNavigationDelegate, WKUIDelegate, ASWebAuthenticationPresentationContextProviding, PHPickerViewControllerDelegate {
    private var web: WKWebView!
    private let statusLabel = UILabel()
    private let cancelButton = UIButton(type: .system)
    private let statusBar = UIStackView()
    private var userAgent = ""
    private var loginTask: Task<Void, Never>?
    private var loginGeneration: UUID?
    private var authSession: ASWebAuthenticationSession?
    private var fileTask: Task<Void, Never>?
    private var fileCompletion: (@MainActor @Sendable ([URL]?) -> Void)?
    private let videos = VideoPreparation()
    private let origin = NativeLoginClient.origin

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.078, green: 0.09, blue: 0.075, alpha: 1)
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = .all
        web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self; web.uiDelegate = self
        web.isOpaque = false; web.backgroundColor = view.backgroundColor
        web.allowsBackForwardNavigationGestures = true
        statusLabel.numberOfLines = 0
        statusLabel.textColor = UIColor(red: 0.83, green: 0.93, blue: 0.45, alpha: 1)
        cancelButton.setTitle("Atcelt / Cancel", for: .normal)
        cancelButton.addTarget(self, action: #selector(cancelCurrentTask), for: .touchUpInside)
        statusBar.axis = .vertical; statusBar.spacing = 8
        statusBar.addArrangedSubview(statusLabel); statusBar.addArrangedSubview(cancelButton)
        statusBar.isHidden = true
        let stack = UIStackView(arrangedSubviews: [statusBar, web])
        stack.axis = .vertical; stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        try? VideoPreparation.prepareFolder()
        web.evaluateJavaScript("navigator.userAgent") { [weak self] value, _ in
            guard let self = self else { return }
            let base = value as? String ?? "Mozilla/5.0 (iPhone; CPU iPhone OS 18_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
            self.userAgent = base + " PIIPIBUBUSIOS/1"
            self.web.customUserAgent = self.userAgent
            self.web.load(URLRequest(url: self.origin.appendingPathComponent("account")))
        }
    }
    private func ownSite(_ url: URL?) -> Bool {
        guard let url = url else { return false }
        return url.scheme == "https" && url.host == origin.host && (url.port == nil || url.port == 443)
    }
    private func trusted(_ frame: WKFrameInfo) -> Bool { frame.isMainFrame && ownSite(frame.request.url) }
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { decisionHandler(.cancel); return }
        if action.targetFrame?.isMainFrame == false { decisionHandler(.allow); return }
        if ownSite(url) {
            if url.path == "/auth/google" { decisionHandler(.cancel); startLogin(); return }
            decisionHandler(.allow); return
        }
        decisionHandler(.cancel)
        if ["https", "mailto", "tel"].contains(url.scheme ?? "") { UIApplication.shared.open(url) }
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = action.request.url, ownSite(url) { webView.load(action.request) }
        return nil
    }
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { view.window ?? UIWindow() }
    @objc private func cancelCurrentTask() {
        loginGeneration = nil; loginTask?.cancel(); loginTask = nil
        authSession?.cancel(); authSession = nil
        fileTask?.cancel(); videos.cancel()
        statusBar.isHidden = true
    }
    private func startLogin() {
        guard loginTask == nil, fileTask == nil else { return }
        let generation = UUID(); loginGeneration = generation
        statusBar.isHidden = false; statusLabel.text = "Sāk pieslēgšanos / Starting sign-in…"
        loginTask = Task { [weak self] in
            guard let self = self else { return }
            do {
                let client = NativeLoginClient(userAgent: self.userAgent)
                let grant = try await client.start()
                try Task.checkCancellation()
                guard self.loginGeneration == generation else { return }
                self.statusLabel.text = "Pieslēgšanās kods / Sign-in code: \(grant.pairing)\nSalīdzini kodu un apstiprini pieslēgšanos. / Match and approve the code."
                let session = ASWebAuthenticationSession(url: NativeLoginClient.googleURL(grant), callbackURLScheme: "piipibubuslab") { [weak self] _, error in
                    Task { @MainActor in
                        guard let self = self, self.loginGeneration == generation else { return }
                        if error != nil { self.cancelCurrentTask() }
                    }
                }
                self.authSession = session
                session.presentationContextProvider = self
                guard session.start() else { throw AppFailure.message("Neizdevās atvērt Google pieslēgšanos. / Unable to open Google sign-in.") }
                while !Task.isCancelled && Date() < grant.deadline {
                    if try await client.ready(grant) {
                        self.loginGeneration = nil
                        self.loginTask = nil
                        self.authSession?.cancel(); self.authSession = nil
                        self.statusBar.isHidden = true
                        // The WebView, not URLSession, receives the HTTPS HttpOnly session cookies.
                        self.web.load(NativeLoginClient.exchangeRequest(grant))
                        return
                    }
                    try await Task.sleep(nanoseconds: 1_500_000_000)
                }
                try Task.checkCancellation()
                throw AppFailure.message("Pieslēgšanās laiks beidzies. Sāc vēlreiz. / Sign-in expired. Start again.")
            } catch is CancellationError { }
            catch { if self.loginGeneration == generation { self.cancelCurrentTask(); self.alert(error.localizedDescription) } }
        }
    }
    func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping @MainActor @Sendable ([URL]?) -> Void) {
        guard trusted(frame), fileCompletion == nil, loginTask == nil else { completionHandler(nil); return }
        fileCompletion = completionHandler
        webView.evaluateJavaScript("document.activeElement?.accept || ''") { [weak self] value, _ in
            guard let self = self else { return }
            let accept = value as? String ?? ""
            var config = PHPickerConfiguration(photoLibrary: .shared())
            config.selectionLimit = parameters.allowsMultipleSelection ? 8 : 1
            config.preferredAssetRepresentationMode = .compatible
            if accept.contains("image") && !accept.contains("video") { config.filter = .images }
            else if accept.contains("image") && accept.contains("video") { config.filter = .any(of: [.images, .videos]) }
            else { config.filter = .videos }
            let picker = PHPickerViewController(configuration: config)
            picker.delegate = self
            self.present(picker, animated: true)
        }
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard !results.isEmpty else { finishFiles(nil); return }
        statusBar.isHidden = false
        fileTask = Task { [weak self] in
            guard let self = self else { return }
            var prepared: [URL] = []
            do {
                for result in results {
                    try Task.checkCancellation()
                    let movie = result.itemProvider.hasItemConformingToTypeIdentifier(UTType.movie.identifier)
                    self.statusLabel.text = "Sagatavo video / Preparing selected file…"
                    let source = try await VideoPreparation.copyFromProvider(result.itemProvider, movie: movie)
                    try Task.checkCancellation()
                    if movie {
                        do {
                            let compressed = try await self.videos.compress(source) { [weak self] progress in
                                self?.statusLabel.text = "Saspiež video / Compressing video: \(Int(progress * 100))%\nAtstāj lietotni atvērtu. / Keep the app open."
                            }
                            prepared.append(compressed)
                        } catch is CancellationError { try? FileManager.default.removeItem(at: source); throw CancellationError() }
                        catch {
                            if await self.originalFallback() { prepared.append(source) }
                            else { try? FileManager.default.removeItem(at: source); throw CancellationError() }
                        }
                    } else { prepared.append(source) }
                }
                self.finishFiles(prepared)
            } catch is CancellationError { for file in prepared { try? FileManager.default.removeItem(at: file) }; self.finishFiles(nil) }
            catch { self.finishFiles(nil); self.alert(error.localizedDescription) }
        }
    }
    private func finishFiles(_ urls: [URL]?) {
        let done = fileCompletion; fileCompletion = nil
        fileTask = nil; statusBar.isHidden = true
        done?(urls)
    }
    private func originalFallback() async -> Bool {
        await withCheckedContinuation { continuation in
            let prompt = UIAlertController(title: "Video saspiešana neizdevās / Compression failed", message: "Oriģināls galerijā nav mainīts. Nosūtīt oriģinālu? / The gallery original is unchanged. Send the original?", preferredStyle: .alert)
            prompt.addAction(UIAlertAction(title: "Oriģināls / Original", style: .default) { _ in continuation.resume(returning: true) })
            prompt.addAction(UIAlertAction(title: "Atcelt / Cancel", style: .cancel) { _ in continuation.resume(returning: false) })
            present(prompt, animated: true)
        }
    }
    private func alert(_ message: String) {
        let prompt = UIAlertController(title: "PIIPIBUBUS LAB", message: message, preferredStyle: .alert)
        prompt.addAction(UIAlertAction(title: "OK", style: .default))
        present(prompt, animated: true)
    }
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        guard trusted(frame) else { completionHandler(); return }
        let prompt = UIAlertController(title: "PIIPIBUBUS LAB", message: message, preferredStyle: .alert)
        prompt.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        present(prompt, animated: true)
    }
    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        guard trusted(frame) else { completionHandler(false); return }
        let prompt = UIAlertController(title: "PIIPIBUBUS LAB", message: message, preferredStyle: .alert)
        prompt.addAction(UIAlertAction(title: "Turpināt / Continue", style: .default) { _ in completionHandler(true) })
        prompt.addAction(UIAlertAction(title: "Atcelt / Cancel", style: .cancel) { _ in completionHandler(false) })
        present(prompt, animated: true)
    }
}
