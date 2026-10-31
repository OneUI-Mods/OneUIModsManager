package dev.ryuya.behaviorprobe

import dalvik.system.PathClassLoader
import java.lang.reflect.InvocationTargetException

/**
 * Release-APK text/model fixtures. Creates no window, recorder, camera, network or app component.
 */
object TextContractProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val originalStrip = String::class.java.getMethod("strip")
        val originalBlank = String::class.java.getMethod("isBlank")
        val originalSplit = String::class.java.getMethod("split", String::class.java)
        check(args.size % 2 == 0)
        for (index in args.indices step 2) {
            val packageName = args[index]
            // Match an application's boot parent; do not substitute the probe's Kotlin runtime.
            val loader = PathClassLoader(args[index + 1], ClassLoader.getSystemClassLoader().parent)
            val helper = Class.forName("$packageName.TextCompatibility", true, loader)
            val instance = helper.getField("INSTANCE").get(null)
            val strip = helper.getDeclaredMethod("strip", String::class.java)
            val blank = helper.getDeclaredMethod("isBlank", CharSequence::class.java)
            val split = helper.getDeclaredMethod("split", String::class.java, String::class.java)
            for (code in 0..0xffff) {
                val char = code.toChar()
                val text = "$char-content-$char"
                check(strip.invoke(instance, text) == originalStrip.invoke(text)) {
                    "$packageName strip U+${code.toString(16)}"
                }
                check(
                    blank.invoke(instance, char.toString()) == originalBlank.invoke(char.toString())
                ) {
                    "$packageName blank U+${code.toString(16)}"
                }
            }
            val inputs =
                arrayOf("", " ", "ab", ",a,,b,,,", "\n", "a\r\nb\n", "\u00a0", "x\u0000y\u0000")
            for (input in inputs) for (regex in
                arrayOf(",", "\\R", "", "(?=b)", "\u0000", "\\s+")) {
                @Suppress("UNCHECKED_CAST")
                val expected = originalSplit.invoke(input, regex) as Array<String>
                @Suppress("UNCHECKED_CAST")
                val actual = split.invoke(instance, input, regex) as Array<String>
                check(actual.contentEquals(expected)) {
                    "$packageName split input=$input regex=$regex"
                }
            }
            try {
                split.invoke(instance, "", "[")
                error("Invalid regex accepted")
            } catch (error: InvocationTargetException) {
                check(error.cause is java.util.regex.PatternSyntaxException)
            }
            println(
                "PASS $packageName release: 65536 whitespace boundaries, 48 split cases, invalid expression"
            )
            if (packageName == "dev.ryuya.galaxyservicepanel") {
                val model = Class.forName("$packageName.DiagnosticDocument", true, loader)
                val source = arrayListOf("\u00a0", "\u2007", "\u202f", "\u00a0LTE RRC\u00a0")
                val rows =
                    model.getDeclaredMethod("parse", List::class.java).invoke(null, source)
                        as List<*>
                check(rows.size == source.size)
                for (row in rows.indices) {
                    val entry = rows[row] ?: error("Missing row")
                    val field =
                        entry.javaClass.getDeclaredField("text").apply { isAccessible = true }
                    check(field.get(entry) == source[row])
                }
                println(
                    "PASS actual Service Mode model retains nonbreaking rows and native indices"
                )
            }
        }
    }
}
