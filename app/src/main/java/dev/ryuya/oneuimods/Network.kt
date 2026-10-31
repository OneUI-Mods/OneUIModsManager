package dev.ryuya.oneuimods

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.jvm.JvmField
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic

/* Public GitHub HTTPS requests only. Redirects cannot carry downloads to arbitrary hosts. */
object Network {
    @JvmStatic
    @Throws(IOException::class)
    fun open(address: String?, accept: String?, etag: String?): HttpURLConnection? {
        var address: String? = address
        for (redirects in 0..5) {
            val url = URL(address)
            val host = url.getHost()
            if (
                ("https" != url.getProtocol()) ||
                    !(host == "api.github.com" ||
                        host == "github.com" ||
                        host.endsWith(".githubusercontent.com")) ||
                    url.getUserInfo() != null
            )
                throw IOException("Unsupported download origin")
            val c: HttpURLConnection? = url.openConnection() as HttpURLConnection?
            c!!.setInstanceFollowRedirects(false)
            c!!.setConnectTimeout(15000)
            c!!.setReadTimeout(20000)
            c!!.setRequestProperty("User-Agent", "OneUIModsManager/" + BuildConfig.VERSION_NAME)
            c!!.setRequestProperty("Accept", accept)
            if (host == "api.github.com")
                c!!.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (etag != null && !etag.isEmpty()) c!!.setRequestProperty("If-None-Match", etag)
            val status = c!!.getResponseCode()
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                val location = c!!.getHeaderField("Location")
                c!!.disconnect()
                if (location == null) throw IOException("Missing redirect")
                address = URL(url, location).toString()
                continue
            }
            if (status == 200 || status == 304) return c
            var reset: Long = 0
            try {
                if (
                    (status == 403 && "0" == c!!.getHeaderField("X-RateLimit-Remaining")) ||
                        status == 429
                )
                    reset = c!!.getHeaderField("X-RateLimit-Reset").toLong() * 1000
            } catch (ignored: NumberFormatException) {}
            c!!.disconnect()
            throw Network.Failure(status, reset)
        }
        throw IOException("Too many redirects")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun text(url: String?, etag: String?, limit: Int): Network.Response? {
        val c = Network.open(url, "application/vnd.github+json", etag)
        try {
            if (c!!.getResponseCode() == 304) return Network.Response("", etag, true)
            c!!.getInputStream().use { `in` ->
                ByteArrayOutputStream().use { out ->
                    val bytes = ByteArray(8192)
                    var size: Int
                    while ((`in`.read(bytes).also { size = it }) != -1) {
                        if (out.size() + size > limit) throw IOException("Response too large")
                        out.write(bytes, 0, size)
                    }
                    return Network.Response(
                        out.toString(StandardCharsets.UTF_8),
                        c!!.getHeaderField("ETag"),
                        false,
                    )
                }
            }
        } finally {
            c!!.disconnect()
        }
    }

    @JvmRecord
    data class Response(val text: String?, val etag: String?, val unchanged: Boolean) {
        override fun toString(): String = "Response[text=$text, etag=$etag, unchanged=$unchanged]"
    }

    class Failure(status: Int, retryAt: Long) : IOException("HTTP " + status) {
        @JvmField val status: Int
        @JvmField val retryAt: Long

        init {
            this.status = status
            this.retryAt = retryAt
        }
    }
}
