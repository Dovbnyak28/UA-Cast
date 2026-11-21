package com.uacastplayer.data.premium

import com.uacastplayer.premium.billing.PurchaseRecord
import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.CompletableDeferred

/** Play callbacks have no request id. An unresolved timed-out flow must never be replaced by B. */
internal class PendingPurchaseCoordinator {
    class Attempt(val productId: String, val result: CompletableDeferred<PurchaseResult> = CompletableDeferred())
    private var pending: Attempt? = null

    @Synchronized fun begin(productId: String): Attempt? {
        if (pending != null) return null
        return Attempt(productId).also { pending = it }
    }

    @Synchronized fun complete(records: List<PurchaseRecord>, outcome: (PurchaseRecord?) -> PurchaseResult) {
        val attempt = pending ?: return
        val matching = records.firstOrNull { it.productId == attempt.productId }
        // An out-of-band purchase/duplicate success for another product updates entitlement,
        // but is not the outcome of the currently displayed purchase sheet.
        if (records.isEmpty() || matching != null) {
            pending = null
            attempt.result.complete(outcome(matching))
        }
    }

    @Synchronized fun launchFailed(attempt: Attempt) {
        if (pending === attempt) pending = null
    }
}
