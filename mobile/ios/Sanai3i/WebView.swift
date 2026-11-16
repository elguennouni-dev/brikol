import SwiftUI
import WebKit

/// Thin shell around the Sanaï3i web app served by the Spring Boot backend.
/// - the web app stays inside the web view
/// - tel:, WhatsApp, mailto: and every other website open in the matching iOS app
struct WebView: UIViewRepresentable {
    @Binding var offline: Bool
    var reloadToken: Int

    static var baseURL: URL {
        let raw = Bundle.main.object(forInfoDictionaryKey: "BaseURL") as? String ?? ""
        return URL(string: raw) ?? URL(string: "http://localhost:8080/")!
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()          // keeps the login token and language (localStorage)
        config.applicationNameForUserAgent = "Mobile/15E148 Sanai3iApp/iOS"   // the web app knows it runs in the app
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.scrollView.bounces = false
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0.957, green: 0.965, blue: 0.949, alpha: 1)
        web.load(URLRequest(url: Self.baseURL))
        context.coordinator.lastReloadToken = reloadToken
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        if context.coordinator.lastReloadToken != reloadToken {
            context.coordinator.lastReloadToken = reloadToken
            web.load(URLRequest(url: Self.baseURL))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var parent: WebView
        var lastReloadToken = 0

        init(_ parent: WebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }
            let base = WebView.baseURL
            let isWeb = url.scheme == "http" || url.scheme == "https"
            let sameSite = isWeb && url.host == base.host && url.port == base.port
            // Same site, in the same frame: stay in the app. Anything else (tel:, wa.me, target=_blank): open outside.
            if sameSite && navigationAction.targetFrame != nil {
                decisionHandler(.allow)
                return
            }
            if url.scheme == "about" || url.scheme == "blob" || url.scheme == "data" {
                decisionHandler(.allow)
                return
            }
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
            decisionHandler(.cancel)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async { self.parent.offline = false }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async { self.parent.offline = true }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async { self.parent.offline = true }
        }
    }
}
