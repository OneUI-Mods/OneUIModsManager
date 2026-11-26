package dev.ryuya.oneuimods

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/** Reads the compiled manifest's SDK attribute when Android's package parser returns no result. */
internal object ManifestMinSdk {
    private const val MAX_MANIFEST_BYTES = 1024 * 1024
    private const val XML = 0x0003
    private const val RESOURCE_MAP = 0x0180
    private const val START_ELEMENT = 0x0102
    private const val END_ELEMENT = 0x0103
    private const val NODE_HEADER_SIZE = 16
    private const val ATTRIBUTE_EXTENSION_SIZE = 20
    private const val ATTRIBUTE_SIZE = 20
    private const val INTEGER_DECIMAL = 0x10
    private const val INTEGER_HEX = 0x11

    fun read(apk: File): Int? {
        return try {
            ZipFile(apk).use { zip ->
                val manifest = zip.getEntry("AndroidManifest.xml") ?: return null
                if (manifest.size !in 8..MAX_MANIFEST_BYTES.toLong()) return null
                zip.getInputStream(manifest).use { parse(it.readNBytes(MAX_MANIFEST_BYTES + 1)) }
            }
        } catch (_: IOException) {
            null
        }
    }

    internal fun parse(bytes: ByteArray): Int? {
        if (bytes.size !in 8..MAX_MANIFEST_BYTES) return null
        val xml = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (xml.ushort(0) != XML || xml.ushort(2) != 8 || xml.getInt(4) != bytes.size) return null

        var offset = 8
        var sdkNameIndex = -1
        var depth = 0
        while (offset <= bytes.size - 8) {
            val headerSize = xml.ushort(offset + 2)
            val size = xml.getInt(offset + 4)
            if (headerSize < 8 || size < headerSize || size > bytes.size - offset) return null
            when (xml.ushort(offset)) {
                RESOURCE_MAP -> {
                    for (index in 0 until (size - headerSize) / Int.SIZE_BYTES) {
                        val id = xml.getInt(offset + headerSize + index * Int.SIZE_BYTES)
                        if (id == android.R.attr.minSdkVersion) sdkNameIndex = index
                    }
                }
                START_ELEMENT -> {
                    if (depth == 1 && sdkNameIndex >= 0) {
                        minimumSdk(xml, offset, headerSize, size, sdkNameIndex)?.let { return it }
                    }
                    depth++
                }
                END_ELEMENT -> {
                    if (--depth < 0) return null
                }
            }
            offset += size
        }
        return null
    }

    private fun minimumSdk(xml: ByteBuffer, offset: Int, headerSize: Int, size: Int, name: Int): Int? {
        if (headerSize != NODE_HEADER_SIZE || size < headerSize + ATTRIBUTE_EXTENSION_SIZE) return null
        val extension = offset + headerSize
        val start = xml.ushort(extension + 8)
        val stride = xml.ushort(extension + 10)
        val count = xml.ushort(extension + 12)
        if (start !in ATTRIBUTE_EXTENSION_SIZE..(size - headerSize) || stride < ATTRIBUTE_SIZE) return null
        if (count > (size - headerSize - start) / stride) return null

        for (index in 0 until count) {
            val attribute = extension + start + index * stride
            if (xml.getInt(attribute + 4) != name || xml.ushort(attribute + 12) != 8) continue
            val type = xml.get(attribute + 15).toInt()
            if (type != INTEGER_DECIMAL && type != INTEGER_HEX) return null
            return xml.getInt(attribute + 16).takeIf { it > 0 }
        }
        return null
    }

    private fun ByteBuffer.ushort(offset: Int): Int = getShort(offset).toInt() and 0xffff
}
