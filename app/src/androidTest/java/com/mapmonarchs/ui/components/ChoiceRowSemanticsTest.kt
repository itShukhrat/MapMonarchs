package com.mapmonarchs.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmonarchs.ui.theme.MapMonarchsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ChoiceRow's selection state must reach assistive tech.
 *
 * WHY THIS IS AN INSTRUMENTED SEMANTICS TEST, NOT A uiautomator CHECK: the filled
 * hex is the sighted cue for which tracking cadence is active. The non-sighted
 * equivalent is the Selected semantics property plus a stateDescription, and a
 * device dump proved they were NOT surfacing -- every cadence row read "not
 * selected" in uiautomator even while one was visibly chosen.
 *
 * uiautomator's XML `selected` attribute maps to legacy
 * AccessibilityNodeInfo.isSelected(), which Compose does not populate from its
 * semantics, and it does not expose stateDescription at all -- so it could never
 * have proven this either way. assertIsSelected() reads the actual Compose
 * SemanticsProperties.Selected that TalkBack consumes. This is the authoritative
 * source, and it locks the fix against regression.
 */
@RunWith(AndroidJUnit4::class)
class ChoiceRowSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun threeChoices(selectedLabel: String) {
        rule.setContent {
            MapMonarchsTheme {
                Column {
                    listOf("PRECISE", "BALANCED", "CONSERVATIVE").forEach { label ->
                        ChoiceRow(
                            label = label,
                            detail = "detail for $label",
                            selected = label == selectedLabel,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theSelectedChoiceExposesItsSelectedState() {
        threeChoices(selectedLabel = "BALANCED")
        // onNodeWithText finds the merged selectable node carrying the label.
        rule.onNodeWithText("BALANCED", substring = true).assertIsSelected()
    }

    @Test
    fun theUnselectedChoicesReportNotSelected() {
        threeChoices(selectedLabel = "BALANCED")
        rule.onNodeWithText("PRECISE", substring = true).assertIsNotSelected()
        rule.onNodeWithText("CONSERVATIVE", substring = true).assertIsNotSelected()
    }

    @Test
    fun selectionFollowsWhichChoiceIsActive() {
        // A different active choice must move the selected state with it -- the
        // property is not pinned to a position.
        threeChoices(selectedLabel = "CONSERVATIVE")
        rule.onNodeWithText("CONSERVATIVE", substring = true).assertIsSelected()
        rule.onNodeWithText("BALANCED", substring = true).assertIsNotSelected()
    }
}
