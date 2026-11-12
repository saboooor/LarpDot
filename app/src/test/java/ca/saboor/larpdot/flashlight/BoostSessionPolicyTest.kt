package ca.saboor.larpdot.flashlight

import org.junit.Assert.*
import org.junit.Test

class BoostSessionPolicyTest {
    @Test fun openingDoesNotAcceptAStoppedCameraCallback() {
        val policy = BoostSessionPolicy()
        val opening = policy.start(40, 127)
        assertTrue(policy.accepts(opening))
        policy.stop()
        assertFalse(policy.accepts(opening))
        assertEquals(0, policy.requestedLevel)
    }

    @Test fun rapidRestartRejectsPreviousSessionCallbacks() {
        val policy = BoostSessionPolicy()
        val first = policy.start(30, 127)
        val second = policy.start(50, 127)
        assertFalse(policy.accepts(first))
        assertTrue(policy.accepts(second))
        assertEquals(50, policy.requestedLevel)
    }

    @Test fun brightnessUpdatesDuringOpeningUseLatestClampedLevel() {
        val policy = BoostSessionPolicy()
        val opening = policy.start(1, 127)
        policy.strength(300, 127)
        assertEquals(127, policy.requestedLevel)
        assertTrue(policy.accepts(opening))
        policy.strength(-20, 127)
        assertEquals(1, policy.requestedLevel)
    }

    @Test fun lateSliderUpdatesCannotRestartStoppedSession() {
        val policy = BoostSessionPolicy()
        policy.start(70, 127)
        policy.stop()
        policy.strength(80, 127)
        assertEquals(0, policy.requestedLevel)
    }
}
