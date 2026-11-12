package ca.saboor.larpdot.flashlight

/** Rejects callbacks from camera opens/captures that were superseded or stopped. */
internal class BoostSessionPolicy {
    var generation = 0
        private set
    var requestedLevel = 0
        private set

    fun start(level: Int, maximum: Int): Int {
        generation++
        requestedLevel = level.coerceIn(1, maximum.coerceAtLeast(1))
        return generation
    }

    fun strength(level: Int, maximum: Int) {
        if (requestedLevel > 0) requestedLevel = level.coerceIn(1, maximum.coerceAtLeast(1))
    }

    fun stop() { generation++; requestedLevel = 0 }
    fun accepts(token: Int): Boolean = token == generation && requestedLevel > 0
}
