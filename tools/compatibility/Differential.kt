package dev.ryuya.behavioraudit

import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.OptionalInt

/** Bounded, non-I/O comparison of explicitly selected legacy and migrated model methods. */
object Differential {
    private val texts =
        arrayOf(
            "",
            " ",
            "\u0000",
            "\u00a0",
            "\u2007",
            "\u202f",
            "\u2003",
            "abc",
            "0",
            "1",
            "1.2.3",
            "1.10-beta.2",
            "true",
            "4:5",
            "LTE RRC",
            "RSRP: -95 RSRQ: -12",
            " a ",
            "\u00a0a\u00a0",
            "\n",
            ",",
            "::",
            "ja_JP",
        )
    private val integers = intArrayOf(-1, 0, 1, 2, 3, 7, 8, 17, 31, 127, 128, 255, 256, 1024)
    private val floats =
        floatArrayOf(
            Float.NaN,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            -0f,
            0f,
            .00001f,
            -.1f,
            .1f,
            .5f,
            1f,
            2f,
            100f,
        )

    fun value(type: Class<*>, sample: Int): Any =
        when (type) {
            String::class.java -> texts[sample.mod(texts.size)]
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaObjectType -> sample % 2 == 0
            Int::class.javaPrimitiveType,
            Int::class.javaObjectType -> integers[sample.mod(integers.size)]
            Long::class.javaPrimitiveType,
            Long::class.javaObjectType ->
                integers[sample.mod(integers.size)].toLong() *
                    if (sample % 4 == 0) 1_000_000_000L else 1L
            Float::class.javaPrimitiveType,
            Float::class.javaObjectType -> floats[sample.mod(floats.size)]
            Double::class.javaPrimitiveType,
            Double::class.javaObjectType -> floats[sample.mod(floats.size)].toDouble()
            Byte::class.javaPrimitiveType -> sample.toByte()
            Short::class.javaPrimitiveType -> sample.toShort()
            Char::class.javaPrimitiveType -> sample.toChar()
            List::class.java,
            Collection::class.java -> arrayListOf(texts[sample.mod(texts.size)])
            Map::class.java -> linkedMapOf("key" to sample)
            else ->
                when {
                    type.isEnum -> type.enumConstants.let { it[sample.mod(it.size)] }
                    type.isArray && type.componentType.isPrimitive -> {
                        val size = intArrayOf(0, 1, 7, 8, 17, 32, 100)[sample.mod(7)]
                        ReflectArray.newInstance(type.componentType, size).also { array ->
                            repeat(size) {
                                ReflectArray.set(array, it, value(type.componentType, sample + it))
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Unsupported fixture type $type")
                }
        }

    fun shape(value: Any?, depth: Int = 0): String =
        when (value) {
            null -> "null"
            is Float -> "float:${value.toBits().toUInt().toString(16)}"
            is Double -> "double:${value.toBits().toULong().toString(16)}"
            is String ->
                "str:" + value.map { it.code.toString(16).padStart(4, '0') }.joinToString("")
            is Number,
            is Boolean,
            is Char -> "${value.javaClass.simpleName}:$value"
            is Enum<*> -> "${value.declaringJavaClass.name}:${value.name}"
            is OptionalInt -> if (value.isPresent) "optional:${value.asInt}" else "empty"
            is Optional<*> -> if (value.isPresent) shape(value.get(), depth + 1) else "empty"
            is List<*> -> value.map { shape(it, depth + 1) }.toString()
            is Map<*, *> ->
                value.entries
                    .associate { shape(it.key, depth + 1) to shape(it.value, depth + 1) }
                    .toSortedMap()
                    .toString()
            else ->
                when {
                    value.javaClass.isArray ->
                        (0 until ReflectArray.getLength(value))
                            .map { shape(ReflectArray.get(value, it), depth + 1) }
                            .toString()
                    depth > 5 -> value.javaClass.name
                    else ->
                        value.javaClass.name +
                            value.javaClass.declaredFields
                                .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                                .associate {
                                    it.isAccessible = true
                                    it.name to shape(it.get(value), depth + 1)
                                }
                                .toSortedMap()
                }
        }

    private fun invoke(method: Method, arguments: Array<Any?>): String =
        try {
            shape(method.invoke(null, *arguments)) + " INPUT=" + shape(arguments)
        } catch (error: InvocationTargetException) {
            "THROW:${(error.cause ?: error).javaClass.name}"
        } catch (error: Throwable) {
            "HARNESS:${error.javaClass.name}"
        }

    fun loader(path: Path): URLClassLoader =
        URLClassLoader(
            Files.readAllLines(path).map { Path.of(it).toUri().toURL() }.toTypedArray(),
            ClassLoader.getPlatformClassLoader(),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        loader(Path.of(args[0])).use { old ->
            loader(Path.of(args[1])).use { now ->
                var checks = 0
                var methods = 0
                var differences = 0
                for (name in Files.readAllLines(Path.of(args[2]))) {
                    val types =
                        try {
                            Class.forName(name, true, old) to Class.forName(name, true, now)
                        } catch (error: Throwable) {
                            println("SKIP\t$name\t${error.javaClass.simpleName}")
                            continue
                        }
                    for (first in types.first.declaredMethods) {
                        if (
                            !Modifier.isStatic(first.modifiers) ||
                                first.isSynthetic ||
                                first.name == "main"
                        )
                            continue
                        val second =
                            try {
                                val parameters =
                                    first.parameterTypes
                                        .map {
                                            if (it.isPrimitive) it
                                            else Class.forName(it.name, false, now)
                                        }
                                        .toTypedArray()
                                first.parameterTypes.forEach { value(it, 0) }
                                parameters.forEach { value(it, 0) }
                                types.second.getDeclaredMethod(first.name, *parameters).apply {
                                    isAccessible = true
                                }
                            } catch (_: Throwable) {
                                continue
                            }
                        first.isAccessible = true
                        methods++
                        repeat(180) { sample ->
                            val before =
                                first.parameterTypes
                                    .mapIndexed { index, type ->
                                        value(type, sample * (index + 1) + index * 7)
                                    }
                                    .toTypedArray<Any?>()
                            val after =
                                second.parameterTypes
                                    .mapIndexed { index, type ->
                                        value(type, sample * (index + 1) + index * 7)
                                    }
                                    .toTypedArray<Any?>()
                            val left = invoke(first, before)
                            val right = invoke(second, after)
                            checks++
                            if (left != right) {
                                differences++
                                if (differences <= 100)
                                    println("DIFF\t$name#${first.name}\t$sample\t$left\t$right")
                            }
                        }
                    }
                }
                println("SUMMARY\tmethods=$methods\tchecks=$checks\tdifferences=$differences")
            }
        }
    }
}
