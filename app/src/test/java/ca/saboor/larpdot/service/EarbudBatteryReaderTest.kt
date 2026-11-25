package ca.saboor.larpdot.service

import ca.saboor.larpdot.notification.EarbudBatteryLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarbudBatteryReaderTest {
    @Test fun usesShizukuForMissingReadingsAndKeepsStandardReadings() {
        val metadata = EarbudBatteryLevels(left = 60, right = 90)
        assertEquals(metadata, mergeEarbudBatteryLevels(null, metadata))
        assertEquals(EarbudBatteryLevels(42, 90), mergeEarbudBatteryLevels(EarbudBatteryLevels(42, null), metadata))
        assertEquals(EarbudBatteryLevels(null, 0), mergeEarbudBatteryLevels(EarbudBatteryLevels(null, 0), null))
        assertNull(mergeEarbudBatteryLevels(null, null))
    }

    @Test fun usesThePresentationDescriptionToIdentifyEachEarbud() {
        assertEquals(EarbudSide.RIGHT, earbudSideFromPresentation(presentation(0x0e)))
        assertEquals(EarbudSide.LEFT, earbudSideFromPresentation(presentation(0x0d)))
        assertNull(earbudSideFromPresentation(presentation(0x06)))
        assertNull(earbudSideFromPresentation(presentation(0x0d, namespace = 2)))
        assertNull(earbudSideFromPresentation(presentation(0x0e).copyOf(6)))
    }

    @Test fun acceptsOnlySingleByteBatteryPercentages() {
        assertEquals(0, earbudBatteryPercent(byteArrayOf(0)))
        assertEquals(100, earbudBatteryPercent(byteArrayOf(100)))
        assertNull(earbudBatteryPercent(byteArrayOf(101)))
        assertNull(earbudBatteryPercent(byteArrayOf(-1)))
        assertNull(earbudBatteryPercent(byteArrayOf(50, 0)))
        assertNull(earbudBatteryPercent(byteArrayOf()))
    }

    private fun presentation(descriptionLowByte: Byte, namespace: Byte = 1): ByteArray =
        byteArrayOf(0x04, 0x00, 0xad.toByte(), 0x27, namespace, descriptionLowByte, 0x01)
}
