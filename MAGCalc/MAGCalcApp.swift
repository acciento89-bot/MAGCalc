import SwiftUI

@main
struct MAGCalcApp: App {
    @State private var proStore = ProStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(proStore)
                .preferredColorScheme(.dark)
                .task {
                    await proStore.refresh()
                }
        }
    }
}
