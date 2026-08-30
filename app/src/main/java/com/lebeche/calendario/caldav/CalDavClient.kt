package com.lebeche.calendario.caldav

import android.util.Log
import com.lebeche.calendario.BuildConfig
import com.lebeche.calendario.data.Account
import com.lebeche.calendario.data.CalInfo
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "CalDAV"

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

/** Resultado del descubrimiento, con diagnóstico para la interfaz. */
data class DiscoveryResult(val calendars: List<CalInfo>, val error: String? = null)

private class HttpResult(val code: Int, val body: String?, val etag: String?)

/**
 * Cliente CalDAV sobre OkHttp. Implementa descubrimiento (PROPFIND),
 * sincronización (REPORT sync-collection / calendar-query) y escritura (PUT/DELETE).
 */
class CalDavClient {

    // ------------------------------------------------------------------ descubrimiento

    fun discover(account: Account): DiscoveryResult {
        val diag = StringBuilder()
        var base = normalize(account.baseUrl)
        diag.appendLine("URL base: $base")

        if (!looksLikeDavRoot(base)) {
            tryWellKnown(account, base)?.let {
                base = it
                diag.appendLine("Descubrimiento .well-known -> $base")
            }
        }

        val principal = discoverPrincipal(account, base, diag)
            ?: discoverPrincipal(account, synologyPrincipal(base, account.username), diag)
            ?: base.also { diag.appendLine("Principal: no devuelto por el servidor; se usa la raíz") }
        diag.appendLine("Principal: $principal")

        val home = discoverHome(account, principal, diag)
            ?: discoverHome(account, synologyHome(base, account.username), diag)
            ?: principal.also { diag.appendLine("Home: no devuelto por el servidor; se usa el principal") }
        diag.appendLine("Home: $home")

        val calendars = listCalendars(account, home, diag)
        if (calendars.isNotEmpty()) {
            diag.appendLine("Calendarios encontrados: ${calendars.size}")
            return DiscoveryResult(calendars)
        }

        probeCalendar(account, base, diag)?.let {
            diag.appendLine("La raíz es un calendario directo: ${it.href}")
            return DiscoveryResult(listOf(it))
        }
        probeCalendar(account, home, diag)?.let {
            diag.appendLine("El home es un calendario directo: ${it.href}")
            return DiscoveryResult(listOf(it))
        }

        return DiscoveryResult(emptyList(), diag.toString())
    }

    private fun discoverPrincipal(account: Account, base: String, diag: StringBuilder): String? {
        val res = execute(
            account, "PROPFIND", base,
            propfindBody(listOf("current-user-principal")),
            mapOf("Depth" to "0")
        )
        if (res == null) { diag.appendLine("PROPFIND principal: error de red en $base"); return null }
        diag.appendLine("PROPFIND principal $base -> HTTP ${res.code}")
        if (res.code !in 200..299) return null
        val ms = DavXmlParser.parse(res.body)
        for (r in ms.responses) {
            val href = r.props["current-user-principal"] ?: continue
            return resolve(base, href)
        }
        return null
    }

    private fun discoverHome(account: Account, principal: String, diag: StringBuilder): String? {
        val res = execute(
            account, "PROPFIND", principal,
            propfindBody(listOf("calendar-home-set")),
            mapOf("Depth" to "0")
        )
        if (res == null) { diag.appendLine("PROPFIND home: error de red en $principal"); return null }
        diag.appendLine("PROPFIND home $principal -> HTTP ${res.code}")
        if (res.code !in 200..299) return null
        val ms = DavXmlParser.parse(res.body)
        for (r in ms.responses) {
            val href = r.props["calendar-home-set"] ?: continue
            return resolve(principal, href)
        }
        return null
    }

