package com.mapmonarchs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.DisabledFg
import com.mapmonarchs.ui.theme.Obsidian
import com.mapmonarchs.ui.theme.Slate
import com.mapmonarchs.ui.theme.DisabledFg
import com.mapmonarchs.ui.theme.Obsidian
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignEdge
import com.mapmonarchs.ui.theme.SovereignHalo
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary
import com.mapmonarchs.ui.theme.EdgeLit
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.Void
// Bottom clearance for the floating command dock, defined here rather than
// imported from feature.shell so ui.components does not depend up into the
// shell. Tracks the dock geometry: 78dp tall + 12dp inset, plus margin.
private val DOCK_CLEARANCE = 104.dp

/**
 * Every non-map screen uses this.
 *
 * There is no TopAppBar: Material's bar brings its own elevation, its own title
 * scale, and its own back affordance, all of which read as "generic Android".
 * Instead the title is set in the instrument-panel label style and the back
 * control is a word, not a chevron -- consistent with a product whose entire
 * hierarchy is typographic.
 */
@Composable
fun CommandScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MapMonarchsBrush.screen)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextCommand(
                label = "BACK",
                onClick = onBack,
                modifier = Modifier
                    .offset(x = (-10).dp)
                    .testTag(UiTags.BACK)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .testTag(UiTags.SCREEN_TITLE)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                // Clears the floating command dock so the last row of any screen
                // is never trapped under it. The dock is persistent chrome (~90dp:
                // 78dp tall, 12dp inset) drawn over every screen except SIGN IN, and
                // six screens each used to carry their own 40dp spacer that did not
                // clear it -- the same defect the capture button had. One inset here
                // fixes all of them and stops the next screen inheriting the bug.
                .padding(bottom = DOCK_CLEARANCE)
        ) {
            content()
            // Clears the floating command bar. Done HERE rather than in each
            // screen because the bar is global: a per-screen spacer would be the
            // same measurement copied eight times, and the one that got forgotten
            // would trap its last row under the bar with no way to reach it.
            Spacer(modifier = Modifier.height(COMMAND_BAR_CLEARANCE))
        }
    }
}

/**
 * Vertical room reserved at the bottom of every command surface for the floating
 * bar plus its inset. Lives here, next to the only thing that applies it.
 */
private val COMMAND_BAR_CLEARANCE = 104.dp

object UiTags {
    const val BACK = "ui_back"
    const val SCREEN_TITLE = "ui_screen_title"

    const val ERROR_STRIP = "ui_error_strip"
    const val RETRY = "ui_retry"
}

/**
 * A command that is set as text rather than as a filled control.
 *
 * WHY THIS EXISTS: BACK, RESEND CODE, USE A DIFFERENT ADDRESS and EXIT were each
 * a bare `Text` with `.clickable`, which makes the touch target exactly the size
 * of the word. "BACK" at labelMedium is roughly 40x14dp -- a third of the 48dp
 * floor, on the control every single screen depends on to get out. They also
 * announced as plain text, so a screen reader never called them buttons.
 *
 * The padding is what makes the target; the text is only what you see. Keeping
 * the typographic look was deliberate -- a chevron or a filled button here would
 * drag the whole product back toward stock Android.
 */
@Composable
fun TextCommand(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SovereignGlow
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Section divider. A tracked label over a hairline -- no card, no elevation. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 28.dp, bottom = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        // A luminous divider: accent-lit at the left where the label anchors,
        // fading to nothing. Reads as an instrument rule catching light, not a
        // flat grey line.
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to SovereignEdge,
                        0.5f to SlateEdge,
                        1f to Color.Transparent
                    )
                )
        )
    }
}

/**
 * A dashboard panel.
 *
 * The premium refresh's "card" half: SectionHeader draws a rule under a label;
 * this gives the group a BODY with depth and a lit edge, so related readouts read
 * as one instrument rather than loose text on black. The title sits above the
 * panel (tracked, muted) so the eye gets hierarchy before detail.
 *
 * Additive: SectionHeader still exists for a single note or one-line status a
 * full panel would over-format.
 */
@Composable
fun CommandCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.padding(top = 24.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MapMonarchsBrush.card)
                .border(1.dp, EdgeLit, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content
        )
    }
}

/**
 * A labelled value. The workhorse of every settings/identity surface.
 * Value is right-aligned so a column of them scans like an instrument readout.
 */
@Composable
fun StatusRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            // Merged so a screen reader says "Email sign-in: ENABLED" as one fact.
            // Unmerged, Diagnostics alone read out roughly thirty orphaned
            // fragments -- every label and every value announced as unrelated
            // text, in an order that made none of them mean anything.
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = valueColor)
    }
}

