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
    private val changed: Runnable = Runnable {
        refreshView()
        maybeAutoInstall()
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        state = ManagerState.get(this)
        if (saved != null) {
            autoRepo = saved.getString("auto", "")
            installRepo = saved.getString("install", "")
            permissionRepo = saved.getString("permission", "")
            installCode = saved.getLong("install_code")
            awaitingResult = saved.getBoolean("awaiting")
            installing = awaitingResult
            if (!awaitingResult && !installRepo.isEmpty()) autoRepo = installRepo
            val list = saved.getStringArrayList("queue")
            if (list != null) queue.addAll(list)
        }
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
                .putStringArrayListExtra("repos", ArrayList<String?>(repos))
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
        if (installing) return
        val app = state!!.find(repo)
        if (app == null) return
        val ready = ReadyStore.read(this, app)
        if (ready == null) return
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
                                    if (!resumed || isFinishing()) {
                                        installing = false
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
                                        installing = false
                                        queue.clear()
                                        message(getString(R.string.install_failed))
                                    }
                                }
                            )
                    } catch (failure: Exception) {
                        state!!
                            .main
                            .post(
                                Runnable {
                                    installing = false
                                    queue.clear()
                                    ReadyStore.clear(this, app)
                                    message(
                                        Ui.error(
                                            this,
                                            if (failure is ApkVerifier.Rejected) failure.reason
                                            else "network",
                                        )
                                    )
                                    refreshView()
                                }
                            )
                    }
                }
            )
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
        out.putStringArrayList("queue", ArrayList<String?>(queue))
        super.onSaveInstanceState(out)
    }
}
