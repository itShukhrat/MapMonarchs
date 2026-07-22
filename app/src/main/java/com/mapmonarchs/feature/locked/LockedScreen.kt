package com.mapmonarchs.feature.locked

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mapmonarchs.R
import androidx.compose.ui.unit.dp
import com.mapmonarchs.feature.shell.FeatureLock
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.ActionEmphasis
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.LockMotifMark
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.TextMuted

object LockedTags {
    const val STATUS = "locked_status"
    const val WHY = "locked_why"
    const val UNLOCK = "locked_unlock"
    const val PREVIEW = "locked_enter_preview"
    const val COMMITMENT = "locked_commitment"
}

/**
 * The locked surface.
 *
 * PROBLEM SOLVED: a player who opens LEADERBOARD and finds an empty list assumes
 * the app is broken. A player who finds a precise reason and an unlock condition
 * understands the product's shape and where it is going.
 *
 * WHY NOT GENERIC: this is not "Coming soon!" with an illustration. It states
 * three specific things -- why, what unlocks it, what to do instead -- and it is
 * deliberately honest about design decisions that are unflattering (there is
 * nothing to sell; ranking nobody against nobody). A roadmap that only says
 * pleasant things is marketing, not status.
 *
 * WHAT CAN GO WRONG: if these strings go stale after a feature ships, this
 * becomes a lie. Each lock lives on its Route so the compiler forces it to be
 * removed with the gate, not left behind.
 */
@Composable
fun LockedScreen(
    title: String,
    lock: FeatureLock,
    onBack: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    onEnterPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CommandScreen(
        title = title,
        onBack = onBack,
        modifier = modifier,
        // Withheld destinations get the warmer wash; undeployed ones do not.
        // See LockMotif.isWithheld for why that distinction is worth drawing.
        background = if (lock.motif.isWithheld) {
            MapMonarchsBrush.prestige
        } else {
            MapMonarchsBrush.screen
        }
    ) {

        // The motif carries the character of the destination. It replaced a small
        // hollow hexagon that was identical on all six locked screens, which made
        // six genuinely different ideas read as one screen shown six times.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LockMotifMark(motif = lock.motif)

            Row(
                modifier = Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The status word stays plain and stays first. Whatever the motif
                // suggests, the fact is that this is shut.
                Text(
                    text = stringResource(R.string.locked_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                    modifier = Modifier.testTag(LockedTags.STATUS)
                )
            }
        }

        SectionHeader(stringResource(R.string.locked_why))
        BodyNote(stringResource(lock.why), modifier = Modifier.testTag(LockedTags.WHY))

        SectionHeader(stringResource(R.string.locked_unlocks_when))
        BodyNote(stringResource(lock.unlocksWhen), modifier = Modifier.testTag(LockedTags.UNLOCK))

        SectionHeader(stringResource(R.string.locked_for_now))
        BodyNote(stringResource(lock.insteadNow))

        // Only the surfaces that will one day take money carry this. It is set
        // apart from FOR NOW because it is not a workaround for today -- it is
        // the rule the surface will still be bound by after it opens.
        lock.commitment?.let { commitment ->
            SectionHeader(stringResource(R.string.locked_never_sold))
            BodyNote(stringResource(commitment), modifier = Modifier.testTag(LockedTags.COMMITMENT))
        }

        if (onEnterPreview != null) {
            SectionHeader(stringResource(R.string.locked_see_how))
            BodyNote(stringResource(R.string.locked_preview_body))
            CommandAction(
                label = stringResource(R.string.locked_enter_preview),
                onClick = onEnterPreview,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(LockedTags.PREVIEW)
            )
        }

        if (onOpenDiagnostics != null) {
            CommandAction(
                label = stringResource(R.string.locked_open_diagnostics),
                onClick = onOpenDiagnostics,
                // Secondary ONLY when it is competing with ENTER PREVIEW. On the locks
                // that offer no preview this is the sole action on the screen, and a
                // screen whose only action is muted has no primary at all -- which is
                // how MISSIONS shipped for one build, verified by screenshot.
                emphasis = if (onEnterPreview != null) {
                    ActionEmphasis.SECONDARY
                } else {
                    ActionEmphasis.PRIMARY
                },
                modifier = Modifier.padding(top = 16.dp)
            )
        }

    }
}
