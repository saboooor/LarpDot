package ca.saboor.larpdot.service

import org.junit.Assert.*
import org.junit.Test

class MediaAppVisibilityTest {
    @Test fun onlyTheCurrentSessionOwnerHidesMedia() {
        assertTrue(shouldHideMediaApp(true, "com.spotify.music", "com.spotify.music"))
        assertFalse(shouldHideMediaApp(true, "com.google.android.apps.youtube.music", "com.spotify.music"))
        assertFalse(shouldHideMediaApp(true, "com.android.launcher", "com.spotify.music"))
    }

    @Test fun leavingPlayerOrChangingSessionMakesMediaVisibleAgain() {
        assertTrue(shouldHideMediaApp(true, "player.a", "player.a"))
        assertFalse(shouldHideMediaApp(true, "browser", "player.a"))
        assertFalse(shouldHideMediaApp(true, "player.a", "player.b"))
    }

    @Test fun disabledSettingAndUnknownPackagesNeverHideMedia() {
        assertFalse(shouldHideMediaApp(false, "player", "player"))
        assertFalse(shouldHideMediaApp(true, null, "player"))
        assertFalse(shouldHideMediaApp(true, "player", null))
        assertFalse(shouldHideMediaApp(true, "", ""))
    }
}
