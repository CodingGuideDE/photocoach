import SwiftUI
import shared

/// Haengt die Compose-Multiplatform-UI (MainViewController() aus shared/ui/MainViewController.kt)
/// als UIViewController in SwiftUI ein. Das ist die Standard-Bruecke fuer Compose-Multiplatform-
/// Apps auf iOS - alle Screens kommen aus dem shared-Modul, Swift bleibt ein duenner Host
/// (analog zu androidApp/MainActivity.kt auf Android-Seite).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

#Preview {
    ContentView()
}