/** Explanatory prose. Muted, never competing with the readouts above it. */
@Composable
fun BodyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/**
 * Whether an action can be taken, and if not, why.
 *
 * A sealed type rather than `enabled: Boolean` plus an optional reason string,
 * because an unavailable control with no stated reason is not representable this
 * way. That is the same construction used by Blocker, DeliveryProof and
 * FeatureLock -- in each case the alternative was a boolean that let the
 * explanation go missing, and in each case it eventually did.
 */
sealed interface ActionState {
    data object Ready : ActionState

    /** [reason] is announced to screen readers and is never optional. */
    data class Unavailable(val reason: String) : ActionState
}

/**
 * The primary action.
 *
 * WHY IT IS NO LONGER A SATURATED SLAB: the fill was `Sovereign`, which is
 * 0xFF4C8DFF, which was then also the first entry of TERRITORY_PALETTE. One player in
 * eight owned ground drawn in exactly the colour of every primary button in the
 * app, and on the map screen both are on screen at once. Colour.kt's own rule is
 * that territory must be the most saturated thing on screen; a full-bleed accent
 * button broke that rule and made a control indistinguishable from land.
 *
 * Emphasis now comes from geometry and edge instead: a dark core, a lit border,
 * and a FILLED HEXAGON marking the live action. That hexagon already means "this
 * is the active one" in ChoiceRow and in the command dock indicator, so the
 * product has one selection language rather than three.
 *
 * Unavailable actions stay legible and announce their reason. They do not stay
 * tappable -- unlike the capture button, whose blocker lives on a server the
 * player cannot see, these are gated by something visible directly above them
 * (an empty field). What was missing was never the tap, it was the announcement.
 */
@Composable
fun CommandAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ActionState = ActionState.Ready
) {
    val ready = state is ActionState.Ready
    val shape = RoundedCornerShape(14.dp)
    val haptics = LocalHapticFeedback.current

    val edge = if (ready) Sovereign else SlateEdge
    val labelColor = if (ready) SovereignGlow else DisabledFg

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 52dp floor including padding: this is the largest, most-used control
            // in the product and it is pressed outdoors, one-handed, walking.
            .heightIn(min = 52.dp)
            // A ready action lifts off the surface with an accent-tinted shadow, so
            // the primary move on any screen reads as high-value and tactile. An
            // unavailable one stays flat and grounded -- the depth itself signals
            // "this is the thing to press".
            .then(
                if (ready) Modifier.shadow(12.dp, shape, ambientColor = SovereignHalo, spotColor = SovereignHalo)
                else Modifier
            )
            .clip(shape)
            .background(if (ready) MapMonarchsBrush.actionReady else androidx.compose.ui.graphics.SolidColor(Slate))
            .border(if (ready) 1.5.dp else 1.dp, edge, shape)
            .clickable(
                enabled = ready,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics {
                if (state is ActionState.Unavailable) {
                    disabled()
                    contentDescription = "$label. Unavailable: ${state.reason}"
                }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Non-colour cue for the live state, so emphasis survives greyscale
            // and colour-vision deficiency.
            Box(
                modifier = Modifier
                    // 11dp, not 9: below about 10dp the six sides stop resolving
                    // and it reads as a dot, which loses the only reason it is a
                    // hexagon rather than a bullet.
                    .size(11.dp)
                    .rotate(90f)
                    .clip(HexagonShape)
                    .background(if (ready) Sovereign else Color.Transparent)
                    .border(1.dp, if (ready) Sovereign else DisabledFg, HexagonShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/**
 * Selectable option. Used for tracking mode.
 * Selection is shown by a filled hex mark, not a radio button -- the hexagon is
 * this product's selection language everywhere else, so it is here too.
 */
@Composable
fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // selectable, not clickable: this is a choice among peers, and
            // Role.RadioButton is what makes assistive tech announce WHICH cadence
            // is active. The filled hex is the sighted cue; without this the
            // non-sighted equivalent simply did not exist.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // stateDescription is what actually reaches the accessibility tree:
            // .selectable sets the role, but on a merged node with an explicit
            // contentDescription the selected flag was not surfacing -- verified
            // on device, every cadence row read "not selected". TalkBack now
            // announces the active one, matching the filled-hex cue.
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
                contentDescription = "$label. $detail"
            }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .rotate(90f)
                .clip(HexagonShape)
                .background(if (selected) Sovereign else Color.Transparent)
                .border(1.dp, if (selected) Sovereign else SlateEdge, HexagonShape)
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) TextPrimary else TextMuted
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
