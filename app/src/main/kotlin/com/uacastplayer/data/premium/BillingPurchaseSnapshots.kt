package com.uacastplayer.data.premium

import com.uacastplayer.premium.billing.PremiumProducts
import com.uacastplayer.premium.billing.PurchaseRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transport readiness is not an ownership answer. Publish only complete, non-superseded queries. */
internal class BillingPurchaseSnapshots {
    private val snapshot = MutableStateFlow<Set<PurchaseRecord>?>(null)
    val state = snapshot.asStateFlow()
    private val queryMutex = Mutex()
    private var purchaseRevision = 0L

    @Synchronized fun notePurchases(records: List<PurchaseRecord>) {
        if (records.isEmpty()) return
        purchaseRevision++
        // An incremental callback cannot turn Unknown into an authoritative full snapshot.
        snapshot.value = snapshot.value?.plus(records)
    }

    suspend fun refresh(query: suspend (String) -> List<PurchaseRecord>?): Set<PurchaseRecord>? = queryMutex.withLock {
        val revision = beginQuery()
        val subscriptions = query(PremiumProducts.TYPE_SUBSCRIPTION) ?: return@withLock null
        val oneTime = query(PremiumProducts.TYPE_ONE_TIME) ?: return@withLock null
        publishIfCurrent(revision, (subscriptions + oneTime).toSet())
    }

    @Synchronized private fun beginQuery(): Long {
        snapshot.value = null
        return purchaseRevision
    }

    @Synchronized private fun publishIfCurrent(revision: Long, owned: Set<PurchaseRecord>): Set<PurchaseRecord>? {
        if (revision != purchaseRevision) return null
        snapshot.value = owned
        return owned
    }
}
