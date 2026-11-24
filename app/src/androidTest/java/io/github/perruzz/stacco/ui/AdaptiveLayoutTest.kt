package io.github.perruzz.stacco.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.perruzz.stacco.ui.components.AdaptiveRow
import io.github.perruzz.stacco.ui.components.DialogButtons
import io.github.perruzz.stacco.ui.components.SectionLabel
import io.github.perruzz.stacco.ui.settings.SettingRow
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.today.StampTimeline
import io.github.perruzz.stacco.domain.BreakInfo
import io.github.perruzz.stacco.domain.DayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The layouts that adapt to large fonts on narrow screens: nothing cut, no word broken, no button
 * squeezed into a column of letters, no tab pushed out of the bar.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val res get() = rule.activity.resources

    /** [content] in a box [width] wide, with the fonts at [fontScale]. */
    private fun show(width: Dp, fontScale: Float = 1f, content: @Composable () -> Unit) = rule.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            StaccoTheme(darkTheme = false) { Box(Modifier.width(width)) { content() } }
        }
    }

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first().lineCount
    }

    private fun SemanticsNodeInteraction.bounds() = getUnclippedBoundsInRoot()

    /** A single text, not the row that merges it with the others. */
    private fun text(value: String) = rule.onNodeWithText(value, useUnmergedTree = true)

    @Composable
    private fun Tile(tag: String, width: Dp, height: Dp = 40.dp) = Box(Modifier.width(width).height(height).testTag(tag))

    @Test
    fun threeTilesShareTheRowWhenTheyFit() {
        show(300.dp) { AdaptiveRow { Tile("a", 80.dp); Tile("b", 80.dp); Tile("c", 80.dp, height = 60.dp) } }
        val (a, c) = listOf("a", "c").map { rule.onNodeWithTag(it).bounds() }
        assertEquals(a.top, c.top)
        assertEquals((a.right - a.left).value, (300f - 20f) / 3f, 0.6f)
        // The cells of a line share its height.
        rule.onNodeWithTag("a").assertHeightIsEqualTo(60.dp)
    }

    @Test
    fun tilesWrapWhenTheWidestDoesNotFit() {
        show(200.dp) { AdaptiveRow { Tile("a", 80.dp); Tile("b", 80.dp); Tile("c", 80.dp) } }
        val (a, b, c) = listOf("a", "b", "c").map { rule.onNodeWithTag(it).bounds() }
        assertEquals(a.top, b.top)
        assertTrue(c.top > a.bottom)
    }

    @Test
    fun oneTilePerLineOnAVeryNarrowScreen() {
        show(90.dp) { AdaptiveRow { Tile("x", 80.dp); Tile("y", 80.dp) } }
        val (x, y) = listOf("x", "y").map { rule.onNodeWithTag(it).bounds() }
        assertTrue(y.top > x.bottom)
    }

    @Test
    fun aSectionLabelKeepsItsWordsWhole() {
        // A card on a 360dp phone with fonts at 150%.
        show(272.dp, fontScale = 1.5f) {
            SectionLabel("Reperibilità") { Text("Sei reperibile oggi") }
        }
        val label = rule.onNodeWithText("REPERIBILITÀ")
        assertEquals(1, label.lineCount())
        assertTrue(label.bounds().right < rule.onNodeWithText("Sei reperibile oggi").bounds().left)
    }

    @Test
    fun dialogButtonsWrapInsteadOfSqueezing() {
        show(240.dp, fontScale = 1.5f) {
            DialogButtons(onDismiss = {}, onConfirm = {}, confirmLabel = "Conferma", onExtra = {}, extraLabel = "Rimuovi")
        }
        val confirm = rule.onNodeWithText("Conferma").assertIsDisplayed()
        assertEquals(1, confirm.lineCount())
        // The extra action goes on a line of its own, above Cancel and Confirm.
        assertTrue(rule.onNodeWithText("Rimuovi").bounds().bottom <= confirm.bounds().top)
    }

    @Test
    fun aLongValueGoesUnderTheTextOfASetting() {
        show(272.dp, fontScale = 1.5f) {
            SettingRow(Icons.Rounded.Celebration, "Santo patrono", subtitle = "La festa del patrono della città in cui lavori", value = "Nessuno", onClick = {})
        }
        assertTrue(text("Nessuno").bounds().top >= text("Santo patrono").bounds().bottom)
    }

    @Test
    fun aShortValueStaysBesideTheText() {
        show(400.dp) {
            SettingRow(Icons.Rounded.Celebration, "Ore giornaliere", subtitle = "Il tempo da lavorare", value = "8h", onClick = {})
        }
        val value = text("8h").bounds()
        val title = text("Ore giornaliere").bounds()
        assertTrue(value.left > title.right)
        assertTrue(value.top < text("Il tempo da lavorare").bounds().bottom)
    }

    @Test
    fun everyTabStaysInTheBarWithLargeFontsOnANarrowPhone() {
        // A tab in the middle selected: its label used to push the last ones out of the bar.
        show(360.dp, fontScale = 1.5f) { StaccoNavBar(selected = MainTab.CALENDAR, onSelect = {}) }
        for (tab in listOf(MainTab.TODAY, MainTab.BANK, MainTab.SETTINGS)) {
            // Whole, not squeezed to a sliver at the end of the bar.
            val bounds = rule.onNodeWithContentDescription(res.getString(tab.label)).assertIsDisplayed().assertWidthIsAtLeast(48.dp).bounds()
            assertTrue("${res.getString(tab.label)} inside the screen", bounds.right <= 360.dp)
        }
    }

    @Test
    fun theCirclesOfTheTimelineKeepTheSameDistance() {
        // "24m break" under the end of the break and "worked 8h" under the exit; nothing under the
        // entry and the start of the break. With large fonts too, where those lines take more room.
        var fontScale by mutableFloatStateOf(1f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StaccoTheme(darkTheme = false) {
                    Box(Modifier.width(360.dp)) {
                        StampTimeline(
                            record = DayRecord(entry = 480, breakOut = 750, breakIn = 774, exit = 1020),
                            next = null,
                            breakInfo = BreakInfo(actual = 24, deducted = 30, assumed = false, ongoing = false, shortfall = 6),
                            worked = 480,
                            onEdit = {},
                        )
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.5f)) {
            fontScale = scale
            rule.waitForIdle()
            val rows = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            assertEquals(4, rows.size)
            // Every row as tall as the tallest, so the circles, in the middle of their rows, are evenly spaced.
            rows.forEach { assertEquals("at $scale", rows[0].height, it.height, 0.5f) }
            val gaps = rows.map { it.center.y }.zipWithNext { above, below -> below - above }
            gaps.forEach { assertEquals("at $scale", gaps[0], it, 0.5f) }
        }
    }
}
