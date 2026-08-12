package com.uacastplayer.log

/**
 * Lets a note through only when it differs from the one before it.
 *
 * For a line written on a hot path whose answer rarely changes. [LogBuffer] holds 500 entries and
 * is the one record that survives the device's own log ring rolling over - but a line repeated once
 * a second empties it of everything else within minutes. A report from a real device made the point
 * exactly: 20 entries for 4 minutes 19 seconds of use, and 16 of them were the same sentence about
 * the same channel having no artwork.
 *
 * Deliberately only one note deep, not a set of everything seen. A verdict that alternates is a
 * change and should be recorded each time it changes; the case being cut here is the same answer
 * arriving over and over, which is silence dressed up as information.
 *
 * The race between the read and the write is benign and left unguarded: two threads passing the
 * same note at once log it twice, which costs one duplicate line - cheaper than a lock on a path
 * that runs while a channel is switching.
 */
class RepeatedNoteFilter {

    @Volatile
    private var last: String? = null

    fun isWorthLogging(note: String): Boolean {
        if (note == last) return false
        last = note
        return true
    }
}
