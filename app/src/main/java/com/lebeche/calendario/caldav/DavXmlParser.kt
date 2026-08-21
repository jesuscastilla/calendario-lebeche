package com.lebeche.calendario.caldav

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Una respuesta `<D:response>` de un multistatus WebDAV/CalDAV. */
data class DavResponse(
    var href: String? = null,
    var status: String? = null,
    val props: MutableMap<String, String> = mutableMapOf()
)

/** Resultado de parsear un `<D:multistatus>`. */
data class Multistatus(
    val responses: List<DavResponse>,
    val syncToken: String?
)

/**
 * Parser minimalista de respuestas WebDAV/CalDAV (PROPFIND y REPORT).
 * Clasifica las propiedades por su nombre local, ignorando el namespace.
 */
object DavXmlParser {

    fun parse(xml: String?): Multistatus {
        val responses = mutableListOf<DavResponse>()
        if (xml.isNullOrBlank()) return Multistatus(responses, null)

        var topSyncToken: String? = null
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val stack = mutableListOf<String>()
        var current: DavResponse? = null
        var inResponse = false
        var textBuf = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.substringAfter(':') ?: ""
                    stack.add(name)
                    if (name == "response") {
                        current = DavResponse()
                        inResponse = true
                    }
                    textBuf = StringBuilder()
                }

                XmlPullParser.TEXT -> textBuf.append(parser.text)

                XmlPullParser.END_TAG -> {
                    val name = parser.name?.substringAfter(':') ?: ""
                    val text = textBuf.toString()

                    if (inResponse && current != null) {
                        val path = stack.joinToString("/")
                        when {
                            path == "response/href" -> current.href = text
                            path == "response/status" -> current.status = text
                            path.endsWith("/prop/displayname") -> current.props["displayname"] = text
                            path.endsWith("/prop/calendar-color") -> current.props["calendar-color"] = text
                            path.endsWith("/prop/getctag") -> current.props["getctag"] = text
                            path.endsWith("/prop/sync-token") -> current.props["sync-token"] = text
                            path.endsWith("/prop/getetag") -> current.props["getetag"] = text
                            path.endsWith("/prop/calendar-data") -> current.props["calendar-data"] = text
                            path.endsWith("/prop/current-user-principal/href") -> current.props["current-user-principal"] = text
                            path.endsWith("/prop/calendar-home-set/href") -> current.props["calendar-home-set"] = text
                            path.endsWith("/prop/resourcetype/collection") -> current.props["collection"] = "1"
                            path.endsWith("/prop/resourcetype/calendar") -> current.props["calendar"] = "1"
                        }
                    } else if (!inResponse && name == "sync-token") {
                        topSyncToken = text
                    }

                    stack.removeAt(stack.size - 1)
                    if (name == "response") {
                        current?.let { responses.add(it) }
                        current = null
                        inResponse = false
                    }
                    textBuf = StringBuilder()
                }
            }
            eventType = parser.next()
        }

        return Multistatus(responses, topSyncToken)
    }
}
