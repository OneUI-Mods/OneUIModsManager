package dev.ryuya.oneuimods

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.jvm.JvmStatic

object Io {
    @JvmStatic
    @Throws(IOException::class)
    fun read(file: File): String? {
        return String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun write(file: File, value: String) {
        Files.write(file.toPath(), value.toByteArray(StandardCharsets.UTF_8))
    }
}
