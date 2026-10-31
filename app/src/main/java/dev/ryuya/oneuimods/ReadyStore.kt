package dev.ryuya.oneuimods

import android.content.Context
import java.io.File
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic
import org.json.JSONException
import org.json.JSONObject

object ReadyStore {
    @JvmStatic
    fun folder(c: Context): File {
        val folder = File(c.getFilesDir(), "updates")
        check(!(!folder.isDirectory() && !folder.mkdirs())) { "Cannot create update folder" }
        return folder
    }

    @JvmStatic
    fun file(c: Context, app: AppEntry): File? {
        return File(ReadyStore.folder(c), app.repo + ".apk")
    }

    @JvmStatic
    fun read(c: Context, app: AppEntry): ReadyStore.Ready? {
        try {
            val data =
                JSONObject(ManagerState.get(c)!!.preferences.getString("ready_" + app.repo, "{}"))
            val file = ReadyStore.file(c, app)
            if (!file!!.isFile()) return null
            val installed = InstalledApp.read(c, app)
            val code = data.getLong("code")
            if (installed != null && installed.code >= code) {
                ReadyStore.clear(c, app)
                return null
            }
            return ReadyStore.Ready(file, code, data.getString("version"), data.getString("sha256"))
        } catch (missing: JSONException) {
            return null
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun save(c: Context?, app: AppEntry, verified: ApkVerifier.Verified) {
        val data =
            JSONObject()
                .put("code", verified.code)
                .put("version", verified.version)
                .put("sha256", verified.sha256)
        ManagerState.get(c!!)!!
            .preferences
            .edit()
            .putString("ready_" + app.repo, data.toString())
            .apply()
    }

    @JvmStatic
    fun clear(c: Context, app: AppEntry) {
        ManagerState.get(c)!!.preferences.edit().remove("ready_" + app.repo).apply()
        ReadyStore.file(c, app)!!.delete()
    }

    @JvmRecord
    data class Ready(val file: File?, val code: Long, val version: String?, val digest: String?) {
        override fun toString(): String =
            "Ready[file=$file, code=$code, version=$version, digest=$digest]"
    }
}
