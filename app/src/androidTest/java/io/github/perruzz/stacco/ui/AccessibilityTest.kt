package io.github.perruzz.stacco.ui

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.DayFix
import io.github.perruzz.stacco.domain.DayRecord
import io.github.perruzz.stacco.domain.DayType
import io.github.perruzz.stacco.domain.WorkCalendar
import io.github.perruzz.stacco.domain.WorkSettings
import io.github.perruzz.stacco.ui.bank.DaysToFixCard
import io.github.perruzz.stacco.ui.calendar.cellDescription
import io.github.perruzz.stacco.ui.components.RollingText
import io.github.perruzz.stacco.ui.components.StampDial
import io.github.perruzz.stacco.ui.settings.HoldToConfirmButton
import io.github.perruzz.stacco.ui.settings.StepperRow
import io.github.perruzz.stacco.ui.settings.SwitchRow
import io.github.perruzz.stacco.ui.settings.WeekdayToggles
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import io.github.perruzz.stacco.ui.today.label
import io.github.perruzz.stacco.ui.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** What TalkBack finds on the controls changed for 2.0: labels, roles, states, touch targets. */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val res get() = rule.activity.resources

    private fun show(content: @Composable () -> Unit) = rule.setContent { StaccoTheme(darkTheme = false) { content() } }

    /** The touch area of the node, as accessibility services measure it. */
    private fun SemanticsNodeInteraction.assertTouchTargetAtLeast48(): SemanticsNodeInteraction {
        val bounds = fetchSemanticsNode().touchBoundsInRoot
        val min = with(rule.density) { 48.dp.toPx() } - 1f
        assertTrue("touch target ${bounds.width}x${bounds.height}px", bounds.width >= min && bounds.height >= min)
        return this
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun stateContains(text: String) = SemanticsMatcher("state description contains $text") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains(text) == true
    }

    @Test
    fun aSwitchRowIsOneSwitchWithItsTitleAndState() {
        var checked by mutableStateOf(false)
        show {
            SwitchRow(Icons.Rounded.Celebration, "Festività italiane", checked, onCheckedChange = { checked = it }, subtitle = "Le feste nazionali")
        }
        // The row and its switch are a single element: title, subtitle, role and state together.
        rule.onAllNodes(isToggleable()).assertCountEquals(1)
        rule.onNode(isToggleable() and hasText("Festività italiane") and hasText("Le feste nazionali"))
            .assert(hasRole(Role.Switch))
            .assertIsOff()
            .performClick()
            .assertIsOn()
        assertTrue(checked)
    }

    @Test
    fun weekdaysSayTheirFullNameAndWhetherTheyAreWorkingDays() {
        var selected by mutableStateOf(setOf(1, 2, 3, 4, 5))
        show {
            WeekdayToggles(selected, order = (1..7).toList(), labels = { "x$it" }, onToggle = { d -> selected = if (d in selected) selected - d else selected + d })
        }
        rule.onAllNodes(isToggleable()).assertCountEquals(7)
        val monday = DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val sunday = DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        rule.onNodeWithContentDescription(monday).assert(hasRole(Role.Checkbox)).assertIsOn()
        rule.onNodeWithContentDescription(sunday).assertIsOff().performClick().assertIsOn()
        assertTrue(7 in selected)
        // The short label drawn in the circle is not read on top of the name.
        rule.onAllNodesWithText("x1").assertCountEquals(0)
    }

    @Test
    fun stepperButtonsHaveALabelAndAFullTouchTarget() {
        var value by mutableIntStateOf(3)
        show { StepperRow(Icons.Rounded.EventRepeat, "Anticipo", value, 0..15, onValueChange = { value = it }) }
        rule.onNodeWithContentDescription(res.getString(R.string.a11y_increase, "Anticipo"))
            .assertTouchTargetAtLeast48()
            .performClick()
        rule.onNodeWithContentDescription(res.getString(R.string.a11y_decrease, "Anticipo")).assertTouchTargetAtLeast48()
        assertEquals(4, value)
        // The value is read whole, not one rolling digit at a time.
        rule.onNodeWithText("4").assertIsDisplayed()
    }

    @Test
    fun rollingNumbersAreReadWhole() {
        show { RollingText("08:02") }
        rule.onNodeWithText("08:02").assertIsDisplayed()
        rule.onAllNodesWithText("0").assertCountEquals(0)
    }

    @Test
    fun theDialIsASliderTalkBackCanTurn() {
        var stamped: Int? = null
        show {
            StampDial(actionLabel = "Timbra uscita", icon = Icons.AutoMirrored.Rounded.Logout, nowSeconds = 600 * 60, onStamp = { stamped = it }, nowLabel = "adesso")
        }
        rule.mainClock.autoAdvance = false
        val dial = rule.onNode(hasContentDescription(res.getString(R.string.a11y_dial)))
        dial.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assert(stateContains("10:00"))
        // Five minutes back, as a swipe down with TalkBack would do minute by minute.
        dial.performSemanticsAction(SemanticsActions.SetProgress) { it(-5f) }
        rule.mainClock.advanceTimeBy(1_000)
        dial.assert(stateContains("09:55"))
        rule.onNode(hasText("Timbra uscita") and hasClickAction()).performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(9 * 60 + 55, stamped)
    }

    @Test
    fun theDialButtonsHaveAFullTouchTarget() {
        show { StampDial(actionLabel = "Timbra", icon = Icons.AutoMirrored.Rounded.Logout, nowSeconds = 0, onStamp = {}) }
        rule.onNodeWithContentDescription(Fmt.offset(1)).assertTouchTargetAtLeast48()
        rule.onNodeWithContentDescription(Fmt.offset(-1)).assertTouchTargetAtLeast48()
    }

    @Test
    fun eraseEverythingCanBeDoneWithTalkBack() {
        var erased = false
        show { HoldToConfirmButton(text = "Tieni premuto", holdingText = "Ancora…", onConfirmed = { erased = true }, actionLabel = "cancellare tutto") }
        rule.onNodeWithText("Tieni premuto")
            .assert(hasRole(Role.Button))
            .assert(SemanticsMatcher("long click labelled") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "cancellare tutto" })
            .performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(erased)
    }

    @Test
    fun daysLeftOutOfTheBalanceOpenOneByOne() {
        val today = LocalDate.of(2026, 11, 23)
        val days = (1..5).map { today.minusDays(it * 3L) to DayFix.MISSING_EXIT } + (LocalDate.of(2025, 12, 11) to DayFix.INCONSISTENT)
        var opened: LocalDate? = null
        show { DaysToFixCard(days, today, onOpen = { opened = it }) }
        rule.onNodeWithText(res.getQuantityString(R.plurals.days_to_fix_title, days.size, days.size)).assertIsDisplayed()
        val missing = res.getString(R.string.calendar_missing_exit)
        rule.onAllNodesWithText(missing).assertCountEquals(3)
        rule.onNodeWithText(res.getString(R.string.days_to_fix_more, days.size)).performClick()
        rule.onAllNodesWithText(missing).assertCountEquals(5)
        // A day of another year says so.
        rule.onNode(hasText(Fmt.dayMonthYear(LocalDate.of(2025, 12, 11))) and hasClickAction()).performClick()
        assertEquals(LocalDate.of(2025, 12, 11), opened)
    }

    @Test
    fun aCalendarDaySaysEverythingItsCellShows() {
        val date = LocalDate.of(2026, 11, 16)
        val record = DayRecord(entry = 480, exit = 1050, onCall = true)
        val calendar = WorkCalendar(WorkSettings(), mapOf(date to record))
        val text = cellDescription(res, date, today = date, record, DayType.OFFICE, null, null, delta = 40, onCallOn = true, calendar)
        assertTrue(text, text.startsWith(Fmt.longDate(date)))
        assertTrue(text, res.getString(DayType.OFFICE.label) in text)
        assertTrue(text, res.getString(R.string.a11y_day_on_call) in text)
        assertTrue(text, res.getQuantityString(R.plurals.a11y_minutes, 40, 40) in text)
        assertTrue(text, text.endsWith(res.getString(R.string.relative_today)))
        // Another year is spelled out.
        val old = cellDescription(res, LocalDate.of(2025, 3, 4), today = date, null, null, null, null, null, onCallOn = false, calendar)
        assertTrue(old, "2025" in old)
    }
}
