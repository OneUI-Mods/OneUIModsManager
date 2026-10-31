package dev.ryuya.oneuimods

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.jvm.JvmField
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic

object ApkVerifier {
    @JvmStatic
    @Throws(Exception::class)
    fun hash(file: File): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file.toPath()).use { `in` ->
            val buffer = ByteArray(65536)
            var count: Int
            while ((`in`.read(buffer).also { count = it }) != -1) digest.update(buffer, 0, count)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    @JvmStatic
    @Throws(Exception::class)
    fun verify(
        c: Context,
        app: AppEntry,
        file: File,
        expected: String?,
        expectedCode: Long,
    ): ApkVerifier.Verified? {
        val digest = ApkVerifier.hash(file)
        if (
            expected == null ||
                !expected.matches("[a-fA-F0-9]{64}".toRegex()) ||
                !digest.equals(expected, ignoreCase = true)
        )
            throw ApkVerifier.Rejected("hash")
        val pm = c.getPackageManager()
        val apk =
            pm.getPackageArchiveInfo(
                file.getAbsolutePath(),
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        if (apk == null || !app.packageName.equals(apk.packageName) || apk.applicationInfo == null)
            throw ApkVerifier.Rejected("package")
        if (
            (apk!!.applicationInfo!!.flags and
                (ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_TEST_ONLY)) != 0
        )
            throw ApkVerifier.Rejected("debug")
        if (apk!!.applicationInfo!!.minSdkVersion > Build.VERSION.SDK_INT)
            throw ApkVerifier.Rejected("android")
        if (apk.signingInfo == null || apk!!.signingInfo!!.hasMultipleSigners())
            throw ApkVerifier.Rejected("signature")
        val current = apk!!.signingInfo!!.getApkContentsSigners()
        if (current == null || current.size != 1) throw ApkVerifier.Rejected("signature")
        var history = apk!!.signingInfo!!.getSigningCertificateHistory()
        if (history == null) history = current
        var trusted: Boolean = false
        for (cert in history) {
            val pin =
                HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()))
            trusted = trusted or app.certificates!!.contains(pin)
        }
        if (!trusted) throw ApkVerifier.Rejected("signature")
        val installed = InstalledApp.fresh(c, app)
        if (installed != null) {
            if (apk.getLongVersionCode() <= installed.code) throw ApkVerifier.Rejected("not_newer")
            var same: Boolean = false
            for (cert in history) same =
                same or
                    pm.hasSigningCertificate(
                        app.packageName!!,
                        cert.toByteArray(),
                        PackageManager.CERT_INPUT_RAW_X509,
                    )
            if (!same) throw ApkVerifier.Rejected("signature")
        }
        if (expectedCode > 0 && expectedCode != apk.getLongVersionCode())
            throw ApkVerifier.Rejected("version")
        return ApkVerifier.Verified(apk.getLongVersionCode(), apk.versionName, digest)
    }

    @JvmRecord
    data class Verified(val code: Long, val version: String?, val sha256: String?) {
        override fun toString(): String = "Verified[code=$code, version=$version, sha256=$sha256]"
    }

    class Rejected(reason: String) : IOException(reason) {
        @JvmField val reason: String

        init {
            this.reason = reason
        }
    }
}
