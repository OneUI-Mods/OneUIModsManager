package dev.ryuya.oneuimods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutterMuteSetupTest {
    @Test
    fun elevatedSetupRequiresTheExactShutterMuteEntry() {
        val target = AppEntry(
            "galaxy-shutter-mute",
            "net.ryuya.dev.galaxyshutter.mute",
            "Galaxy Shutter Mute",
            "",
            "camera",
            null,
        )
        assertTrue(ShutterMuteSetup.supports(target))
        assertFalse(ShutterMuteSetup.supports(target.copy(packageName = target.packageName + ".manager")))
        assertFalse(ShutterMuteSetup.supports(target.copy(repo = "different-repository")))
    }
}
