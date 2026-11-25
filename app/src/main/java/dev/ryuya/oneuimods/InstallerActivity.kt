package dev.ryuya.oneuimods

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.ArrayDeque
import java.util.ArrayList
import kotlin.jvm.JvmField
import rikka.shizuku.Shizuku

/* Installation is sequential, user-confirmed, and rechecks the local APK before sharing it. */
abstract class InstallerActivity : AppCompatActivity() {
    @JvmField protected var state: ManagerState? = null
    private val queue: ArrayDeque<String?> = ArrayDeque<String?>()
    private var autoRepo: String = ""
    private var installRepo: String = ""
    private var permissionRepo: String = ""
    private var installCode: Long = 0
    private var resumed: Boolean = false
    private var installing: Boolean = false
    private var awaitingResult: Boolean = false
    private var shutterPermissionRepo: String? = null
    private var shutterPermissionForInstall: Boolean = false
    private val shutterPermission = Shizuku.OnRequestPermissionResultListener { request, result ->
        val repo = shutterPermissionRepo
        if (request == SHUTTER_PERMISSION_REQUEST && repo != null) {
            shutterPermissionRepo = null
            if (result == PackageManager.PERMISSION_GRANTED) {
                runShutterSetup(repo, shutterPermissionForInstall, ShutterMuteSetup.Backend.SHIZUKU)
            } else {
                queue.clear()
                message(getString(R.string.shutter_access_denied))
            }
        }
    }
    private val changed: Runnable = Runnable {
        refreshView()
        maybeAutoInstall()
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        state = ManagerState.get(this)
        Shizuku.addRequestPermissionResultListener(shutterPermission)
        if (saved != null) {
            autoRepo = saved.getString("auto", "")
            installRepo = saved.getString("install", "")
            permissionRepo = saved.getString("permission", "")
            installCode = saved.getLong("install_code")
            awaitingResult = saved.getBoolean("awaiting")
            shutterPermissionRepo = saved.getString("shutter_permission_repo")
            shutterPermissionForInstall = saved.getBoolean("shutter_permission_install")
            installing = awaitingResult
            if (!awaitingResult && !installRepo.isEmpty()) autoRepo = installRepo
            val list = saved.getStringArrayList("queue")
            if (list != null) queue.addAll(list)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shutterPermission)
        super.onDestroy()
    }

    protected abstract fun refreshView()

    override fun onStart() {
        super.onStart()
        state!!.add(changed)
    }

