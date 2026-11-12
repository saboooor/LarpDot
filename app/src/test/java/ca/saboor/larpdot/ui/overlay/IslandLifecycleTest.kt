package ca.saboor.larpdot.ui.overlay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class IslandLifecycleTest {
    @Test fun exitRetainsVisibilityUntilGracePeriodFinishes() = runBlocking {
        val lifecycle = IslandLifecycle<IslandType> { this }
        lifecycle.update(IslandType.FLASHLIGHT, true)
        val exited = CompletableDeferred<Unit>()
        lifecycle.update(IslandType.FLASHLIGHT, false, 1) { exited.complete(Unit) }
        assertTrue(lifecycle.isActive(IslandType.FLASHLIGHT))
        withTimeout(1000) { exited.await() }
        assertFalse(lifecycle.isActive(IslandType.FLASHLIGHT))
    }

    @Test fun reactivationCancelsExitWithoutAffectingOtherProviders() = runBlocking {
        val lifecycle = IslandLifecycle<IslandType> { this }
        lifecycle.update(IslandType.MEDIA, true)
        lifecycle.update(IslandType.FLASHLIGHT, true)
        var staleExit = false
        lifecycle.update(IslandType.FLASHLIGHT, false, 1) { staleExit = true }
        lifecycle.update(IslandType.FLASHLIGHT, true)
        coroutineContext[Job]!!.children.toList().forEach { it.join() }
        assertTrue(lifecycle.isActive(IslandType.FLASHLIGHT))
        assertTrue(lifecycle.isActive(IslandType.MEDIA))
        assertFalse(staleExit)
    }

    @Test fun clearCancelsPendingCallbacksAndResetsAllProviders() = runBlocking {
        val lifecycle = IslandLifecycle<IslandType> { this }
        var callback = false
        for (type in IslandType.entries) {
            lifecycle.update(type, true)
            lifecycle.update(type, false, 1) { callback = true }
        }
        lifecycle.clear()
        coroutineContext[Job]!!.children.toList().forEach { it.join() }
        assertFalse(callback)
        IslandType.entries.forEach { assertFalse(lifecycle.isActive(it)) }
    }
}
