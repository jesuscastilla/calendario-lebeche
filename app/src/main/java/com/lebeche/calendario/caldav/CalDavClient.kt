package com.lebeche.calendario.caldav

import com.lebeche.calendario.data.Account
import com.lebeche.calendario.data.CalInfo
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Evento remoto devuelto por un REPORT (con su iCalendar en crudo). */
data class RemoteEvent(val href: String, val etag: String?, val icalData: String?)

/** Resultado de un pull (sincronización de cambios desde el servidor). */
data class PullResult(
    val events: List<RemoteEvent>,
    val deletedHrefs: List<String>,
    val newSyncToken: String?
)

/** Resultado de un PUT (creación/actualización de evento). */
data class PutResult(val href: String?, val etag: String?)

private class HttpResult(val code: Int, val body: String?, val etag: String?)

/**
 * Cliente CalDAV sobre OkHttp. Implementa descubrimiento (PROPFIND),
 * sincronización (REPORT sync-collection / calendar-query) y escritura (PUT/DELETE).
 */
class CalDavClient {

    // ------------------------------------------------------------------ descubrimiento

    fun discover(account: Account): List<CalInfo> {
        var base = normalize(account.baseUrl)
        tryWellKnown(account, base)?.let { base = it }

        val principal = discoverPrincipal(account, base) ?: base
        val home = discoverHome(account, principal) ?: principal

        val calendars = listCalendars(account, home)
        if (calendars.isNotEmpty()) return calendars

        probeCalendar(account, base)?.let { return listOf(it) }
        return emptyList()
    }

    private fun discoverPrincipal(account: Account, base: String): String? {
        val res = execute(
            account, "PROPFIND", base,
            propfindBody(listOf("current-user-principal")),
            mapOf("Depth" to "0")
        ) ?: return null
        if (res.code !in 200..299) return null
        val ms = DavXmlParser.parse(res.body)
        for (r in ms.responses) {
            val href = r.props["current-user-principal"] ?: continue
            return resolve(base, href)
        }
        return null
    }

    private fun discoverHome(account: Account, principal: String): String? {
        val res = execute(
            account, "PROPFIND", principal,
            propfindBody(listOf("calendar-home-set")),
            mapOf("Depth" to "0")
        ) ?: return null
        if (res.code !in 200..299) return null
        val ms = DavXmlParser.parse(res.body)
        for (r in ms.responses) {
            val href = r.props["calendar-home-set"] ?: continue
            return resolve(principal, href)
        }
        return null
    }

    private fun listCalendars(account: Account, home: String): List<CalInfo> {
        val res = execute(
            account, "PROPFIND", home,
            propfindBody(listOf("resourcetype", "displayname", "calendar-color", "getctag", "sync-token")),
            mapOf("Depth" to "1")
        ) ?: return emptyList()
        if (res.code !in 200..299) return emptyList()
        val ms = DavXmlParser.parse(res.body)
        val result = mutableListOf<CalInfo>()
        for (r in ms.responses) {
            if (r.props["calendar"] != "1") continue
            val href = r.href ?: continue
            val name = r.props["displayname"]?.takeIf { it.isNotBlank() }
                ?: href.substringAfterLast('/').takeIf { it.isNotBlank() }
                ?: href
            result.add(
                CalInfo(
                    accountId = account.id,
                    href = resolve(home, href),
                    displayName = name,
                    color = parseColor(r.props["calendar-color"]),
                    ctag = r.props["getctag"],
                    syncToken = r.props["sync-token"]
                )
            )
        }
        return result
    }

    private fun probeCalendar(account: Account, url: String): CalInfo? {
        val res = execute(
            account, "PROPFIND", url,
            propfindBody(listOf("resourcetype", "displayname", "calendar-color", "getctag", "sync-token")),
            mapOf("Depth" to "0")
        ) ?: return null
        if (res.code !in 200..299) return null
        val ms = DavXmlParser.parse(res.body)
        for (r in ms.responses) {
            if (r.props["calendar"] == "1") {
                val href = r.href ?: url
                val name = r.props["displayname"]?.takeIf { it.isNotBlank() } ?: url
                return CalInfo(
                    accountId = account.id,
                    href = resolve(url, href),
                    displayName = name,
                    color = parseColor(r.props["calendar-color"]),
                    ctag = r.props["getctag"],
                    syncToken = r.props["sync-token"]
                )
            }
        }
        return null
    }

