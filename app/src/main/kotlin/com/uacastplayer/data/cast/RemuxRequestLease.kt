package com.uacastplayer.data.cast

/** Shared producer ownership for raw remux and flattened HLS. Identity, not value equality:
 * A -> B -> A and stop/start with the same token are new owners. */
internal class RemuxRequestLease(val rootId: String)
