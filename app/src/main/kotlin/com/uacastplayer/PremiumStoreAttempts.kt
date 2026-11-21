package com.uacastplayer

import com.uacastplayer.premium.billing.PurchaseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The UI-facing state of store attempts (purchase / restore) - moved out of [AppViewModel]
 * unchanged; the store calls themselves stay with [com.uacastplayer.data.premium.PremiumRepository]. */
internal class PremiumStoreAttempts(private val scope: CoroutineScope) {
    private val _lastPurchaseOutcome = MutableStateFlow<PurchaseResult?>(null)

    /**
     * How the last purchase or restore ended, until a screen has shown it.
     *
     * Kept rather than discarded because there is nowhere else for it to go: a purchase is answered
     * by Play through a client-wide listener, long after the tap, and possibly after the sheet that
     * started it has been dismissed. Without this, a declined card and a successful one are the same
     * event from the user's side - nothing happens - and the only move left is to tap buy again.
     *
     * [PurchaseResult.Success] is deliberately not held: the entitlement flow has already changed by
     * then, so the screen redraws itself as unlocked, which says it better than a message would.
     */
    val lastPurchaseOutcome: StateFlow<PurchaseResult?> = _lastPurchaseOutcome.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)

    /**
     * Whether an attempt is with the store right now, so the buy controls can be disabled.
     *
     * A guard rather than a spinner, and it guards something real: Play answers a purchase through
     * a client-wide listener rather than through the call that started it, so a second attempt
     * launched before the first is answered leaves the first waiting for a reply now addressed to
     * the second. It also covers the seconds while Play's own sheet is opening, which on a slow
     * phone is long enough to press again and which the screen has nothing to say about otherwise.
     */
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    /**
     * One attempt at a time, and the flag is cleared in a `finally`: a store call that throws would
     * otherwise leave every buy button disabled for the rest of the session, which is a worse
     * outcome than the double tap this prevents.
     */
    fun startStoreAttempt(attempt: suspend () -> PurchaseResult) {
        if (_isPurchasing.value) return
        _lastPurchaseOutcome.value = null
        _isPurchasing.value = true
        scope.launch {
            try {
                report(attempt())
            } finally {
                _isPurchasing.value = false
            }
        }
    }

    /**
     * Two outcomes are deliberately not reported.
     *
     * Cancelling is a decision: the user closed Play's sheet and knows exactly what happened, and an
     * app that answers that with a message is scolding them for not buying. Success needs no message
     * either - the entitlement flow has already moved, so every lock on screen has just opened,
     * which is a better answer than a line of text saying it did.
     */
    private fun report(result: PurchaseResult) {
        _lastPurchaseOutcome.value = result.takeUnless {
            it is PurchaseResult.Cancelled || it is PurchaseResult.Success
        }
    }
}