    private fun tryWellKnown(account: Account, base: String): String? {
        return try {
            val url = origin(base) + "/.well-known/caldav"
            val req = Request.Builder().url(url).build()
            val client = client(account.insecureTls)
            val resp = client.newCall(addAuth(req, account)).execute()
            resp.use { r ->
                if (r.isSuccessful) {
                    val finalUrl = r.request.url.toString()
                    if (finalUrl != url) normalize(finalUrl) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ sincronización

    fun fetchEvents(account: Account, calendar: CalInfo, syncToken: String?): PullResult {
        val body = if (syncToken.isNullOrBlank()) calendarQueryBody() else syncCollectionBody(syncToken)
        val res = execute(account, "REPORT", calendar.href, body, mapOf("Depth" to "1"))
        if (res == null || res.code !in 200..299) return PullResult(emptyList(), emptyList(), syncToken)

        val ms = DavXmlParser.parse(res.body)
        val events = mutableListOf<RemoteEvent>()
        val deleted = mutableListOf<String>()
        for (r in ms.responses) {
            val href = r.href ?: continue
            val abs = resolve(calendar.href, href)
            val status = r.status ?: ""
            if (status.contains("404")) {
                deleted.add(abs)
                continue
            }
            val data = r.props["calendar-data"]
            if (data != null) {
                events.add(RemoteEvent(abs, r.props["getetag"], data))
            }
        }
        return PullResult(events, deleted, ms.syncToken ?: syncToken)
    }

    fun putEvent(account: Account, calendar: CalInfo, event: com.lebeche.calendario.data.Event, icalData: String, etag: String?): PutResult {
        val href = event.remoteHref
            ?: (calendar.href.trimEnd('/') + "/" + (event.remoteUid ?: java.util.UUID.randomUUID().toString()) + ".ics")
        val headers = mutableMapOf<String, String>()
        if (etag != null) headers["If-Match"] = quoteEtag(etag)
        else headers["If-None-Match"] = "*"
        val res = execute(account, "PUT", href, icalData, headers, "text/calendar; charset=utf-8")
        if (res == null || res.code !in 200..299) throw CalDavException("Error al guardar el evento (HTTP ${res?.code})")
        return PutResult(href, res.etag)
    }

    fun deleteEvent(account: Account, calendar: CalInfo, href: String, etag: String?) {
        val headers = if (etag != null) mapOf("If-Match" to quoteEtag(etag)) else emptyMap()
        val res = execute(account, "DELETE", href, null, headers)
        if (res == null || res.code !in 200..299) throw CalDavException("Error al borrar el evento (HTTP ${res?.code})")
    }

    // ------------------------------------------------------------------ infraestructura

    private fun execute(
        account: Account,
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>,
        contentType: String = "application/xml; charset=utf-8"
    ): HttpResult? {
        return try {
            val rb = Request.Builder().url(url)
            rb.header("Authorization", Credentials.basic(account.username, account.password))
            for ((k, v) in headers) rb.header(k, v)
            val requestBody = if (body != null) body.toRequestBody(contentType.toMediaType()) else null
            rb.method(method, requestBody)
            val resp = client(account.insecureTls).newCall(rb.build()).execute()
            resp.use { r -> HttpResult(r.code, r.body?.string(), r.header("ETag")) }
        } catch (e: Exception) {
            null
        }
    }

    private fun addAuth(req: Request, account: Account): Request =
        req.newBuilder().header("Authorization", Credentials.basic(account.username, account.password)).build()

    private fun client(insecure: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (insecure) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            b.sslSocketFactory(sslContext.socketFactory, trustAll)
            b.hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }

    private fun propfindBody(props: List<String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>")
        sb.append("<D:propfind xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\" xmlns:CS=\"http://calendarserver.org/ns/\" xmlns:A=\"http://apple.com/ns/ical/\"><D:prop>")
        for (p in props) {
            when (p) {
                "resourcetype" -> sb.append("<D:resourcetype/>")
                "displayname" -> sb.append("<D:displayname/>")
                "current-user-principal" -> sb.append("<D:current-user-principal/>")
                "calendar-home-set" -> sb.append("<C:calendar-home-set/>")
                "getctag" -> sb.append("<CS:getctag/>")
                "sync-token" -> sb.append("<D:sync-token/>")
                "calendar-color" -> sb.append("<CS:calendar-color/><A:calendar-color/>")
                "getetag" -> sb.append("<D:getetag/>")
                "calendar-data" -> sb.append("<C:calendar-data/>")
            }
        }
        sb.append("</D:prop></D:propfind>")
        return sb.toString()
    }

    private fun calendarQueryBody(): String =
        """<?xml version="1.0" encoding="utf-8" ?>
<C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:prop><D:getetag/><C:calendar-data/></D:prop>
  <C:filter>
    <C:comp-filter name="VCALENDAR">
      <C:comp-filter name="VEVENT">
        <C:time-range start="20000101T000000Z" end="21000101T000000Z"/>
      </C:comp-filter>
    </C:comp-filter>
  </C:filter>
</C:calendar-query>"""

    private fun syncCollectionBody(token: String): String =
        """<?xml version="1.0" encoding="utf-8" ?>
<D:sync-collection xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:sync-token>${xmlEscape(token)}</D:sync-token>
  <D:sync-level>1</D:sync-level>
  <D:prop><D:getetag/><C:calendar-data/></D:prop>
</D:sync-collection>"""

    private fun resolve(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return try {
            base.toHttpUrl().resolve(href)?.toString()
                ?: (base.trimEnd('/') + "/" + href.trimStart('/'))
        } catch (e: Exception) {
            base.trimEnd('/') + "/" + href.trimStart('/')
        }
    }

    private fun normalize(url: String): String = url.trim().trimEnd('/')

    private fun origin(url: String): String {
        val u = url.toHttpUrl()
        val port = if (u.port != 80 && u.port != 443) ":${u.port}" else ""
        return "${u.scheme}://${u.host}$port"
    }

    private fun parseColor(s: String?): Int {
        if (s.isNullOrBlank()) return 0xFF8FD6EF.toInt()
        val t = s.trim().removePrefix("#")
        return try {
            when (t.length) {
                6 -> 0xFF000000.toInt() or t.toInt(16)
                8 -> t.toLong(16).toInt()
                else -> 0xFF8FD6EF.toInt()
            }
        } catch (e: Exception) {
            0xFF8FD6EF.toInt()
        }
    }

    private fun quoteEtag(etag: String): String = if (etag.startsWith("\"")) etag else "\"$etag\""

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

class CalDavException(message: String) : Exception(message)

