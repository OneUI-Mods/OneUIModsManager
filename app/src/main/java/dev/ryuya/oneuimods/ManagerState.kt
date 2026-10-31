package dev.ryuya.oneuimods

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.HashSet
import java.util.List
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.Synchronized
import kotlin.math.max

/* Application-scoped state; no Activity references survive a detached UI. */
class ManagerState private constructor(c: Context) {
    @JvmField val context: Context
    @JvmField val preferences: SharedPreferences
    @JvmField val main: Handler = Handler(Looper.getMainLooper())
    @JvmField val worker: ExecutorService? = Executors.newSingleThreadExecutor()
    @JvmField
    val feeds: MutableMap<String?, MutableList<Release?>?> =
        ConcurrentHashMap<String?, MutableList<Release?>?>()
    @JvmField val errors: MutableMap<String?, String?> = ConcurrentHashMap<String?, String?>()
    @JvmField val listeners: MutableSet<Runnable?> = HashSet<Runnable?>()
    @JvmField @Volatile var apps: MutableList<AppEntry?>? = java.util.List.of<AppEntry?>()
    @JvmField @Volatile var checking: Boolean = false
    @JvmField @Volatile var completed: Int = 0
    @JvmField @Volatile var downloadRepo: String = ""
    @JvmField @Volatile var downloadPercent: Int = 0
    @JvmField @Volatile var downloading: Boolean = false
    @JvmField @Volatile var downloadError: String = ""
    @JvmField @Volatile var retryAt: Long = 0

    init {
        context = c.getApplicationContext()
        preferences = context.getSharedPreferences("manager", 0)
        try {
            apps = Catalog.load(context)
        } catch (e: Exception) {
            throw IllegalStateException("Bundled catalog is invalid", e)
        }
        for (app in apps!!) loadCached(app!!)
    }

    fun add(listener: Runnable?) {
        listeners.add(listener)
    }

    fun remove(listener: Runnable?) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        main.post(
            Runnable {
                for (listener in List.copyOf<Runnable?>(listeners)) listener.run()
            }
        )
    }

    fun previews(): Boolean {
        return preferences.getBoolean("previews", true)
    }

    fun release(app: AppEntry): Release? {
        return ReleasePolicy.choose(
            feeds.getOrDefault(app.repo, java.util.List.of<Release?>())!!,
            previews(),
        )
    }

    fun find(repo: String?): AppEntry? {
        for (app in apps!!) if (app!!.repo.equals(repo)) return app
        return null
    }

    fun update(app: AppEntry): Boolean {
        val installed = InstalledApp.read(context, app)
        return installed != null &&
            ReleasePolicy.update(installed.code, installed.name, release(app))
    }

    fun refresh(force: Boolean) {
        if (checking) return
        if (
            !force &&
                System.currentTimeMillis() - preferences.getLong("last_check", 0) < 6 * 3600000L
        )
            return
        checking = true
        completed = 0
        retryAt = 0
        notifyChanged()
        worker!!.execute(
            Runnable {
                try {
                    var catalogLoaded: Boolean = true
                    try {
                        apps = Catalog.refresh(context)
                    } catch (ignored: Exception) {
                        catalogLoaded = false // Keep the verified last valid catalog offline.
                    }
                    val summary =
                        ParallelChecks.run<AppEntry?>(
                            apps!!,
                            4,
                            Function { app: AppEntry? -> this.checkRelease(app!!) },
                            Consumer { result: ParallelChecks.Result? ->
                                completed++
                                retryAt = Math.max(retryAt, result!!.retryAt)
                                notifyChanged()
                            },
                        )
                    if (catalogLoaded && summary!!.successful)
                        preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    checking = false
                    notifyChanged()
                }
            }
        )
    }

    private fun checkRelease(app: AppEntry): ParallelChecks.Result? {
        try {
            val cache = File(context.getFilesDir(), "release-" + app.repo + ".json")
            val key: String = "etag_" + app.repo
            val etag: String? = if (cache.isFile()) preferences.getString(key, "") else ""
            val response =
                Network.text(
                    "https://api.github.com/repos/OneUI-Mods/" +
                        app.repo +
                        "/releases?per_page=100",
                    etag,
                    4 * 1024 * 1024,
                )
            val raw: String? = if (response!!.unchanged) Io.read(cache) else response!!.text
            val parsed = ReleaseMetadata.load(context, app, GitHubReleases.parse(app, raw)!!, true)
            if (!response!!.unchanged) Io.write(cache, raw!!)
            feeds.put(app.repo, parsed)
            errors.remove(app.repo)
            preferences
                .edit()
                .putString(key, if (response!!.etag == null) "" else response!!.etag)
                .putLong("checked_" + app.repo, System.currentTimeMillis())
                .apply()
            return ParallelChecks.Result(true, 0)
        } catch (error: Exception) {
            errors.put(
                app.repo,
                if (error is Network.Failure) "HTTP " + error.status else "offline",
            )
            val retry: Long = if (error is Network.Failure) error.retryAt else 0
            return ParallelChecks.Result(
                false,
                if (retry > System.currentTimeMillis()) retry else 0,
            )
        }
    }

    private fun loadCached(app: AppEntry) {
        try {
            feeds.put(
                app.repo,
                ReleaseMetadata.load(
                    context,
                    app,
                    GitHubReleases.parse(
                        app,
                        Io.read(File(context.getFilesDir(), "release-" + app.repo + ".json")),
                    )!!,
                    false,
                ),
            )
        } catch (ignored: Exception) {}
    }

    companion object {
        private var instance: ManagerState? = null

        @JvmStatic
        @Synchronized
        fun get(c: Context): ManagerState? {
            if (ManagerState.instance == null) ManagerState.instance = ManagerState(c)
            return ManagerState.instance
        }
    }
}
