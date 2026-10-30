package dev.ryuya.oneuimods

import java.util.List
import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    private fun release(version: String?, code: Long, preview: Boolean): Release? {
        return Release(
            version,
            version,
            "",
            "",
            "2026-10-25",
            preview,
            Release.Asset("app-release.apk", "", "", 4096),
            null,
            null,
            code,
            version,
        )
    }

    @Test
    fun comparesNumbersRatherThanText() {
        assertTrue(VersionOrder.compare("1.10.0", "1.9.9")!!.orElseThrow() > 0)
        assertEquals(0, VersionOrder.compare("v1.2", "1.2.0")!!.orElseThrow())
    }

    @Test
    fun ranksPrereleasesAndNumericSuffixes() {
        assertTrue(VersionOrder.compare("1.0.0", "1.0.0-beta.12")!!.orElseThrow() > 0)
        assertTrue(VersionOrder.compare("1.0.0-beta.12", "1.0.0-beta.2")!!.orElseThrow() > 0)
    }

    @Test
    fun unknownTagsDoNotInventUpdates() {
        assertTrue(VersionOrder.compare("rolling", "1.0.0")!!.isEmpty())
        assertFalse(ReleasePolicy.update(4, "1.0.0", release("rolling", 0, false)))
    }

    @Test
    fun knownBuildNumberDetectsSameVersionUpdate() {
        assertTrue(ReleasePolicy.update(1, "0.1.0", release("0.1.0", 3, true)))
        assertFalse(ReleasePolicy.update(4, "0.1.0", release("0.1.0", 3, true)))
    }

    @Test
    fun stableSwitchFiltersPreviewWithoutDowngrading() {
        val stable = release("1.0.0", 1, false)
        val preview = release("2.0.0-beta", 2, true)
        assertEquals(preview, ReleasePolicy.choose(List.of<Release?>(stable, preview), true))
        assertEquals(stable, ReleasePolicy.choose(List.of<Release?>(stable, preview), false))
        assertFalse(ReleasePolicy.update(2, "2.0.0-beta", stable))
    }

    @Test
    fun highestReleaseWinsEvenWhenBackportWasPublishedLater() {
        assertEquals(
            5,
            ReleasePolicy.choose(
                    List.of<Release?>(release("1.2.0", 5, false), release("1.1.5", 4, false)),
                    true,
                )!!
                .versionCode,
        )
    }

    @Test
    fun rejectsForeignRepositoriesAndProtocols() {
        val good: String =
            "https://github.com/OneUI-Mods/Example/releases/download/v1/app-release.apk"
        assertTrue(ReleasePolicy.downloadUrl("Example", good))
        assertFalse(ReleasePolicy.downloadUrl("Example", good.replace("https:", "http:")))
        assertFalse(ReleasePolicy.downloadUrl("Other", good))
        assertFalse(
            ReleasePolicy.downloadUrl(
                "Example",
                good.replace("github.com", "github.com.evil.example"),
            )
        )
        assertFalse(
            ReleasePolicy.downloadUrl("Example", good.replace("github.com", "someone@github.com"))
        )
    }
}
