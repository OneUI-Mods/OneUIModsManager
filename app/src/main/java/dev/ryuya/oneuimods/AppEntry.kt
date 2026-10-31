package dev.ryuya.oneuimods

import java.util.HashSet
import java.util.Set
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic
import org.json.JSONException
import org.json.JSONObject

@JvmRecord
data class AppEntry(
    val repo: String?,
    val packageName: String?,
    val title: String?,
    val description: String?,
    val category: String?,
    val certificates: MutableSet<String?>?,
) {
    fun repositoryUrl(): String? {
        return "https://github.com/OneUI-Mods/" + repo
    }

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun parse(value: JSONObject): AppEntry? {
            val repo = value.getString("repo")
            val pkg = value.getString("packageName")
            if (
                !repo.matches("[A-Za-z0-9_.-]{1,100}".toRegex()) ||
                    !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+".toRegex())
            )
                throw JSONException("Invalid app identity")
            val certs = HashSet<String?>()
            val pins = value.getJSONArray("certificates")
            for (i in 0 until pins.length()) {
                val pin = pins.getString(i).lowercase()
                if (!pin.matches("[a-f0-9]{64}".toRegex()))
                    throw JSONException("Invalid certificate")
                certs.add(pin)
            }
            if (certs.isEmpty()) throw JSONException("Missing certificate")
            return AppEntry(
                repo,
                pkg,
                value.getString("title"),
                value.optString("description"),
                value.optString("category", "device"),
                Set.copyOf<String?>(certs),
            )
        }
    }

    override fun toString(): String =
        "AppEntry[repo=$repo, packageName=$packageName, title=$title, description=$description, category=$category, certificates=$certificates]"
}
