package ca.saboor.larpdot.ui.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ca.saboor.larpdot.media.MediaTrackInfo
import ca.saboor.larpdot.service.CustomColorMode
import ca.saboor.larpdot.service.CustomIsland
import ca.saboor.larpdot.service.CustomIslandColor
import ca.saboor.larpdot.service.CustomIslandCondition
import ca.saboor.larpdot.service.CustomIslandIcon
import ca.saboor.larpdot.service.CustomIslandImage
import ca.saboor.larpdot.service.CustomIslandSlot
import ca.saboor.larpdot.service.CustomIslandVisibility
import ca.saboor.larpdot.service.CustomNotification
import ca.saboor.larpdot.service.CustomNotificationArtwork
import ca.saboor.larpdot.service.CustomNotificationRule
import ca.saboor.larpdot.service.isValidNotificationPattern
import ca.saboor.larpdot.service.matchesNotificationPattern
import ca.saboor.larpdot.service.matchingNotification
import ca.saboor.larpdot.service.resolveSources
import ca.saboor.larpdot.ui.components.parseRgbHex
import ca.saboor.larpdot.ui.components.resolveCustomIslandColor
import ca.saboor.larpdot.ui.components.toRgbHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIslandComponentsTest {
    @Test fun notificationImagesUseTheMatchedSourceAndAreHiddenWhileLocked() {
        val artwork = CustomNotificationArtwork(null, null, null)
        val matching = CustomNotification("1", "source", "status", "Download", "Ready", 1, emptyList(), artwork = artwork)
        val unrelated = matching.copy(key = "2", packageName = "other", postedAt = 2, artwork = null)
        val items = mapOf(matching.key to matching, unrelated.key to unrelated)
        val island = CustomIsland(
            "custom",
            visibility = CustomIslandVisibility.CONDITIONAL,
            condition = CustomIslandCondition.NOTIFICATION,
            notificationRule = CustomNotificationRule(packageName = "source"),
        )
        val media = MediaTrackInfo(playerPackageName = "player")
        val unlocked = island.resolveSources(media, null, emptyList(), items, locked = false)
        assertEquals(artwork, unlocked.image)
        assertEquals("source", unlocked.appPackage)
        val locked = island.resolveSources(media, null, emptyList(), items, locked = true)
        assertNull(locked.image)
        assertNull(locked.appPackage)
    }

    @Test fun markdownStylesHeadingsAndKeepsLiveValuesLiteral() {
        val rendered = renderCustomIslandMarkdown(
            "# {song_title}\n\n## Details\n\n- **Bold** and *italic*",
            mapOf("song_title" to "**Live title**"),
        )
        assertEquals("**Live title**\nDetails\n• Bold and italic", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontSize == 28.sp })
        assertTrue(rendered.spanStyles.any { it.item.fontSize == 24.sp })
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test fun templatesReplaceKnownPlaceholdersAndPreserveLiteralText() {
        assertEquals("Playing Song by Artist {unknown}", customIslandText(
            "Playing {song_title} by {artist} {unknown}",
            mapOf("song_title" to "Song", "artist" to "Artist"),
        ))
    }

    @Test fun notificationRuleSelectsNewestMatchingAppChannelAndText() {
        val older = CustomNotification("old", "app", "status", "Download", "Ready", 1, emptyList())
        val newer = older.copy(key = "new", postedAt = 4)
        val wrongApp = newer.copy(packageName = "other", postedAt = 5)
        val wrongChannel = newer.copy(channelId = "other", postedAt = 6)
        val wrongText = newer.copy(text = "Waiting", postedAt = 7)
        val island = CustomIsland("custom", notificationRule = CustomNotificationRule("app", "status", "download", "ready"))
        assertEquals(newer, island.matchingNotification(listOf(older, newer, wrongApp, wrongChannel, wrongText)))
        assertNull(island.matchingNotification(listOf(wrongApp, wrongChannel, wrongText)))
    }

    @Test fun notificationPatternsSupportWildcardsRegexAndSafeValidation() {
        // Wildcards (* and ?)
        assertTrue(matchesNotificationPattern("Get off in 5 stops", "Get off in * stops"))
        assertTrue(matchesNotificationPattern("Get off in 12 stops", "get off in * stops"))
        assertTrue(matchesNotificationPattern("Get off in 1 stop", "Get off in * stop*"))
        assertFalse(matchesNotificationPattern("Board bus 920", "Get off in * stops"))

        assertTrue(matchesNotificationPattern("Get off in 5 stops", "Get off in ? stops"))
        assertFalse(matchesNotificationPattern("Get off in 12 stops", "Get off in ? stops"))

        // Escaped wildcards & regex metacharacters treated literally outside /.../
        assertTrue(matchesNotificationPattern("Rating: 5* (great)", "5\\* (great)"))
        assertFalse(matchesNotificationPattern("Rating: 50 (great)", "5\\* (great)"))

        // Regex via /pattern/
        assertTrue(matchesNotificationPattern("Get off in 1 stop", "/Get off in \\d+ stops?/"))
        assertTrue(matchesNotificationPattern("Get off in 14 stops", "/Get off in \\d+ stops?/"))
        assertFalse(matchesNotificationPattern("Get off now", "/Get off in \\d+ stops?/"))

        // Invalid regex never throws and reports invalid
        assertTrue(isValidNotificationPattern("Get off in * stops"))
        assertTrue(isValidNotificationPattern("/Get off in \\d+ stops?/"))
        assertFalse(isValidNotificationPattern("/[unclosed/"))
        assertFalse(matchesNotificationPattern("anything", "/[unclosed/"))

        // Rule matching integration with Transit-like notifications
        val stop5 = CustomNotification("1", "com.thetransitapp.droid", "go", "Get off in 5 stops", "", 1, emptyList())
        val stop2 = stop5.copy(key = "2", title = "Get off in 2 stops", postedAt = 2)
        val unrelated = stop5.copy(key = "3", title = "Which line are you on?", postedAt = 3)
        val rule = CustomNotificationRule("com.thetransitapp.droid", "go", titleContains = "Get off in * stops")
        assertTrue(rule.matches(stop5))
        assertTrue(rule.matches(stop2))
        assertFalse(rule.matches(unrelated))
    }

    @Test fun customWingMorphLinksMatchSameIconsAndImagesAcrossSidesAndSecondaryBubbles() {
        val base = CustomIsland(
            id = "morph",
            left = CustomIslandSlot(image = CustomIslandImage.APP_ICON, width = 64, height = 24),
            right = CustomIslandSlot(icon = CustomIslandIcon.NAVIGATION, width = 64, height = 24),
            expandedLeft = CustomIslandSlot(image = CustomIslandImage.APP_ICON, width = 44, height = 44),
            expandedRight = CustomIslandSlot(icon = CustomIslandIcon.NAVIGATION, width = 44, height = 44),
        )
        assertEquals(
            CustomWingMorphLinks(leftCompactTarget = CustomWingSide.LEFT, rightCompactTarget = CustomWingSide.RIGHT),
            resolveCustomWingMorphLinks(base, fromSecondaryBubble = false),
        )
        // Secondary bubble only renders its first non-widget wing (left) and links it to matching expanded wing
        assertEquals(
            CustomWingMorphLinks(leftCompactTarget = CustomWingSide.LEFT, rightCompactTarget = null),
            resolveCustomWingMorphLinks(base, fromSecondaryBubble = true),
        )

        // Cross-side match when compact left matches expanded right
        val swapped = CustomIsland(
            id = "swapped",
            left = CustomIslandSlot(icon = CustomIslandIcon.STAR, width = 64, height = 24),
            expandedRight = CustomIslandSlot(icon = CustomIslandIcon.STAR, width = 44, height = 44),
        )
        assertEquals(
            CustomWingMorphLinks(leftCompactTarget = CustomWingSide.RIGHT, rightCompactTarget = null),
            resolveCustomWingMorphLinks(swapped, fromSecondaryBubble = false),
        )

        // Different icons/images or swipe-down-only islands do not morph
        val mismatched = base.copy(
            expandedLeft = CustomIslandSlot(icon = CustomIslandIcon.TIMER, width = 44, height = 44),
            expandedRight = CustomIslandSlot(image = CustomIslandImage.ALBUM_ART, width = 44, height = 44),
        )
        assertEquals(CustomWingMorphLinks(), resolveCustomWingMorphLinks(mismatched, fromSecondaryBubble = false))
        assertEquals(
            CustomWingMorphLinks(),
            resolveCustomWingMorphLinks(base.copy(visibility = CustomIslandVisibility.SWIPE_DOWN), fromSecondaryBubble = false),
        )

        // Matching wing text also morphs and reports content cleanly
        val textIsland = CustomIsland(
            id = "text-wings",
            left = CustomIslandSlot(text = "{progress}", width = 56, height = 24),
            expandedLeft = CustomIslandSlot(text = "{progress}", width = 64, height = 44),
        )
        assertTrue(textIsland.left.hasContent)
        assertTrue(textIsland.expandedLeft.hasContent)
        assertFalse(CustomIslandSlot(text = "   ", width = 56, height = 24).hasContent)
        assertEquals(
            CustomWingMorphLinks(leftCompactTarget = CustomWingSide.LEFT, rightCompactTarget = null),
            resolveCustomWingMorphLinks(textIsland, fromSecondaryBubble = false),
        )
        assertEquals(
            CustomWingMorphLinks(leftCompactTarget = CustomWingSide.LEFT, rightCompactTarget = null),
            resolveCustomWingMorphLinks(textIsland, fromSecondaryBubble = true),
        )

        // Notification icon wing morphs and supports icon color
        val notifIconIsland = CustomIsland(
            id = "notif-icon-wings",
            left = CustomIslandSlot(image = CustomIslandImage.NOTIFICATION_ICON, width = 64, height = 24),
            expandedLeft = CustomIslandSlot(image = CustomIslandImage.NOTIFICATION_ICON, width = 44, height = 44),
        )
        assertTrue(notifIconIsland.left.supportsIconColor)
        assertTrue(notifIconIsland.expandedLeft.supportsIconColor)
        assertEquals(
            CustomWingMorphLinks(leftCompactTarget = CustomWingSide.LEFT, rightCompactTarget = null),
            resolveCustomWingMorphLinks(notifIconIsland, fromSecondaryBubble = false),
        )
    }

    @Test
    fun colorPickerResolutionAndHexParsingHandleDynamicAndCustomColors() {
        val materialYou = Color(0xFFD0BCFF)
        val dominant = Color(0xFF4CAF50)

        assertEquals(
            materialYou,
            resolveCustomIslandColor(
                CustomIslandColor(CustomColorMode.MATERIAL_YOU, 0x112233),
                materialYou,
                dominant,
            ),
        )
        assertEquals(
            dominant,
            resolveCustomIslandColor(
                CustomIslandColor(CustomColorMode.DOMINANT, 0x112233),
                materialYou,
                dominant,
            ),
        )
        assertEquals(
            materialYou,
            resolveCustomIslandColor(
                CustomIslandColor(CustomColorMode.DOMINANT, 0x112233),
                materialYou,
                null,
            ),
        )
        assertEquals(
            Color(0xFFFF7043),
            resolveCustomIslandColor(
                CustomIslandColor(CustomColorMode.CUSTOM, 0xFF7043),
                materialYou,
                dominant,
            ),
        )

        assertEquals("00FF0A", 0x00FF0A.toRgbHex())
        assertEquals(0xFF7043, "#ff7043".parseRgbHex())
        assertEquals(0x000000, "000000".parseRgbHex())
        assertNull("12345".parseRgbHex())
        assertNull("GGGGGG".parseRgbHex())
    }
}
