package dev.ryuya.oneuimods

import android.content.Context
import kotlin.jvm.JvmStatic

object AppStatus {
    @JvmStatic
    fun label(c: Context, state: ManagerState, app: AppEntry): String? {
        if (ReadyStore.read(c, app) != null) return c.getString(R.string.ready)
        if (state.downloading && state.downloadRepo == app.repo)
            return c.getString(R.string.downloading)
        val installed = InstalledApp.read(c, app)
        val release = state.release(app)
        if (installed == null) return c.getString(R.string.not_installed)
        if (release == null)
            return c.getString(
                if (state.errors.containsKey(app.repo)) R.string.cached else R.string.no_release
            )
        if (ReleasePolicy.update(installed.code, installed.name, release))
            return c.getString(R.string.update_available)
        if (release.versionCode > 0)
            return c.getString(
                if (installed.code > release.versionCode) R.string.ahead else R.string.latest
            )
        val compared = VersionOrder.compare(installed.name, release.versionName)
        return c.getString(
            if (compared!!.isEmpty()) R.string.unknown_version
            else if (compared!!.getAsInt() > 0) R.string.ahead else R.string.latest
        )
    }
}
