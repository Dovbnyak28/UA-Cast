package com.uacastplayer.app

import com.uacastplayer.core.security.PinHasher
import com.uacastplayer.parentalcontrol.LockedChannelsStorage
import com.uacastplayer.parentalcontrol.ParentalControlPinPolicy
import com.uacastplayer.parentalcontrol.ParentalControlPinStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the parental-control PIN lock (see [LockedChannelsStorage]/[ParentalControlPinStorage.parentalControlPinHash]).
 * Locking a channel is always allowed - it only ever narrows what's playable, nothing to protect by
 * gating it - but *removing* a lock (watching a locked channel, un-locking it permanently, changing
 * or resetting the PIN) requires [unlockedThisSession] to be true first, which only [verifyPin]
 * (correct PIN) can set. [unlockedThisSession] is deliberately in-memory only, never persisted, so
 * a fresh process always starts locked again - "until the app is closed" per the feature's design.
 */
class ParentalControlController(
    private val store: LockedChannelsStorage,
    private val preferences: ParentalControlPinStorage,
    private val scope: CoroutineScope,
    /** Where [PinHasher]'s PBKDF2 rounds run - see [verifyPin]. Injected rather than hardcoded so
     * tests stay on their own dispatcher instead of hopping to a real thread pool mid-assertion. */
    private val hashingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _lockedKeys = MutableStateFlow<Set<String>>(emptySet())
    val lockedKeys: StateFlow<Set<String>> = _lockedKeys.asStateFlow()

    private val _isPinSet = MutableStateFlow(preferences.parentalControlPinHash != null)
    val isPinSet: StateFlow<Boolean> = _isPinSet.asStateFlow()

    private val _unlockedThisSession = MutableStateFlow(false)
    val unlockedThisSession: StateFlow<Boolean> = _unlockedThisSession.asStateFlow()

    fun loadInitial() {
        scope.launch { _lockedKeys.value = store.load() }
    }

    fun isLocked(channelKey: String): Boolean = channelKey in _lockedKeys.value

    fun lockChannel(channelKey: String) {
        if (channelKey in _lockedKeys.value) return
        _lockedKeys.value = _lockedKeys.value + channelKey
        scope.launch { store.save(_lockedKeys.value) }
    }

    /** Permanently removes [channelKey] from the locked set. Callers must gate this behind
     * [unlockedThisSession] themselves (e.g. via [verifyPin]) - this function doesn't re-check it,
     * so a UI that already prompted for the PIN this session isn't forced to verify it again per
     * channel. */
    fun unlockChannelPermanently(channelKey: String) {
        if (channelKey !in _lockedKeys.value) return
        _lockedKeys.value = _lockedKeys.value - channelKey
        scope.launch { store.save(_lockedKeys.value) }
    }

    /** Verifies [pin] against the stored hash and, on success, flips [unlockedThisSession] for the
     * rest of this process's lifetime. False (with no state change) for a wrong PIN or if none is
     * set at all - the latter shouldn't happen from the UI (an unlock prompt only ever shows when
     * [isPinSet] is already true), but failing closed instead of throwing is cheap insurance.
     *
     * Suspending because [PinHasher] runs 120,000 PBKDF2 rounds - ~26ms on a desktop JVM, and
     * several times that on the low-end phones this app targets. That is a frozen frame on the tap
     * that submits the PIN, for work that has no business on the main thread. */
    suspend fun verifyPin(pin: String): Boolean {
        val hash = preferences.parentalControlPinHash
        val salt = preferences.parentalControlPinSalt
        if (hash == null || salt == null) return false
        val matches = withContext(hashingDispatcher) { PinHasher.verify(pin, salt, hash) }
        if (matches) _unlockedThisSession.value = true
        return matches
    }

    /** Sets a new PIN, replacing any existing one. Callers must gate *replacing* an existing PIN
     * behind [unlockedThisSession] themselves; setting the very first PIN needs no such check -
     * there's nothing to protect yet. False (no state change) if [pin] fails
     * [ParentalControlPinPolicy.isValidFormat]. Suspending for the same reason as [verifyPin]. */
    suspend fun setPin(pin: String): Boolean {
        if (!ParentalControlPinPolicy.isValidFormat(pin)) return false
        val salt = PinHasher.generateSalt()
        val hash = withContext(hashingDispatcher) { PinHasher.hash(pin, salt) }
        // Written together, after the hash succeeds: a salt persisted without its hash would leave
        // isPinSet false with a stale salt on disk for the next setPin to overwrite anyway, but
        // pairing the writes keeps the two fields' invariant obvious.
        preferences.parentalControlPinSalt = salt
        preferences.parentalControlPinHash = hash
        _isPinSet.value = true
        return true
    }

    /** Clears the PIN and every locked channel - the "forgot PIN" escape hatch. Deliberately not
     * gated behind [unlockedThisSession]; the only guard this gets is Settings' own confirmation
     * dialog before calling it. */
    fun resetParentalControl() {
        preferences.parentalControlPinHash = null
        preferences.parentalControlPinSalt = null
        _isPinSet.value = false
        _unlockedThisSession.value = false
        _lockedKeys.value = emptySet()
        scope.launch { store.save(emptySet()) }
    }
}