    override fun onStop() {
        state!!.remove(changed)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        InstalledApp.invalidate()
        refreshView()
        maybeAutoInstall()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun maybeAutoInstall() {
        if (!resumed || autoRepo.isEmpty() || installing) return
        val app = state!!.find(autoRepo)
        if (app != null && ReadyStore.read(this, app) != null) {
            val repo: String? = autoRepo
            autoRepo = ""
            install(repo!!)
        } else if (!state!!.downloading) autoRepo = ""
    }

    protected fun download(repos: MutableList<String?>, automatic: Boolean) {
        if (repos.isEmpty() || state!!.downloading) return
        if (!state!!.preferences.getBoolean("asked_notifications", false)) {
            state!!.preferences.edit().putBoolean("asked_notifications", true).apply()
            if (
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            )
                requestPermissions(arrayOf<String?>(Manifest.permission.POST_NOTIFICATIONS), 104)
        }
        if (automatic && repos.size == 1) autoRepo = repos.get(0)!!
        startForegroundService(
            Intent(this, DownloadService::class.java)
                .putStringArrayListExtra("repos", ArrayList<String?>(repos)),
        )
    }

    protected fun installPrepared(repos: MutableList<String?>) {
        queue.clear()
        repos
            .stream()
            .filter { r: String? -> r != "OneUIModsManager" }
            .forEach { repo: String? -> queue.add(repo) }
        if (repos.contains("OneUIModsManager")) queue.add("OneUIModsManager")
        if (!queue.isEmpty()) install(queue.peek()!!)
    }

    protected fun install(repo: String) {
        if (installing || shutterPermissionRepo != null) return
        val app = state!!.find(repo)
        if (app == null) return
        val ready = ReadyStore.read(this, app)
        if (ready == null) return
        if (ShutterMuteSetup.supports(app)) {
            showShutterSetup(app, install = true)
            return
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            permissionRepo = repo
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()),
                ),
                101,
            )
            return
        }
        installing = true
        installRepo = repo
        installCode = ready.code
        state!!
            .worker!!
            .execute(
                Runnable {
                    try {
                        ApkVerifier.verify(this, app, ready.file!!, ready.digest!!, ready.code)
                        state!!
                            .main
                            .post(
                                Runnable {
                                    if (!resumed || isFinishing() || isDestroyed) {
                                        installing = false
                                        if (!isFinishing() && !isDestroyed) autoRepo = repo
                                        return@Runnable
                                    }
                                    try {
                                        val uri =
                                            FileProvider.getUriForFile(
                                                this,
                                                BuildConfig.APPLICATION_ID + ".files",
                                                ready.file!!,
                                            )
                                        val intent =
                                            Intent(Intent.ACTION_INSTALL_PACKAGE)
                                                .setDataAndType(
                                                    uri,
                                                    "application/vnd.android.package-archive",
                                                )
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                        awaitingResult = true
                                        startActivityForResult(intent, 102)
                                    } catch (failure: RuntimeException) {
                                        awaitingResult = false
                                        installing = false
                                        queue.clear()
                                        message(getString(R.string.install_failed))
                                    }
                                },
                            )
                    } catch (failure: Exception) {
                        state!!
                            .main
                            .post(
                                Runnable {
                                    installing = false
                                    queue.clear()
                                    ReadyStore.clear(applicationContext, app)
                                    if (!resumed || isFinishing() || isDestroyed) return@Runnable
                                    message(
                                        Ui.error(
                                            this,
                                            if (failure is ApkVerifier.Rejected) failure.reason
                                            else "network",
                                        ),
                                    )
                                    refreshView()
                                },
                            )
                    }
                },
            )
    }

    protected fun showShutterSetup(app: AppEntry, install: Boolean) {
        if (installing || shutterPermissionRepo != null || !ShutterMuteSetup.supports(app)) return
        val repo = requireNotNull(app.repo)
        val shizuku = ShutterMuteSetup.shizukuAvailable()
        val explanation = getString(if (install) R.string.shutter_install_prompt else R.string.shutter_grant_prompt)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shutter_setup_title)
            .setMessage(if (shizuku) explanation else explanation + "\n\n" + getString(R.string.shutter_no_broker))
            .setNegativeButton(R.string.cancel) { _, _ -> queue.clear() }
            .setOnCancelListener { queue.clear() }
        if (shizuku) {
            dialog.setPositiveButton(if (ShutterMuteSetup.usesSui()) R.string.shutter_sui else R.string.shutter_shizuku) { _, _ ->
                requestShutterAccess(repo, install)
            }
            dialog.setNeutralButton(R.string.shutter_root) { _, _ ->
                runShutterSetup(repo, install, ShutterMuteSetup.Backend.ROOT)
            }
        } else {
            dialog.setPositiveButton(R.string.shutter_root) { _, _ ->
                runShutterSetup(repo, install, ShutterMuteSetup.Backend.ROOT)
            }
        }
        dialog.show()
    }

    private fun requestShutterAccess(repo: String, install: Boolean) {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                runShutterSetup(repo, install, ShutterMuteSetup.Backend.SHIZUKU)
                return
            }
            if (!Shizuku.shouldShowRequestPermissionRationale()) {
                shutterPermissionRepo = repo
                shutterPermissionForInstall = install
                Shizuku.requestPermission(SHUTTER_PERMISSION_REQUEST)
                return
            }
        } catch (_: RuntimeException) {
            shutterPermissionRepo = null
        }
        queue.clear()
        message(getString(R.string.shutter_access_denied))
    }

    private fun runShutterSetup(repo: String, install: Boolean, backend: ShutterMuteSetup.Backend) {
        if (installing || isFinishing || isDestroyed) return
        val manager = state ?: return
        val app = manager.find(repo) ?: return
        val context = applicationContext
        val ready = if (install) ReadyStore.read(context, app) else null
        if (install && ready == null) {
            queue.clear()
            refreshView()
            return
        }
        installing = true
        manager.worker!!.execute {
            var apkInstalled = false
            val failure = runCatching {
                if (ready != null) {
                    ShutterMuteSetup.install(context, app, ready, backend)
                    apkInstalled = true
                    ReadyStore.clear(context, app)
                }
                ShutterMuteSetup.grantPermission(context, app, backend)
            }.exceptionOrNull()
            InstalledApp.invalidate()
            manager.notifyChanged()
            manager.main.post {
                installing = false
                if (isFinishing || isDestroyed) return@post
                refreshView()
                if (failure != null) {
                    queue.clear()
                    val detail = if (failure is ApkVerifier.Rejected) {
                        Ui.error(this, failure.reason)
                    } else {
                        failure.cause?.localizedMessage ?: failure.localizedMessage ?: ""
                    }
                    val resource = when {
                        apkInstalled -> R.string.shutter_installed_grant_failed
                        install -> R.string.shutter_install_failed
                        else -> R.string.shutter_grant_failed
                    }
                    message(getString(resource, detail))
                } else {
                    Toast.makeText(this, R.string.shutter_granted, Toast.LENGTH_SHORT).show()
                    if (install && queue.peek() == repo) queue.remove()
                    queue.peek()?.let { next -> manager.main.postDelayed({ install(next) }, 250) }
                }
            }
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == 101) {
            val repo: String? = permissionRepo
            permissionRepo = ""
            if (getPackageManager().canRequestPackageInstalls()) install(repo!!) else queue.clear()
        }
        if (request == 102) {
            installing = false
            awaitingResult = false
            val app = state!!.find(installRepo)
            val now: InstalledApp? = if (app == null) null else InstalledApp.fresh(this, app)
            val success: Boolean = now != null && now.code >= installCode
            if (success) {
                ReadyStore.clear(this, app!!)
                if (!queue.isEmpty() && queue.peek() == installRepo) queue.remove()
            } else {
                queue.clear()
                Toast.makeText(this, R.string.install_canceled, Toast.LENGTH_SHORT).show()
            }
            installRepo = ""
            refreshView()
            if (success && !queue.isEmpty())
                state!!.main.postDelayed(Runnable { install(queue.peek()!!) }, 250)
        }
    }

    protected fun message(body: String?) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(body)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putString("auto", autoRepo)
        out.putString("install", installRepo)
        out.putString("permission", permissionRepo)
        out.putLong("install_code", installCode)
        out.putBoolean("awaiting", awaitingResult)
        out.putString("shutter_permission_repo", shutterPermissionRepo)
        out.putBoolean("shutter_permission_install", shutterPermissionForInstall)
        out.putStringArrayList("queue", ArrayList<String?>(queue))
        super.onSaveInstanceState(out)
    }

    private companion object {
        const val SHUTTER_PERMISSION_REQUEST = 105
    }
}
