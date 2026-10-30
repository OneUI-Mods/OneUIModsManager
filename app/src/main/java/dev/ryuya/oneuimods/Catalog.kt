package dev.ryuya.oneuimods

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashSet
import java.util.List
import kotlin.jvm.JvmStatic
import org.json.JSONException
import org.json.JSONObject

object Catalog {
    @JvmStatic
    @Throws(JSONException::class)
    fun read(raw: String?): MutableList<AppEntry?>? {
        val root = JSONObject(raw)
        if (root.getInt("schema") != 1) throw JSONException("Unknown catalog schema")
        val items = root.getJSONArray("apps")
        if (items.length() < 1 || items.length() > 100) throw JSONException("Invalid catalog size")
        val result = ArrayList<AppEntry?>()
        val repos = HashSet<String?>()
        val packages = HashSet<String?>()
        for (i in 0 until items.length()) {
            val app = AppEntry.parse(items.getJSONObject(i))
            if (!repos.add(app!!.repo) || !packages.add(app!!.packageName))
                throw JSONException("Duplicate catalog identity")
            result.add(app)
        }
        return List.copyOf<AppEntry?>(result)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun load(context: Context): MutableList<AppEntry?>? {
        val cache = File(context.getFilesDir(), "catalog.json")
        if (cache.isFile())
            try {
                return Catalog.read(Io.read(cache))
            } catch (ignored: Exception) {}
        context.getAssets().open("catalog.json").use { `in` ->
            return Catalog.read(String(`in`.readAllBytes(), StandardCharsets.UTF_8))
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun refresh(c: Context): MutableList<AppEntry?>? {
        val response =
            Network.text(
                "https://raw.githubusercontent.com/OneUI-Mods/OneUIModsManager/main/catalog/apps.json",
                null,
                512000,
            )
        val result = Catalog.read(response!!.text)
        Io.write(File(c.getFilesDir(), "catalog.json"), response!!.text!!)
        return result
    }
}
