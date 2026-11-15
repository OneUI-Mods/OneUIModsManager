# Kotlin compatibility validation tools

[日本語](README.md) · English

The new validation tools are written in Kotlin. Compile the original Java identified by `baseline-lock.json` and the current Kotlin separately. List dependency JARs and output class directories in classpath files, one path per line. Differential JUnit tests use the unchanged original Java test classes.

```sh
kotlinc Differential.kt RecordContracts.kt -jvm-target 17 -include-runtime -d audit.jar
java -cp audit.jar dev.ryuya.behavioraudit.Differential old-classpath.txt new-classpath.txt pure-classes.txt
java -cp audit.jar dev.ryuya.behavioraudit.RecordContracts old-classpath.txt new-classpath.txt
```

Only list models in `pure-classes.txt` after checking that their source has no I/O, device operations, or thread startup. Do not apply this automatically to arbitrary classes. Comparison covers values, input mutation, and exception types. Methods requiring complex app-specific arguments that cannot be constructed are excluded. Record tests share Java values when comparing reference hashes. This is not a proof for every possible input.

Compile `TextContractProbe.kt` to a JAR with Android SDK 37's `android.jar` on the classpath, then convert it to DEX with d8. Copy the release APK and DEX to dedicated Android 16/17 emulators and make them read-only. Arguments are pairs of package names and on-device APK paths.

```sh
CLASSPATH=/data/local/tmp/probe.dex app_process / dev.ryuya.behaviorprobe.TextContractProbe dev.ryuya.oneuimods /data/local/tmp/module.apk
```

Do not use windows, app components, camera, audio, networking, or settings changes. Do not install test Activities or unsigned APKs on the Galaxy. To inspect the product's Kotlin runtime, construct the APK loader with the boot loader as parent. Keep APK/JAR/DEX files, physical-device logs, settings, and signing keys outside source control.
