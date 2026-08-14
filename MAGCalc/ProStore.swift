import Foundation
import Observation
import StoreKit

@MainActor
@Observable
final class ProStore {
    static let productID = "de.kamilunavo.magcalc.pro"

    var isPro = false
    var product: Product?
    var isLoading = false
    var errorMessage: String?

    func refresh() async {
        isLoading = true
        defer { isLoading = false }

        do {
            product = try await Product.products(for: [Self.productID]).first
            await refreshEntitlement()
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func purchase() async {
        guard let product else {
            await refresh()
            guard self.product != nil else {
                errorMessage = String(localized: "store.unavailable")
                return
            }
            await purchase()
            return
        }

        isLoading = true
        defer { isLoading = false }

        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                guard case .verified(let transaction) = verification else {
                    errorMessage = String(localized: "store.verificationFailed")
                    return
                }
                await transaction.finish()
                await refreshEntitlement()
                errorMessage = nil
            case .pending:
                errorMessage = String(localized: "store.pending")
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func restore() async {
        isLoading = true
        defer { isLoading = false }

        do {
            try await AppStore.sync()
            await refreshEntitlement()
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func refreshEntitlement() async {
        var unlocked = false

        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else { continue }
            if transaction.productID == Self.productID && transaction.revocationDate == nil {
                unlocked = true
                break
            }
        }

        isPro = unlocked
    }

    static var preview: ProStore {
        let store = ProStore()
        store.isPro = true
        return store
    }
}
