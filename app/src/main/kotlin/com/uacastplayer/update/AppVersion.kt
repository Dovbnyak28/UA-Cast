package com.uacastplayer.update

/**
 * A version number that can be compared with another one *numerically*.
 *
 * This exists because the obvious implementation is wrong in a way that only shows up later: as
 * strings, `"0.10.0" < "0.9.0"`, because '1' sorts before '9'. An update check built on string
 * comparison works perfectly through 0.1 -> 0.9 and then silently stops offering updates the moment
 * a two-digit minor arrives - the exact point at which a project is old enough for it to matter and
 * long past the point anyone is still testing the update check.
 *
 * Two shapes have to meet here. The installed version is [com.uacastplayer.BuildConfig.VERSION_NAME],
 * which CI extends with its run number (`0.9.0.42` - see `docs/RELEASING.md`), so it can carry more
 * components than a release tag. The published version is a git tag, conventionally `v0.9.0`. Both
 * parse into the same thing: the leading `v` is dropped and missing trailing components count as
 * zero, so `0.9.0` and `0.9` and `0.9.0.0` are all equal, and a CI build `0.9.0.42` is correctly
 * *newer* than the `0.9.0` release it was built from.
 *
 * A pre-release suffix (`1.0.0-beta1`) sorts *below* the same numbers without one, as semver
 * requires - so a device on `1.0.0` is never offered `1.0.0-beta2` as an upgrade.
 */
data class AppVersion(
    val numbers: List<Int>,
    val preRelease: String?,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val width = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until width) {
            val mine = numbers.getOrElse(i) { 0 }
            val theirs = other.numbers.getOrElse(i) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return when {
            preRelease == other.preRelease -> 0
            // Absent beats present: 1.0.0 is a later release than 1.0.0-rc1.
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    companion object {
        /**
         * Parses `v1.2.3`, `1.2.3`, `1.2`, `0.9.0.42` or `1.0.0-beta1`, or returns null for
         * anything that is not a version - an empty tag, a word, `1..2`, a negative or non-numeric
         * component. Null is deliberately not "0.0.0": a tag nobody can parse must not be treated
         * as an ancient version and silently offered as an update to everyone.
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            val dash = trimmed.indexOf('-')
            val numericPart = if (dash >= 0) trimmed.substring(0, dash) else trimmed
            val rawPreRelease = if (dash >= 0) trimmed.substring(dash + 1) else null
            val components = numericPart.split('.')
            val numbers = components.mapNotNull { component ->
                // toIntOrNull accepts a leading '+'; it does not belong in a version component.
                component.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
            }
            val numericPartValid = trimmed.isNotEmpty() && numbers.size == components.size
            val preReleaseValid = rawPreRelease == null || rawPreRelease.isNotEmpty()
            return if (numericPartValid && preReleaseValid) AppVersion(numbers, rawPreRelease) else null
        }
    }
}
