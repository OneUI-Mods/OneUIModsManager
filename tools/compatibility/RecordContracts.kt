package dev.ryuya.behavioraudit

import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path

/** Compare record equality, hashing, construction and text with shared JVM primitive inputs. */
object RecordContracts {
    private fun construct(
        constructor: java.lang.reflect.Constructor<*>,
        values: Array<Any?>,
    ): Result<Any> =
        try {
            Result.success(constructor.newInstance(*values))
        } catch (error: InvocationTargetException) {
            Result.failure(error.cause ?: error)
        }

    @JvmStatic
    fun main(args: Array<String>) {
        Differential.loader(Path.of(args[0])).use { old ->
            Differential.loader(Path.of(args[1])).use { now ->
                val root = Path.of(Files.readAllLines(Path.of(args[0])).first())
                var records = 0
                var checks = 0
                var differences = 0
                Files.walk(root).use { files ->
                    for (file in files.filter { it.toString().endsWith(".class") }.toList()) {
                        val name =
                            root
                                .relativize(file)
                                .toString()
                                .replace('/', '.')
                                .removeSuffix(".class")
                        if (Regex(".*\\$[0-9].*").matches(name)) continue
                        val constructors =
                            try {
                                val before = Class.forName(name, false, old)
                                if (!before.isRecord) continue
                                val after = Class.forName(name, false, now)
                                val types = before.recordComponents.map { it.type }.toTypedArray()
                                if (
                                    types.any {
                                        it.isEnum || (it.classLoader != null && !it.isArray)
                                    }
                                )
                                    continue
                                types.forEach { Differential.value(it, 0) }
                                before.getDeclaredConstructor(*types).apply {
                                    isAccessible = true
                                } to
                                    after.getDeclaredConstructor(*types).apply {
                                        isAccessible = true
                                    }
                            } catch (_: Throwable) {
                                continue
                            }
                        records++
                        repeat(30) { sample ->
                            val values =
                                constructors.first.parameterTypes
                                    .mapIndexed { index, type ->
                                        Differential.value(type, sample + index * 7)
                                    }
                                    .toTypedArray<Any?>()
                            val before = construct(constructors.first, values)
                            val after = construct(constructors.second, values)
                            checks++
                            if (before.isFailure || after.isFailure) {
                                if (
                                    before.exceptionOrNull()?.javaClass !=
                                        after.exceptionOrNull()?.javaClass
                                ) {
                                    differences++
                                    println(
                                        "DIFF_CTOR\t$name\t${before.exceptionOrNull()}\t${after.exceptionOrNull()}"
                                    )
                                }
                                return@repeat
                            }
                            val x = before.getOrThrow()
                            val y = after.getOrThrow()
                            if (x.hashCode() != y.hashCode()) {
                                differences++
                                println(
                                    "DIFF_HASH\t$name\t$sample\t${x.hashCode()}\t${y.hashCode()}"
                                )
                            }
                            if (
                                (x == constructors.first.newInstance(*values)) !=
                                    (y == constructors.second.newInstance(*values))
                            ) {
                                differences++
                                println("DIFF_EQUAL\t$name\t$sample")
                            }
                            if (x.toString() != y.toString()) {
                                differences++
                                println("DIFF_TEXT\t$name\t$sample\t$x\t$y")
                            }
                        }
                    }
                }
                println("SUMMARY\trecords=$records\tchecks=$checks\tdifferences=$differences")
            }
        }
    }
}
