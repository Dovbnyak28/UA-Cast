package com.uacastplayer.diagnostics

/**
 * Where a diagnostics report goes when the user chooses to send one, and what the message says
 * before they do.
 *
 * **Nothing here sends anything.** It describes an email the user's own mail app is handed,
 * pre-addressed and pre-filled, which they then read and send themselves - the same bargain the
 * rest of the app makes. The app opens no connection, talks to no server, and needs no permission
 * to do it, which is also why "collects diagnostic data" is not a claim it has to make: the app
 * does not collect anything, the user forwards something they have already been shown in full.
 *
 * The address is a constant in the APK and therefore public - anyone who decompiles a release has
 * it. That is why it is a dedicated address rather than a personal one: it will eventually receive
 * whatever a published address receives, and the only way to change it is another release.
 */
object DiagnosticsEmail {

    const val RECIPIENT = "dovbnyak@hotmail.com"

    /**
     * The subject line, built so an inbox sorts itself.
     *
     * Version and device are already inside the report, but a subject that carries them means a
     * pile of reports can be read as a list - "three of these are the same Android 11 phone on
     * 0.9.0" - without opening any of them. Sorting a mailbox by hand is the tax for leaving them
     * out, and it is paid every time rather than once.
     */
    fun subject(appVersionName: String, deviceModel: String): String =
        "UA Cast $appVersionName - $deviceModel"
}
