// SPDX-License-Identifier: FSL-1.1-ALv2

import SwiftUI
import TypewrightKit

/// The whole native shell: it hosts the Compose Multiplatform app and flushes the open project
/// whenever the scene leaves the foreground. No screen is designed here (CLAUDE.md law 6).
@main
struct TypewrightIosApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ComposeHost()
                .ignoresSafeArea(.all)
        }
        .onChange(of: scenePhase) { phase in
            if phase != .active {
                MainViewControllerKt.flushWorkspace()
            }
        }
    }
}

private struct ComposeHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
