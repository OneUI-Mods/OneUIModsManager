package dev.ryuya.oneuimods

import java.util.Set
import org.junit.Assert.*
import org.junit.Test

class ReleaseMetadataTest {
    private val sha: String? = "a".repeat(64)
    private val app: AppEntry =
        AppEntry(
            "Example",
            "dev.example.app",
            "Example",
            "",
            "device",
            Set.of<String?>("b".repeat(64)),
        )
    private val release: Release =
        Release(
            "v1.0.0-build.2",
            "1.0.0",
            "",
            "",
            "2026-10-25",
            false,
            Release.Asset("app-release.apk", "", "sha256:" + sha, 4096),
            null,
            null,
            0,
            "1.0.0-build.2",
        )

    @Test
    fun apkMetadataDetectsAnotherBuildWithoutCatalogVersionMapping() {
        val parsed =
            ReleaseMetadata.apply(
                app,
                release,
                app.packageName,
                release.apk!!.name,
                3,
                "1.0.0",
                sha!!,
            )
        assertTrue(ReleasePolicy.update(2, "1.0.0", parsed))
        assertFalse(ReleasePolicy.update(3, "1.0.0", parsed))
        assertEquals(3, parsed!!.versionCode)
    }

    @Test
    fun rejectsMetadataForAnotherPackageOrAsset() {
        assertThrows(
            IllegalArgumentException::class.java,
            {
                ReleaseMetadata.apply(
                    app,
                    release,
                    "dev.other.app",
                    release.apk!!.name,
                    3,
                    "1.0.0",
                    sha!!,
                )
            },
        )
        assertThrows(
            IllegalArgumentException::class.java,
            {
                ReleaseMetadata.apply(app, release, app.packageName, "other.apk", 3, "1.0.0", sha!!)
            },
        )
    }

    @Test
    fun rejectsInvalidBuildsAndHashes() {
        assertThrows(
            IllegalArgumentException::class.java,
            {
                ReleaseMetadata.apply(
                    app,
                    release,
                    app.packageName,
                    release.apk!!.name,
                    0,
                    "1.0.0",
                    sha!!,
                )
            },
        )
        assertThrows(
            IllegalArgumentException::class.java,
            {
                ReleaseMetadata.apply(
                    app,
                    release,
                    app.packageName,
                    release.apk!!.name,
                    3,
                    "",
                    sha!!,
                )
            },
        )
        assertThrows(
            IllegalArgumentException::class.java,
            {
                ReleaseMetadata.apply(
                    app,
                    release,
                    app.packageName,
                    release.apk!!.name,
                    3,
                    "1.0.0",
                    "b".repeat(64),
                )
            },
        )
    }

    @Test
    fun ordinaryNewReleasesWorkWithoutOptionalMetadata() {
        assertTrue(ReleasePolicy.update(1, "0.9.0", release))
        assertFalse(ReleasePolicy.update(3, "1.1.0", release))
    }
}
