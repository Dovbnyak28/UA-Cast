package com.uacastplayer.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpFaultTest {

    /** Verbatim from a Samsung UE40KU6000 refusing Play during a channel switch - the response that
     * produced this class. */
    private val samsungTransitionFault = """
        <?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><s:Fault>
        <faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>
        <UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>701</errorCode>
        <errorDescription>Transition not available</errorDescription></UPnPError>
        </detail></s:Fault></s:Body></s:Envelope>
    """.trimIndent()

    @Test
    fun `a transition fault is recognized as the retryable one`() {
        val fault = UpnpFault.parse(samsungTransitionFault)
        assertEquals("701", fault.code)
        assertEquals("Transition not available", fault.description)
        assertTrue(fault.isTransitionNotAvailable)
    }

    /**
     * 716 is what the same TV answers when it cannot fetch the url it was handed, and it must not
     * be retried: unlike 701 nothing about it resolves on its own, so retrying would only delay
     * falling back by the whole retry budget.
     */
    @Test
    fun `a resource-not-found fault is not retryable`() {
        val fault = UpnpFault.parse("<errorCode>716</errorCode><errorDescription>Resource not found</errorDescription>")
        assertEquals("716", fault.code)
        assertFalse(fault.isTransitionNotAvailable)
    }

    @Test
    fun `a body with no fault detail yields nulls rather than throwing`() {
        val fault = UpnpFault.parse("<html><body>500 Internal Server Error</body></html>")
        assertNull(fault.code)
        assertNull(fault.description)
        assertFalse(fault.isTransitionNotAvailable)
        assertEquals("no UPnP fault detail", fault.toString())
    }

    /** A renderer that closed the connection mid-body must degrade to "no detail", not throw out of
     * the error path that is already handling a failure. */
    @Test
    fun `an unterminated tag is treated as absent`() {
        val fault = UpnpFault.parse("<errorCode>701")
        assertNull(fault.code)
        assertFalse(fault.isTransitionNotAvailable)
    }

    @Test
    fun `an empty body is safe`() {
        assertEquals(UpnpFault(null, null), UpnpFault.parse(""))
    }

    @Test
    fun `the log form carries both halves when present`() {
        assertEquals(
            "errorCode=701 \"Transition not available\"",
            UpnpFault.parse(samsungTransitionFault).toString(),
        )
    }
}
