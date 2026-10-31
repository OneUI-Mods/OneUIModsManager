package dev.ryuya.oneuimods

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import java.io.File
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.LinkedHashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/* User-initiated foreground downloads. Installation always uses Android's confirmation UI. */
class DownloadService : Service() {
    private val worker: ExecutorService? = Executors.newSingleThreadExecutor()
    private val canceled: AtomicBoolean = AtomicBoolean()
    private var state: ManagerState? = null
    private var running: Boolean = false

    override fun onCreate() {
        super.onCreate()
        state = ManagerState.get(this)
        getSystemService<NotificationManager?>(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    DownloadService.CHANNEL,
                    getString(R.string.download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        if (intent == null) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        if ("cancel" == intent.getAction()) {
            canceled.set(true)
            return Service.START_NOT_STICKY
        }
        if (running) return Service.START_NOT_STICKY
        running = true
        canceled.set(false)
        state!!.downloading = true
        state!!.downloadError = ""
        startForeground(
            DownloadService.NOTICE,
            notification(getString(R.string.preparing), 0)!!,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val requested = intent.getStringArrayListExtra("repos")
        worker!!.execute(
            Runnable {
                try {
                    if (requested != null)
                        for (repo in LinkedHashSet<String?>(requested)) {
                            if (canceled.get()) break
                            val app = state!!.find(repo)
                            if (app == null) continue
                            val release = state!!.release(app)
                            if (release == null || ReadyStore.read(this, app) != null) continue
                            state!!.downloadRepo = repo!!
                            state!!.downloadPercent = 0
                            state!!.notifyChanged()
                            try {
                                download(app, release)
                            } catch (rejected: ApkVerifier.Rejected) {
                                state!!.downloadError = rejected.reason
                                break
                            } catch (failed: Exception) {
                                state!!.downloadError =
                                    if (canceled.get()) "canceled" else "network"
                                break
                            }
                        }
                } finally {
                    state!!.downloading = false
                    state!!.downloadRepo = ""
                    state!!.notifyChanged()
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        )
        return Service.START_NOT_STICKY
    }

    @Throws(Exception::class)
    private fun download(app: AppEntry, release: Release) {
        var expected = release.apk!!.digest
        if (expected!!.startsWith("sha256:")) expected = expected!!.substring(7)
        if (!expected!!.matches("[a-fA-F0-9]{64}".toRegex())) {
            if (release.checksums == null) throw ApkVerifier.Rejected("missing_hash")
            val sums = Network.text(release.checksums.url, null, 128000)!!.text
            val line =
                Pattern.compile(
                    "(?m)^([a-fA-F0-9]{64})[ \t]+\\*?" +
                        Pattern.quote(release.apk!!.name) +
                        "[ \t]*$"
                )
            val match = line.matcher(sums)
            if (!match.find()) throw ApkVerifier.Rejected("missing_hash")
            expected = match.group(1)
        }
        if (!ReleasePolicy.downloadUrl(app.repo, release.apk!!.url))
            throw ApkVerifier.Rejected("origin")
        val expectedSize = release.apk!!.size
        if (expectedSize < 1024 || expectedSize > 256L * 1024 * 1024)
            throw ApkVerifier.Rejected("size")
        val partial = File(ReadyStore.folder(this), app.repo + ".part.apk")
        val connection = Network.open(release.apk!!.url, "application/octet-stream", null)
        try {
            connection!!.getInputStream().use { `in` ->
                Files.newOutputStream(partial.toPath()).use { out ->
                    val bytes = ByteArray(65536)
                    var total: Long = 0
                    var last: Long = 0
                    var count: Int
                    while ((`in`.read(bytes).also { count = it }) != -1) {
                        if (canceled.get()) throw InterruptedIOException("Canceled")
                        total += count.toLong()
                        if (total > expectedSize) throw ApkVerifier.Rejected("size")
                        out.write(bytes, 0, count)
                        val now = SystemClock.elapsedRealtime()
                        if (now - last > 250) {
                            last = now
                            state!!.downloadPercent = (total * 100 / expectedSize).toInt()
                            state!!.notifyChanged()
                            getSystemService<NotificationManager?>(NotificationManager::class.java)
                                .notify(
                                    DownloadService.NOTICE,
                                    notification(app.title, state!!.downloadPercent),
                                )
                        }
                    }
                    if (total != expectedSize) throw ApkVerifier.Rejected("size")
                }
            }
            val verified = ApkVerifier.verify(this, app, partial, expected!!, release.versionCode)
            val target = ReadyStore.file(this, app)
            Files.move(
                partial.toPath(),
                target!!.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            ReadyStore.save(this, app, verified!!)
            state!!.notifyChanged()
        } finally {
            connection!!.disconnect()
            partial.delete()
        }
    }

    private fun notification(title: String?, progress: Int): Notification? {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val cancel =
            PendingIntent.getService(
                this,
                1,
                Intent(this, DownloadService::class.java).setAction("cancel"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return android.app.Notification.Builder(this, DownloadService.CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(getString(R.string.downloading))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(
                android.app.Notification.Action.Builder(null, getString(R.string.cancel), cancel)
                    .build()
            )
            .build()
    }

    override fun onDestroy() {
        canceled.set(true)
        worker!!.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val CHANNEL: String = "downloads"
        private const val NOTICE: Int = 4
    }
}
