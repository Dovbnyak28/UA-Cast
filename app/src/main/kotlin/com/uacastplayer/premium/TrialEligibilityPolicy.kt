package com.uacastplayer.premium

/**
 * Whether a device that holds no license should be given the first-launch trial, and what the
 * clock is allowed to say.
 *
 * Both rules exist because the trial is the only thing this app gives away on trust, and both of
 * the ways to take it twice need no tooling, no root and no skill.
 */
object TrialEligibilityPolicy {

    /**
     * Whether an install with nothing stored is a genuinely new one.
     *
     * "Nothing stored" was the whole test, and Android hands the user a button that produces
     * exactly that: Settings -> Apps -> Storage -> Clear data empties SharedPreferences on a stock,
     * unrooted phone. The next launch was a first launch, and the fortnight started again, for as
     * many fortnights as anyone cared to spend three taps on.
     *
     * [firstInstallTimeMillis] is what closes it, because it is the one fact about an install that
     * clearing data does not touch - `PackageManager` keeps it, and only a real uninstall resets
     * it. So the trial is still *granted* from storage and still *ends* on a stored date; this only
     * refuses to hand out a second one to an install that is already older than the trial it is
     * asking for.
     *
     * Deliberately not the reverse - the trial is not computed from the install time. A stored end
     * date is a fact; "installed 14 days ago" becomes wrong the moment the clock is touched, which
     * is the very next rule down.
     */
    fun deservesTrial(firstInstallTimeMillis: Long?, nowMillis: Long): Boolean {
        // Null is "this platform did not tell us", and a clock behind the install date says nothing
        // trustworthy about age either. Both resolve towards granting: refusing would deny a real
        // first launch to someone whose phone has the wrong date, which costs a user everything the
        // app is trying to show them, while granting costs at most one extra fortnight.
        if (firstInstallTimeMillis == null) return true
        return nowMillis - firstInstallTimeMillis < License.TRIAL_DURATION_MILLIS
    }

    /**
     * The clock to judge an expiry against: never earlier than the latest one already seen.
     *
     * Every expiry decision in this app is `now < expiresAt` against the system clock, and the
     * system clock is a setting. Turning off automatic date and time and moving the date back a
     * month made a lapsed trial current again, and a lapsed subscription too for as long as the
     * device stayed away from the store.
     *
     * A high-water mark is the whole defence and it is deliberately a small one: it catches the
     * naive case - which is the case people actually try - and it cannot be defeated without
     * editing storage, which is a different attack with a different cost (see the audit's UAC-06).
     * Moving the clock *forward* is not resisted at all: that only ends an entitlement early, which
     * costs the user rather than the app, and is exactly what someone crossing a date line does.
     */
    fun clampToHighWaterMark(nowMillis: Long, highWaterMarkMillis: Long): Long =
        maxOf(nowMillis, highWaterMarkMillis)
}
