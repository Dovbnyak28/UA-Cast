package com.uacastplayer.data.cast

import com.uacastplayer.core.cast.CastCompatibilityVerdict
import com.uacastplayer.core.cast.TsSourceKind

/** Metadata obtained from bytes already buffered for proxy routing; never a separate HTTP fetch. */
data class ProxySourceDiagnostic(
    val verdict: CastCompatibilityVerdict,
    val sourceKind: TsSourceKind,
)

/** Keeps the original producer's identity across asynchronous delivery to the Cast owner. */
class ProxySourceObservation internal constructor(
    val resourceId: String,
    val diagnostic: ProxySourceDiagnostic,
    internal val lease: RemuxRequestLease,
)