    private fun listCalendars(account: Account, home: String, diag: StringBuilder): List<CalInfo> {
        val res = execute(
            account, "PROPFIND", home,
            propfindBody(listOf("resourcetype", "displayname", "calendar-color", "getctag", "sync-token", "current-user-privilege-set")),
            mapOf("Depth" to "1")
        )
        if (res == null) { diag.appendLine("PROPFIND calendarios: error de red en $home"); return emptyList() }
        diag.appendLine("PROPFIND calendarios $home -> HTTP ${res.code}")
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
                    syncToken = null,
                    readOnly = r.props["writable"] != "1"
                )
            )
        }
        diag.appendLine("Calendarios listados: ${result.size}")
        return result
    }

    private fun probeCalendar(account: Account, url: String, diag: StringBuilder): CalInfo? {
        val res = execute(
            account, "PROPFIND", url,
            propfindBody(listOf("resourcetype", "displayname", "calendar-color", "getctag", "sync-token", "current-user-privilege-set")),
            mapOf("Depth" to "0")
        )
        if (res == null) { diag.appendLine("Probe calendario: error de red en $url"); return null }
        diag.appendLine("Probe calendario $url -> HTTP ${res.code}")
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
                    syncToken = null,
                    readOnly = r.props["writable"] != "1"
                )
            }
        }
        return null
    }

    private fun tryWellKnown(account: Account, base: String): String? {
        val url = origin(base) + "/.well-known/caldav"
        val client = client(account.insecureTls)
        var current = url
        var redirects = 0
        while (true) {
            try {
                val resp = client.newCall(addAuth(Request.Builder().url(current).build(), account)).execute()
                resp.use { r ->
                    if (r.isSuccessful) {
                        val finalUrl = r.request.url.toString()
                        if (finalUrl != url && looksLikeDavRoot(finalUrl) && !looksLikeLogin(finalUrl)) {
                            return normalize(finalUrl)
                        }
                        return null
                    }
                    val loc = r.header("Location")
                    if ((r.code == 301 || r.code == 302 || r.code == 303 || r.code == 307 || r.code == 308) &&
                        loc != null && redirects < 5
                    ) {
                        current = try { current.toHttpUrl().resolve(loc)?.toString() ?: current } catch (e: Exception) { current }
                        redirects++
                    } else {
                        return null
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }
    }

    // ------------------------------------------------------------------ sincronización

    fun fetchEvents(account: Account, calendar: CalInfo, syncToken: String?): PullResult {
        var res = execute(account, "REPORT", calendar.href, reportBody(syncToken), mapOf("Depth" to "1"))
        if (res == null) throw CalDavException("Error de red al sincronizar ${calendar.displayName}")
        if (res.code !in 200..299) {
            if (!syncToken.isNullOrBlank()) {
                res = execute(account, "REPORT", calendar.href, calendarQueryBody(), mapOf("Depth" to "1"))
                if (res == null || res.code !in 200..299) {
                    throw CalDavException("El servidor no aceptó el REPORT (HTTP ${res?.code})")
                }
            } else {
                throw CalDavException("El servidor no aceptó el REPORT (HTTP ${res.code})")
            }
        }

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
        if (res == null || res.code !in 200..299) {
            Log.e(TAG, "PUT $href -> HTTP ${res?.code}: ${res?.body?.take(400)}")
            throw CalDavException("Error al guardar el evento (HTTP ${res?.code})")
        }
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
        val httpClient = client(account.insecureTls)
        var currentUrl = url
        var redirects = 0
        try {
            while (true) {
                val rb = Request.Builder().url(currentUrl)
                rb.header("Authorization", Credentials.basic(account.username, account.password))
                for ((k, v) in headers) rb.header(k, v)
                val requestBody = if (body != null) body.toRequestBody(contentType.toMediaType()) else null
                rb.method(method, requestBody)
                val resp = httpClient.newCall(rb.build()).execute()
                val code = resp.code
                val location = resp.header("Location")
                if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) &&
                    location != null && redirects < 5
                ) {
                    currentUrl = try {
                        currentUrl.toHttpUrl().resolve(location)?.toString() ?: currentUrl
                    } catch (e: Exception) {
                        currentUrl
                    }
                    resp.close()
                    redirects++
                    continue
                }
                val result = HttpResult(code, resp.body?.string(), resp.header("ETag"))
                resp.close()
                return result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en $method $url", e)
            return null
        }
    }

    private fun addAuth(req: Request, account: Account): Request =
        req.newBuilder().header("Authorization", Credentials.basic(account.username, account.password)).build()

    private fun client(insecure: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(LoggingInterceptor)
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
        sb.append("""<?xml version="1.0" encoding="utf-8" ?>""")
        sb.append("""<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:CS="http://calendarserver.org/ns/" xmlns:A="http://apple.com/ns/ical/"><D:prop>""")
        for (p in props) {
            when (p) {
                "resourcetype" -> sb.append("<D:resourcetype/>")
                "displayname" -> sb.append("<D:displayname/>")
                "current-user-principal" -> sb.append("<D:current-user-principal/>")
                "calendar-home-set" -> sb.append("<C:calendar-home-set/>")
                "current-user-privilege-set" -> sb.append("<D:current-user-privilege-set/>")
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

    private fun reportBody(syncToken: String?): String =
        if (syncToken.isNullOrBlank()) calendarQueryBody() else syncCollectionBody(syncToken)

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

    /** Normaliza la URL base garantizando barra final en la ruta (Synology la exige). */
    private fun normalize(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return try {
            val httpUrl = u.toHttpUrl()
            var path = httpUrl.encodedPath
            if (path.isEmpty()) path = "/"
            if (!path.endsWith("/")) path += "/"
            httpUrl.newBuilder().encodedPath(path).build().toString()
        } catch (e: Exception) {
            u.trimEnd('/') + "/"
        }
    }

    private fun origin(url: String): String {
        val u = url.toHttpUrl()
        val port = if (u.port != 80 && u.port != 443) ":${u.port}" else ""
        return "${u.scheme}://${u.host}$port"
    }

    private fun looksLikeDavRoot(url: String): Boolean {
        val path = try { url.toHttpUrl().encodedPath.lowercase() } catch (e: Exception) { url.lowercase() }
        if (path.contains(".well-known")) return false
        return path.contains("caldav") || path.contains("/dav") ||
            path.contains("principals") || path.contains("calendars")
    }

    private fun looksLikeLogin(url: String): Boolean {
        val path = try { url.toHttpUrl().encodedPath.lowercase() } catch (e: Exception) { url.lowercase() }
        return path.contains("webman") || path.contains("login") ||
            path.contains(".cgi") || path.contains("/index") || path.contains("portal")
    }

    private fun synologyPrincipal(base: String, username: String): String =
        resolve(base, "principals/users/" + encodePathSegment(username) + "/")

    private fun synologyHome(base: String, username: String): String =
        resolve(base, "calendars/" + encodePathSegment(username) + "/")

    private fun encodePathSegment(s: String): String =
        try { URLEncoder.encode(s, "UTF-8").replace("+", "%20") } catch (e: Exception) { s }

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

    private fun quoteEtag(etag: String): String {
        if (etag.startsWith('"')) return etag
        val q = '"'
        return "$q$etag$q"
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private object LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, ">> ${req.method} ${req.url}")
                req.body?.let { body ->
                    if (body.contentLength() != 0L) {
                        try {
                            val buffered = Buffer()
                            body.writeTo(buffered)
                            Log.d(TAG, ">> body: ${buffered.readUtf8().take(2000)}")
                        } catch (e: Exception) {
                            Log.d(TAG, ">> body: (no legible)")
                        }
                    }
                }
            }
            val resp = chain.proceed(req)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "<< HTTP ${resp.code} ${resp.request.url}")
                try {
                    val text = resp.peekBody(4096).string()
                    if (text.isNotBlank()) Log.d(TAG, "<< body: ${text.take(4000)}")
                } catch (e: Exception) {
                    // sin cuerpo que mostrar
                }
            }
            return resp
        }
    }
}

class CalDavException(message: String) : Exception(message)
