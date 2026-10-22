package ovh.battistella.sillon.sonos

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** A UPnP service on a Sonos speaker: its control path and its type. */
enum class SonosService(val path: String, val type: String) {
    AV_TRANSPORT("/MediaRenderer/AVTransport/Control", "urn:schemas-upnp-org:service:AVTransport:1"),
    GROUP_RENDERING("/MediaRenderer/GroupRenderingControl/Control", "urn:schemas-upnp-org:service:GroupRenderingControl:1"),
    ZONE_GROUP_TOPOLOGY("/ZoneGroupTopology/Control", "urn:schemas-upnp-org:service:ZoneGroupTopology:1"),
}

/** One SOAP action, ready to post. Arguments keep their order: Sonos reads them in sequence. */
data class SoapCall(
    val service: SonosService,
    val action: String,
    val args: List<Pair<String, String>> = emptyList(),
) {
    val soapAction: String get() = "\"${service.type}#$action\""

    fun body(): String = Soap.envelope(service.type, action, args)
}

/** A UPnP error from the speaker (`errorCode` in the SOAP fault), or an HTTP error without one. */
class SonosException(val code: Int?, message: String) : Exception(message)

object Soap {
    fun envelope(serviceType: String, action: String, args: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:").append(action).append(" xmlns:u=\"").append(serviceType).append("\">")
        args.forEach { (name, value) -> append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>') }
        append("</u:").append(action).append("></s:Body></s:Envelope>")
    }

    fun escape(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    /** The output arguments of an action response, by name, unescaped once. */
    fun values(xml: String): Map<String, String> {
        val body = parseXml(xml).childElements().firstOrNull { it.localName() == "Body" } ?: return emptyMap()
        val response = body.childElements().firstOrNull() ?: return emptyMap()
        if (response.localName() == "Fault") return emptyMap()
        return response.childElements().associate { it.localName() to it.textContent.orEmpty() }
    }

    /** The UPnP `errorCode` of a SOAP fault, or null when [xml] is not one. */
    fun faultCode(xml: String): Int? = runCatching {
        parseXml(xml).descendants().firstOrNull { it.localName() == "errorCode" }?.textContent?.trim()?.toIntOrNull()
    }.getOrNull()
}

internal fun parseXml(xml: String): Element {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    // Speakers never send a DTD; refuse one rather than resolve it (Android's parser ignores these flags).
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { factory.isExpandEntityReferences = false }
    return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
}

internal fun Element.localName(): String = tagName.substringAfter(':')

internal fun Element.childElements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

internal fun Element.descendants(): Sequence<Element> = sequence {
    val stack = ArrayDeque<Element>().apply { add(this@descendants) }
    while (stack.isNotEmpty()) {
        val next = stack.removeFirst()
        yield(next)
        val nodes = next.childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { stack.add(it) }
    }
}

internal fun Element.attr(name: String): String? = getAttribute(name).takeIf { hasAttribute(name) }
