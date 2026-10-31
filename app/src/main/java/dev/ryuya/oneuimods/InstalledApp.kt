package dev.ryuya.oneuimods

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import java.util.HashMap
import java.util.Optional
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic
import kotlin.jvm.Synchronized

@JvmRecord
data class InstalledApp(val code: Long, val name: String?, val enabled: Boolean) {

    companion object {
        private val CACHE: MutableMap<String?, Optional<InstalledApp>> =
            HashMap<String?, Optional<InstalledApp>>()

        @JvmStatic
        @Synchronized
        fun invalidate() {
            InstalledApp.CACHE.clear()
        }

        @JvmStatic
        @Synchronized
        fun read(c: Context, app: AppEntry): InstalledApp? {
            return InstalledApp.CACHE.computeIfAbsent(app.packageName) { key: String? ->
                    Optional.ofNullable<InstalledApp>(InstalledApp.fresh(c, app))
                }!!
                .orElse(null)
        }

        @JvmStatic
        @Synchronized
        fun fresh(c: Context, app: AppEntry): InstalledApp? {
            try {
                val info = c.getPackageManager().getPackageInfo(app.packageName!!, 0)
                return InstalledApp(
                    info.getLongVersionCode(),
                    info.versionName,
                    info.applicationInfo == null || info!!.applicationInfo!!.enabled,
                )
            } catch (missing: NameNotFoundException) {
                return null
            }
        }
    }

    override fun toString(): String = "InstalledApp[code=$code, name=$name, enabled=$enabled]"
}
