import SwiftUI

@main
struct SanaiApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var offline = false
    @State private var reloadToken = 0

    var body: some View {
        ZStack {
            Color(red: 0.957, green: 0.965, blue: 0.949).ignoresSafeArea()
            // The web app handles the safe areas itself (viewport-fit=cover and env(safe-area-inset-*)).
            WebView(offline: $offline, reloadToken: reloadToken)
                .ignoresSafeArea()
            if offline {
                VStack(spacing: 16) {
                    Text("Pas de connexion\nما كاينش الإنترنت")
                        .multilineTextAlignment(.center)
                        .font(.headline)
                    Button("Réessayer / عاود") {
                        offline = false
                        reloadToken += 1
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(red: 0.055, green: 0.169, blue: 0.133))
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(red: 0.957, green: 0.965, blue: 0.949))
            }
        }
    }
}
