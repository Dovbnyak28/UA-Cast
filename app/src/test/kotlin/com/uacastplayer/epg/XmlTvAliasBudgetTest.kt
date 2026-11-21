package com.uacastplayer.epg

import com.uacastplayer.testsupport.JvmAllocations
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlTvAliasBudgetTest {
    @Test fun `repeated aliases do not copy the growing list`() {
        val xml = "<tv><channel id=\"one\">" + "<display-name>Name</display-name>".repeat(32_000) + "</channel></tv>"
        val before = JvmAllocations.currentThreadBytes()
        val result = XmlTvParser.parse(xml.byteInputStream())
        val allocated = JvmAllocations.currentThreadBytes() - before
        assertEquals(listOf("Name"), result.channels.single().displayNames)
        assertFalse(result.aliasLimitExceeded)
        if (before >= 0) assertTrue("aliases allocated $allocated bytes", allocated < 64L * 1024 * 1024)
    }

    @Test fun `distinct aliases are capped and incomplete metadata reaches UI state`() {
        val xml = "<tv><channel id=\"one\">" + (0..100).joinToString("") {
            "<display-name>Канал $it</display-name>"
        } + "</channel></tv>"
        val result = XmlTvParser.parse(xml.byteInputStream())
        assertEquals(XmlTvChannelNames.MAX_PER_CHANNEL, result.channels.single().displayNames.size)
        assertTrue(result.aliasLimitExceeded)
        assertTrue(EpgDataBuilder.build(result).truncation.any)
    }

    @Test fun `document character budget survives channel resets`() {
        val names = XmlTvChannelNames()
        repeat(XmlTvChannelNames.MAX_TOTAL_CHARS / XmlTvParser.MAX_TEXT_LENGTH + 1) {
            names.beginChannel()
            names.add("a".repeat(XmlTvParser.MAX_TEXT_LENGTH))
        }
        assertTrue(names.limited)
        assertTrue(names.finishChannel().isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun `index construction cooperates with cancellation`() {
        EpgIndex(listOf(EpgChannel("one", listOf("Один"), null))) { throw CancellationException() }
    }
}
