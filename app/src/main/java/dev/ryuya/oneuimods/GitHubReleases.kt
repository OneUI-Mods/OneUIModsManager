package dev.ryuya.oneuimods

import android.net.Uri
import java.util.ArrayList
import java.util.List
import kotlin.jvm.JvmStatic
import org.json.JSONArray
import org.json.JSONException

object GitHubReleases {
    @JvmStatic
    @Throws(JSONException::class)
    fun parse(app: AppEntry, raw: String?): MutableList<Release?>? {
        val all = JSONArray(raw)
        val result = ArrayList<Release?>()
        for (i in 0 until all.length()) {
            val item = all.getJSONObject(i)
            if (item.optBoolean("draft")) continue
            val assets = item.getJSONArray("assets")
            val apks = ArrayList<Release.Asset?>()
            var sums: Release.Asset? = null
            var metadata: Release.Asset? = null
            for (n in 0 until assets.length()) {
                val a = assets.getJSONObject(n)
                val name = a.getString("name")
                val url = a.getString("browser_download_url")
                if (!ReleasePolicy.downloadUrl(app.repo, url)) continue
                val asset = Release.Asset(name, url, a.optString("digest", ""), a.getLong("size"))
                val lower = name.lowercase()
                if (
                    lower.endsWith(".apk") &&
                        !lower.contains("debug") &&
                        !lower.contains("unsigned")
                )
                    apks.add(asset)
                if (lower.startsWith("sha256sums")) sums = asset
                if (lower == "release-metadata.json") metadata = asset
            }
            if (apks.size != 1) continue
            val tag = item.getString("tag_name")
            val page: String = app.repositoryUrl() + "/releases/tag/" + Uri.encode(tag)
            result.add(
                Release(
                    tag,
                    item.optString("name", tag),
                    item.optString("body"),
                    page,
                    item.optString("published_at"),
                    item.optBoolean("prerelease"),
                    apks.get(0),
                    sums,
                    metadata,
                    0,
                    tag.replaceFirst("^[vV]".toRegex(), ""),
                )
            )
        }
        return List.copyOf<Release?>(result)
    }
}
