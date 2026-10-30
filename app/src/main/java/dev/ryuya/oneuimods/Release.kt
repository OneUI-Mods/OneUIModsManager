package dev.ryuya.oneuimods

import kotlin.jvm.JvmRecord

@JvmRecord
data class Release(
    val tag: String?,
    val name: String?,
    val notes: String?,
    val pageUrl: String?,
    val published: String?,
    val preview: Boolean,
    val apk: Release.Asset?,
    val checksums: Release.Asset?,
    val metadata: Release.Asset?,
    val versionCode: Long,
    val versionName: String?,
) {
    @JvmRecord
    data class Asset(val name: String?, val url: String?, val digest: String?, val size: Long) {
        override fun toString(): String = "Asset[name=$name, url=$url, digest=$digest, size=$size]"
    }

    override fun toString(): String =
        "Release[tag=$tag, name=$name, notes=$notes, pageUrl=$pageUrl, published=$published, preview=$preview, apk=$apk, checksums=$checksums, metadata=$metadata, versionCode=$versionCode, versionName=$versionName]"
}
