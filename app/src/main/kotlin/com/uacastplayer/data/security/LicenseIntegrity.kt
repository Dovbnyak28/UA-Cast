package com.uacastplayer.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.uacastplayer.core.security.Hex
import com.uacastplayer.log.AppLog
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac

private const val TAG = "LicenseIntegrity"

/**
 * The tag that says a stored licence is the one this app wrote.
 *
 * The key is generated inside the Android Keystore and never leaves it - this class can ask for a
 * MAC over some bytes and can ask whether a MAC matches, and at no point does the key material
 * exist anywhere a `cat` of the app's files would find it. That is what makes editing
 * `uacast_prefs.xml` by hand stop working: the file can be changed, but not re-tagged.
 *
 * **What this is not.** It is not protection from someone who controls the process. The same root
 * that edits the file can patch the verification out of the APK, which the audit records as UAC-05
 * and which nothing on the client answers. What it removes is the cheap version - a text editor and
 * a rooted file manager - and that is the version people actually use.
 *
 * Every failure here degrades to "cannot vouch for it" rather than to "reject it". A Keystore that
 * will not generate a key, an OEM that removed HMAC, a device mid-upgrade: none of those are the
 * user's fault, and answering them by revoking a licence somebody paid for would be a far worse bug
 * than the one this exists to prevent.
 */
object LicenseIntegrity {

    private const val KEY_ALIAS = "uacast_license_hmac"
    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_HMAC_SHA256
    private const val PROVIDER = "AndroidKeyStore"

    /** A tag for [payload], or null if this device cannot produce one. */
    fun sign(payload: String): String? = macOf(payload)

    /**
     * Whether [mac] is this app's tag for [payload].
     *
     * Compared with [java.security.MessageDigest.isEqual] rather than `==`, for the same reason
     * `PinHasher` does: there is no reason to leak, through timing, how much of a guess was right.
     */
    fun verify(payload: String, mac: String): Boolean {
        val expected = macOf(payload) ?: return false
        return java.security.MessageDigest.isEqual(expected.toByteArray(), mac.toByteArray())
    }

    /** Whether this device can tag at all - used to tell "unverifiable" apart from "wrong". */
    fun isAvailable(): Boolean = macOf("probe") != null

    @Suppress("TooGenericExceptionCaught")
    private fun macOf(payload: String): String? = try {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key())
        Hex.encode(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
    } catch (e: Exception) {
        AppLog.w(TAG) { "This device cannot tag a licence: ${e.javaClass.simpleName}" }
        null
    }

    private fun key(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // First run on this install. Generated here rather than at startup so a device that never
        // stores a licence never creates a key it will not use.
        val generator = KeyGenerator.getInstance(ALGORITHM, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build(),
        )
        return generator.generateKey()
    }
}
