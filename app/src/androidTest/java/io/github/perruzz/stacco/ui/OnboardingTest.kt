package io.github.perruzz.stacco.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.perruzz.stacco.R
import io.github.perruzz.stacco.domain.WorkMode
import io.github.perruzz.stacco.ui.onboarding.OnboardingFlow
import io.github.perruzz.stacco.ui.onboarding.SetupAnswers
import io.github.perruzz.stacco.ui.theme.StaccoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The first setup: every question in its place for fixed hours and for shifts, what TalkBack
 * hears, and every page whole on a 360dp phone with the fonts at 150%, in Italian and English,
 * light and dark.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var finished: SetupAnswers? = null
    private var restores = 0
    private lateinit var res: Resources
    private lateinit var locale: Locale
    private var width: Dp = 0.dp

    /** The setup in [language], [width] wide, with the fonts at [fontScale]. */
    private fun show(language: String = "it", width: Dp = 411.dp, fontScale: Float = 1f, dark: Boolean = false) {
        val activity = rule.activity
        val wanted = Locale.forLanguageTag(language)
        locale = wanted
        // Not setLocale(locale): inside apply that would be the configuration's own locale field.
        val config = Configuration(activity.resources.configuration).apply { setLocale(wanted) }
        val localized = activity.createConfigurationContext(config)
        res = localized.resources
        this.width = width
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides config,
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                StaccoTheme(darkTheme = dark) {
                    Box(Modifier.width(width).fillMaxHeight()) {
                        OnboardingFlow(onFinish = { finished = it }, onRestore = { restores++ })
                    }
                }
            }
        }
    }

    private fun text(id: Int) = res.getString(id)

    private fun button(label: String) = rule.onNode(hasText(label) and hasClickAction())

    /** A control inside the page, which may have to be scrolled to first. */
    private fun tapInPage(label: String) {
        button(label).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun exists(label: String) = rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()

    /** What [assertNothingCut] found, reported all together at the end of the walk. */
    private val problems = mutableListOf<String>()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun assertPage(title: Int) {
        rule.onNode(hasText(text(title)) and isHeading()).assertExists()
    }

    /** The main button of the page; "Not now" where it would open the system's permission dialog. */
    private fun forward() {
        when {
            exists(text(R.string.ob_enable_notifications)) -> button(text(R.string.ob_skip)).performClick()
            exists(text(R.string.ob_start)) -> button(text(R.string.ob_start)).performClick()
            exists(text(R.string.ob_enter)) -> button(text(R.string.ob_enter)).performClick()
            else -> button(text(R.string.ob_next)).performClick()
        }
        rule.waitForIdle()
    }

    /** No text of the page cut, squeezed out of the screen or broken in the middle of a word. */
    private fun assertNothingCut(where: String) {
        val screen = with(rule.density) { width.toPx() }
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        if (nodes.isEmpty()) problems += "$where: no text found"
        for (node in nodes) {
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.firstOrNull() ?: continue
            val value = layout.layoutInput.text.text
            if (value.isBlank()) continue
            // The paragraph is laid out at the width available, the text drawn as wide as its lines:
            // cut is a line wider than the text, or lines taller than it (hasVisualOverflow compares
            // the two widths, so it says "cut" for any line shorter than the room it has).
            val widest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }
            val paragraph = layout.multiParagraph
            if (widest > layout.size.width + 1f || paragraph.didExceedMaxLines || paragraph.height > layout.size.height + 1f) {
                problems += "$where: «$value» is cut (drawn ${layout.size}, lines ${widest}x${paragraph.height})"
            }
            for (line in 0 until layout.lineCount) {
                if (layout.isLineEllipsized(line)) problems += "$where: «$value» ends with …"
                if (line < layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    val before = value.getOrNull(end - 1)
                    val after = value.getOrNull(end)
                    if (before != null && after != null && before.isLetter() && after.isLetter()) {
                        problems += "$where: «$value» broken inside a word, line ${line + 1}"
                    }
                }
            }
            val bounds = node.boundsInRoot
            if (bounds.left < -1f || bounds.right > screen + 1f) problems += "$where: «$value» goes out of the screen ($bounds)"
        }
    }

    /** Goes through the whole setup checking every page (with on-call duty on, to see its rules too). */
    private fun walkAndCheck(shifts: Boolean) {
        var page = 0
        while (finished == null) {
            assertNothingCut("page ${page + 1}")
            if (shifts && exists(text(R.string.mode_shifts)) && exists(text(R.string.mode_fixed))) {
                tapInPage(text(R.string.mode_shifts))
                assertNothingCut("page ${page + 1}, shifts")
            }
            if (exists(text(R.string.ob_on_call_yes))) {
                tapInPage(text(R.string.ob_on_call_yes))
                assertNothingCut("page ${page + 1}, on call")
            }
            forward()
            page++
            assertTrue("the setup never ends", page < 20)
        }
        assertTrue(problems.joinToString("\n", prefix = "\n"), problems.isEmpty())
        assertEquals(if (shifts) 11 else 12, page)
    }

    @Test
    fun theFixedHoursSetupAsksEveryQuestionInOrder() {
        show()
        rule.onNodeWithContentDescription(res.getString(R.string.a11y_step, 1, 12)).assertExists()
        button(text(R.string.ob_restore)).performClick()
        assertEquals(1, restores)
        forward()
        val titles = listOf(
            R.string.ob_name_title, R.string.ob_mode_title, R.string.ob_work_title, R.string.ob_days_title,
            R.string.ob_rules_title, R.string.ob_bank_title, R.string.ob_vacation_title, R.string.ob_smart_title,
            R.string.ob_on_call_title, R.string.ob_notif_title,
        )
        for (title in titles) {
            assertPage(title)
            when (title) {
                R.string.ob_work_title -> rule.onNodeWithContentDescription(res.getString(R.string.a11y_step, 4, 12)).assertExists()
                // Saturday becomes a working day (its circle says the whole name of the day).
                R.string.ob_days_title -> rule.onNodeWithContentDescription(DayOfWeek.SATURDAY.getDisplayName(TextStyle.FULL, locale)).performScrollTo().performClick()
                // The break and the rounding of the company.
                R.string.ob_rules_title -> rule.onNodeWithText(text(R.string.settings_rounding).uppercase(locale)).assertExists()
                R.string.ob_on_call_title -> {
                    tapInPage(text(R.string.ob_on_call_yes))
                    rule.onNodeWithText(text(R.string.on_call_rule_credit)).assertExists()
                }
            }
            when (title) {
                // One tap skips the numbers.
                R.string.ob_bank_title -> button(text(R.string.ob_bank_skip)).performClick()
                R.string.ob_vacation_title -> button(text(R.string.ob_vacation_skip)).performClick()
                else -> forward()
            }
            rule.waitForIdle()
        }
        assertPage(R.string.ob_done_title)
        rule.onNodeWithText(text(R.string.ob_summary_on_call)).assertExists()
        forward()
        val answers = finished!!
        assertEquals(WorkMode.FIXED, answers.data.work.mode)
        assertEquals(setOf(1, 2, 3, 4, 5, 6), answers.data.work.workDays)
        assertTrue(answers.data.onCall.enabled)
        assertEquals(0, answers.balance)
        assertNull(answers.vacationLeft)
        assertFalse(answers.data.vacation.tracked)
    }

    @Test
    fun theShiftSetupAsksForShiftsAndRoundingInstead() {
        show(language = "en")
        forward()
        forward()
        assertPage(R.string.ob_mode_title)
        // Two radio buttons; the chosen one says so.
        rule.onAllNodes(hasRole(Role.RadioButton)).assertCountEquals(2)
        button(text(R.string.mode_fixed)).assertIsSelected()
        tapInPage(text(R.string.mode_shifts))
        button(text(R.string.mode_shifts)).assertIsSelected()
        button(text(R.string.mode_fixed)).assertIsNotSelected()
        // One page fewer: the shifts take the place of hours, days and break.
        rule.onNodeWithContentDescription(res.getString(R.string.a11y_step, 3, 11)).assertExists()
        forward()
        val titles = listOf(
            R.string.ob_shifts_title, R.string.ob_rounding_title, R.string.ob_bank_title, R.string.ob_vacation_title,
            R.string.ob_smart_title, R.string.ob_on_call_title, R.string.ob_notif_title,
        )
        for (title in titles) {
            assertPage(title)
            forward()
        }
        assertPage(R.string.ob_done_title)
        forward()
        val answers = finished!!
        assertEquals(WorkMode.SHIFTS, answers.data.work.mode)
        assertTrue(answers.data.work.shifts.isNotEmpty())
        // On-call duty stays off unless chosen.
        assertFalse(answers.data.onCall.enabled)
    }

    @Test
    fun talkBackHearsThePermissionsAndWhetherTheyAreOn() {
        show()
        // Welcome, name, mode, hours, days, rules, bank, vacation, remote work, on call.
        repeat(10) { forward() }
        assertPage(R.string.ob_notif_title)
        val states = setOf(text(R.string.a11y_permission_on), text(R.string.a11y_permission_off))
        val row = rule.onNode(hasText(text(R.string.ob_perm_notifications))).fetchSemanticsNode()
        assertTrue(row.config.getOrNull(SemanticsProperties.StateDescription) in states)
        rule.onNodeWithContentDescription(text(R.string.action_back)).assertExists()
    }

    @Test
    fun fixedHoursFitANarrowPhoneWithLargeFontsInItalian() {
        show(language = "it", width = 360.dp, fontScale = 1.5f)
        walkAndCheck(shifts = false)
    }

    @Test
    fun shiftsFitANarrowPhoneWithLargeFontsInItalianDark() {
        show(language = "it", width = 360.dp, fontScale = 1.5f, dark = true)
        walkAndCheck(shifts = true)
    }

    @Test
    fun fixedHoursFitANarrowPhoneWithLargeFontsInEnglishDark() {
        show(language = "en", width = 360.dp, fontScale = 1.5f, dark = true)
        walkAndCheck(shifts = false)
    }

    @Test
    fun shiftsFitANarrowPhoneWithLargeFontsInEnglish() {
        show(language = "en", width = 360.dp, fontScale = 1.5f)
        walkAndCheck(shifts = true)
    }
}
