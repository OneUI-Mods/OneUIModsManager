package dev.ryuya.oneuimods

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayList
import java.util.HashSet
import java.util.HexFormat
import java.util.List
import kotlin.jvm.JvmStatic
import org.json.JSONObject

/* Optional, APK-derived release information. Never requires a catalog edit for a new version. */
object ReleaseMetadata {
    @JvmStatic
    fun apply(
        app: AppEntry,
        release: Release,
        pkg: String?,
        apk: String?,
        code: Long,
        version: String,
        sha: String,
    ): Release? {
        require(
            !(!app.packageName.equals(pkg) ||
                !release.apk!!.name.equals(apk) ||
                code <= 0 ||
                version.isBlank() ||
                version.length > 200 ||
                !sha.matches("[a-fA-F0-9]{64}".toRegex()))
        ) {
            "Metadata does not match the release APK"
        }
        val apiDigest = release.apk!!.digest
        require(
            !(apiDigest!!.startsWith("sha256:") &&
                !apiDigest!!.substring(7).equals(sha, ignoreCase = true))
        ) {
            "APK digest mismatch"
        }
        return Release(
            release.tag,
            release.name,
            release.notes,
            release.pageUrl,
            release.published,
            release.preview,
            Release.Asset(apk, release.apk!!.url, "sha256:" + sha, release.apk!!.size),
            release.checksums,
            release.metadata,
            code,
            version,
        )
    }

    @JvmStatic
    fun load(
        context: Context,
        app: AppEntry,
        releases: MutableList<Release?>,
        online: Boolean,
    ): MutableList<Release?>? {
        // Fetch at most the stable and preview candidates, rather than downloading every old
        // release.
        val candidates = HashSet<Release?>()
        candidates.add(ReleasePolicy.choose(releases, false))
        candidates.add(ReleasePolicy.choose(releases, true))
        val result = ArrayList<Release?>()
        for (release in releases) {
            var enriched: Release? = release
            if (candidates.contains(release) && release!!.metadata != null) {
                try {
                    enriched = ReleaseMetadata.read(context, app, release!!, online)
                } catch (unavailable: Exception) {
                    // Old releases and unavailable/invalid metadata retain the tag-based
                    // comparison.
                }
            }
            result.add(enriched)
        }
        return List.copyOf<Release?>(result)
    }

    @JvmStatic
    @Throws(Exception::class)
    private fun read(context: Context, app: AppEntry, release: Release, online: Boolean): Release? {
        val asset = release.metadata
        require(
            !(!ReleasePolicy.downloadUrl(app.repo, asset!!.url) ||
                asset!!.size < 1 ||
                asset!!.size > 16384)
        ) {
            "Invalid metadata asset"
        }
        val key =
            ReleaseMetadata.hash(
                (asset!!.url + "\n" + asset!!.digest).toByteArray(StandardCharsets.UTF_8)
            )
        val cache = File(context.getFilesDir(), "metadata-" + key + ".json")
        if (cache.isFile() && (!online || asset!!.digest!!.startsWith("sha256:"))) {
            try {
                return ReleaseMetadata.parse(app, release, Io.read(cache)!!)
            } catch (invalidCache: Exception) {
                if (!online) throw invalidCache
            }
        }
        check(online) { "No cached release metadata" }
        val raw = Network.text(asset!!.url, null, 16384)!!.text
        val parsed = ReleaseMetadata.parse(app, release, raw!!)
        Io.write(cache, raw!!)
        return parsed
    }

    @JvmStatic
    @Throws(Exception::class)
    private fun parse(app: AppEntry, release: Release, raw: String): Release? {
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 16384) { "Metadata too large" }
        val digest = release.metadata!!.digest
        require(
            !(digest!!.startsWith("sha256:") &&
                !ReleaseMetadata.hash(bytes).equals(digest!!.substring(7), ignoreCase = true))
        ) {
            "Metadata digest mismatch"
        }
        val value = JSONObject(raw)
        require(value.getInt("schema") == 1) { "Unknown metadata schema" }
        val code = value.get("versionCode").toString()
        require(code.matches("[1-9][0-9]{0,18}".toRegex())) { "Invalid APK build number" }
        return ReleaseMetadata.apply(
            app,
            release,
            value.getString("packageName"),
            value.getString("apk"),
            code.toLong(),
            value.getString("versionName"),
            value.getString("sha256"),
        )
    }

    @JvmStatic
    @Throws(Exception::class)
    private fun hash(data: ByteArray?): String? {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))
    }
}
