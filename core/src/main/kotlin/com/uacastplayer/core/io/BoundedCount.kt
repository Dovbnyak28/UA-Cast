package com.uacastplayer.core.io

import java.io.DataInputStream
import java.io.IOException

/**
 * How much of a count is worth trusting before the entries it counts have been read.
 *
 * Past this the list grows instead, which costs a handful of array copies on a file that turns out
 * to be honest and nothing at all on one that does not.
 */
private const val PRESIZE_LIMIT = 4096

/**
 * A length field off disk, refused when it is one this app could not have written.
 *
 * Every hand-rolled binary format in this app - the playlist snapshot, the saved sources, the EPG
 * snapshot - writes its collections as a bare `int` followed by that many records, and every
 * decoder read that int and handed it straight to `ArrayList(capacity)`, which allocates its
 * backing array immediately. A negative count throws `IllegalArgumentException`; a large one throws
 * `OutOfMemoryError`. Each of those decoders guards itself with `catch (EOFException)` and
 * `catch (IOException)` - which covers a file that is too short, and neither of these - and each
 * one's caller documents that a snapshot which does not decode must mean "load it again from the
 * source" rather than take anything down with it. One damaged byte in a count field was enough to
 * break that, on restores that run at startup, on phones whose own reports show a 256MB heap.
 *
 * [IOException] rather than a null return, so those existing catches stay the single exit: a file
 * that does not decode is refused the same way whatever is wrong with it.
 *
 * [limit] is for formats with a real ceiling - the parser that produced the file would not emit
 * more. Formats without one pass nothing and get the sense check alone: a merely huge count then
 * runs out of stream within a couple of reads, and that is an `EOFException`, which is already
 * handled. Inventing a ceiling where the domain has none would risk permanently refusing a file
 * that is perfectly good, and a refusal that survives the refetch is worse than the crash.
 */
@Throws(IOException::class)
fun DataInputStream.readCountField(limit: Int = Int.MAX_VALUE): Int {
    val count = readInt()
    if (count < 0 || count > limit) {
        throw IOException("count field claims $count entries where at most $limit is possible")
    }
    return count
}

/** How many entries to size a collection for, given a [count] nothing has yet corroborated. */
fun presizeFor(count: Int): Int = minOf(count, PRESIZE_LIMIT)
