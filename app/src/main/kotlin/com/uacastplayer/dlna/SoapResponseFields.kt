package com.uacastplayer.dlna

import com.uacastplayer.core.concurrent.runCatchingNonFatal
import java.io.StringReader
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/** Bounded flat SOAP values: qualified tags, attributes, CDATA and predefined/numeric entities. */
internal object SoapResponseFields {
    private val declaration = Regex("^\\s*<\\?xml\\s[^?]*\\?>")

    fun read(body: String, vararg names: String): Map<String, String> {
        if (body.length > MAX_SOAP_RESPONSE_BYTES || body.contains("<!DOCTYPE", ignoreCase = true)) return emptyMap()
        return runCatchingNonFatal {
            val handler = SoapFieldsHandler(names.toSet())
            // Preserve legacy fragment replies, including several sibling fields without an envelope.
            val fragment = declaration.replaceFirst(body, "")
            UpnpXmlReader.create(handler).parse(InputSource(StringReader("<response>$fragment</response>")))
            handler.values
        }.getOrDefault(emptyMap())
    }
}

private const val MAX_SOAP_FIELD_DEPTH = 64
private const val MAX_SOAP_FIELD_CHARS = 512

private class SoapFieldsHandler(private val names: Set<String>) : DefaultHandler() {
    val values = mutableMapOf<String, String>()
    private var depth = 0
    private var target: String? = null
    private var targetDepth = 0
    private val text = StringBuilder()
    private var invalidText = false

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        depth++
        if (depth > MAX_SOAP_FIELD_DEPTH) throw SAXException("SOAP nesting limit")
        if (target != null) {
            invalidText = true
        } else {
            val name = qName.substringAfter(':')
            if (name in names && name !in values) {
                target = name
                targetDepth = depth
                text.setLength(0)
                invalidText = false
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (target == null || invalidText) return
        if (text.length + length > MAX_SOAP_FIELD_CHARS) invalidText = true
        else text.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        if (depth == targetDepth && target != null) {
            if (!invalidText) {
                val value = text.toString().trim()
                if (value.isNotEmpty()) values[checkNotNull(target)] = value
            }
            target = null
        }
        depth--
    }

    override fun error(error: SAXParseException) { throw error }
    override fun fatalError(error: SAXParseException) { throw error }
}
