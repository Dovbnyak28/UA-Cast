package com.uacastplayer.core.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Production coroutine dispatchers used as injectable constructor/function defaults.
 *
 * Keeping the platform binding here means every I/O owner can accept a test dispatcher without
 * forcing ordinary call sites to know how the production pool is created.
 */
object AppDispatchers {
    @Suppress("InjectDispatcher")
    val io: CoroutineDispatcher = Dispatchers.IO
}
