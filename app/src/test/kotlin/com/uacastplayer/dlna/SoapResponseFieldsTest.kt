package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoapResponseFieldsTest {
    @Test fun siblingFieldsAndLegacyUnboundPrefixesRemainSupported() {
        assertEquals(mapOf("errorCode" to "701", "errorDescription" to "Transition & wait"),
            SoapResponseFields.read("<u:errorCode>701</u:errorCode>" +
                "<errorDescription>Transition &amp; wait</errorDescription>", "errorCode", "errorDescription"))
    }

    @Test fun validXmlTextAndQuotedAttributesDoNotChangeTheValue() {
        assertEquals(mapOf("CurrentVolume" to "17"), SoapResponseFields.read(
            "<?xml version='1.0'?><CurrentVolume note='>'><![CDATA[1]]>&#55;</CurrentVolume>", "CurrentVolume"))
    }

    @Test fun doctypeAndExternalEntitiesAreNeverResolved() {
        val body = """<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///does-not-exist/uacast-test">]>
            <CurrentVolume>&secret;</CurrentVolume>"""
        assertTrue(SoapResponseFields.read(body, "CurrentVolume").isEmpty())
    }

    @Test fun oversizedAndDeepBodiesFailClosed() {
        assertTrue(SoapResponseFields.read(" ".repeat(MAX_SOAP_RESPONSE_BYTES.toInt() + 1), "CurrentVolume").isEmpty())
        val deep = "<x>".repeat(65) + "<CurrentVolume>17</CurrentVolume>" + "</x>".repeat(65)
        assertTrue(SoapResponseFields.read(deep, "CurrentVolume").isEmpty())
    }

    @Test fun nestedMarkupAndOversizedValuesCannotBecomeAPlainField() {
        assertTrue(SoapResponseFields.read("<CurrentVolume><x>17</x></CurrentVolume>", "CurrentVolume").isEmpty())
        assertTrue(SoapResponseFields.read("<errorCode>${"7".repeat(513)}</errorCode>", "errorCode").isEmpty())
    }

    @Test fun aTruncatedBodyNeverPublishesPartiallyParsedFields() {
        assertTrue(SoapResponseFields.read("<errorCode>701</errorCode><detail>", "errorCode").isEmpty())
    }
}
