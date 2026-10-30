package dev.ryuya.oneuimods

import java.net.URI
import kotlin.jvm.JvmStatic

object ReleasePolicy {
    @JvmStatic
    fun downloadUrl(repo: String?, url: String?): Boolean {
        try {
            val u = URI.create(url)
            return "https" == u.getScheme() &&
                "github.com" == u.getHost() &&
                u.getUserInfo() == null &&
                u.getPort() == -1 &&
                u.getPath().startsWith("/OneUI-Mods/" + repo + "/releases/download/")
        } catch (bad: IllegalArgumentException) {
            return false
        }
    }

    @JvmStatic
    fun choose(releases: MutableList<Release?>, includePreview: Boolean): Release? {
        var best: Release? = null
        for (item in releases) {
            if (item!!.preview && !includePreview) continue
            if (best == null) {
                best = item
                continue
            }
            val c: Int
            if (item!!.versionCode > 0 && best.versionCode > 0)
                c = item!!.versionCode.compareTo(best.versionCode)
            else
                c =
                    VersionOrder.compare(item!!.versionName, best.versionName)!!.orElse(
                        item!!.published!!.compareTo(best.published!!)
                    )
            if (c > 0) best = item
        }
        return best
    }

    @JvmStatic
    fun update(installedCode: Long, installedName: String?, release: Release?): Boolean {
        if (release == null) return false
        if (release.versionCode > 0) return release.versionCode > installedCode
        return VersionOrder.compare(release.versionName, installedName)!!.orElse(0) > 0
    }
}
