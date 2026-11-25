package dev.ryuya.oneuimods

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku
import rikka.sui.Sui

internal object ShutterMuteSetup {
    const val PACKAGE = "net.ryuya.dev.galaxyshutter.mute"
    private const val REPO = "galaxy-shutter-mute"
    private const val CERTIFICATE = "5fcf43c524c48708405bd61f78300a91a4e10ca0ceebfe130c650c7d679f2da5"
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val MAX_OUTPUT = 16_384

    enum class Backend { SHIZUKU, ROOT }

    fun supports(app: AppEntry): Boolean = app.repo == REPO && app.packageName == PACKAGE

    fun shizukuAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    fun usesSui(): Boolean = Sui.isSui()

    private fun trustedInstall(context: Context): Boolean =
        context.packageManager.hasSigningCertificate(
            PACKAGE, java.util.HexFormat.of().parseHex(CERTIFICATE), PackageManager.CERT_INPUT_SHA256,
        )

    private fun permissionGranted(context: Context): Boolean =
        context.packageManager.checkPermission(PERMISSION, PACKAGE) == PackageManager.PERMISSION_GRANTED

    fun needsPermission(context: Context, app: AppEntry): Boolean =
        supports(app) && trustedInstall(context) && !permissionGranted(context)

    fun install(context: Context, app: AppEntry, ready: ReadyStore.Ready, backend: Backend) {
        require(supports(app))
        // Keep the exact verified bytes even if a download replaces the prepared file.
        val apk = File.createTempFile("shutter-mute-", ".apk", context.cacheDir)
        try {
            requireNotNull(ready.file).copyTo(apk, overwrite = true)
            ApkVerifier.verify(context, app.copy(certificates = mutableSetOf(CERTIFICATE)), apk, ready.digest, ready.code)
            val user = currentUserId().toString()
            runCommand(
                backend,
                listOf("pm", "install", "--user", user, "--bypass-low-target-sdk-block", "-r", "-S", apk.length().toString()),
                apk,
            )
            val installed = InstalledApp.fresh(context, app)
            if (installed == null || installed.code != ready.code || !trustedInstall(context)) {
                throw IOException("The installed package could not be verified.")
            }
        } finally {
            apk.delete()
        }
    }

    fun grantPermission(context: Context, app: AppEntry, backend: Backend) {
        require(supports(app))
        if (!trustedInstall(context)) throw ApkVerifier.Rejected("signature")
        if (permissionGranted(context)) return
        runCommand(backend, listOf("pm", "grant", "--user", currentUserId().toString(), PACKAGE, PERMISSION))
        if (!permissionGranted(context)) throw IOException("Android did not grant the permission.")
    }

    // Android encodes the profile ID in the upper portion of an application UID.
    private fun currentUserId(): Int = Process.myUid() / 100_000

    private fun start(backend: Backend, command: List<String>): java.lang.Process =
        when (backend) {
            Backend.ROOT -> ProcessBuilder("su", "-c", command.joinToString(" ")).start()
            Backend.SHIZUKU -> {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
                )
                method.isAccessible = true
                method.invoke(null, command.toTypedArray(), null, null) as java.lang.Process
            }
        }

    private fun runCommand(backend: Backend, command: List<String>, apk: File? = null) {
        val process = start(backend, command)
        val io = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "ShutterMuteSetup").apply { isDaemon = true }
        }
        val timeoutSeconds = if (apk == null) 20L else 60L
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        fun remaining() = (deadline - System.nanoTime()).coerceAtLeast(0)
        try {
            val stdout = io.submit<ByteArray> { process.inputStream.use { it.readNBytes(MAX_OUTPUT) } }
            val stderr = io.submit<ByteArray> { process.errorStream.use { it.readNBytes(MAX_OUTPUT) } }
            val input = io.submit {
                process.outputStream.use { output -> apk?.inputStream()?.use { it.copyTo(output) } }
            }
            // Shizuku's timed Process.waitFor polls exitValue before the process exits.
            val exit = io.submit<Int> { process.waitFor() }
            val code = exit.get(remaining(), TimeUnit.NANOSECONDS)
            val output = stdout.get(remaining(), TimeUnit.NANOSECONDS)
            val error = stderr.get(remaining(), TimeUnit.NANOSECONDS)
            if (code != 0) {
                val detail = (error.toString(Charsets.UTF_8) + "\n" + output.toString(Charsets.UTF_8)).trim()
                throw IOException(detail.take(320).ifBlank { "Command exited with code $code." })
            }
            input.get(remaining(), TimeUnit.NANOSECONDS)
        } finally {
            runCatching { process.destroyForcibly() }
            io.shutdownNow()
        }
    }
}
