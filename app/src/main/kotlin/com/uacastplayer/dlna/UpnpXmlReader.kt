package com.uacastplayer.dlna

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler

/** Shared parser hardening; vendor field names are interpreted by the caller, not this factory. */
internal object UpnpXmlReader {
    fun create(handler: DefaultHandler): XMLReader {
        val factory = SAXParserFactory.newInstance()
        // Keep qName mode: some legacy replies use prefixes without declaring their namespaces.
        runCatchingNonFatal { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        val reader = factory.newSAXParser().xmlReader
        optionalFeature(reader, "http://apache.org/xml/features/disallow-doctype-decl", true)
        optionalFeature(reader, "http://xml.org/sax/features/external-general-entities", false)
        optionalFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false)
        reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
        reader.contentHandler = handler
        reader.errorHandler = handler
        return reader
    }

    private fun optionalFeature(reader: XMLReader, name: String, value: Boolean) {
        // Expat and Xerces do not support the same feature set. Never swallow JVM-fatal errors.
        runCatchingNonFatal { reader.setFeature(name, value) }
    }
}
