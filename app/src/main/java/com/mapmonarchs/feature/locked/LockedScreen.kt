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
import androidx.compose.ui.unit.dp
import com.mapmonarchs.feature.shell.FeatureLock
import com.mapmonarchs.ui.components.BodyNote
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
                    text = "LOCKED",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                    modifier = Modifier.testTag(LockedTags.STATUS)
                )
            }
        }

        SectionHeader("WHY")
        BodyNote(lock.why, modifier = Modifier.testTag(LockedTags.WHY))

        SectionHeader("UNLOCKS WHEN")
        BodyNote(lock.unlocksWhen, modifier = Modifier.testTag(LockedTags.UNLOCK))

        SectionHeader("FOR NOW")
        BodyNote(lock.insteadNow)

        if (onEnterPreview != null) {
            SectionHeader("SEE HOW IT WORKS")
            BodyNote(
                "Preview mode runs the claim loop locally on this device so you can see " +
                    "what holding ground looks like. It is simulated: nothing is sent to " +
                    "the server, nothing is saved, and none of it becomes real territory."
            )
            CommandAction(
                label = "ENTER PREVIEW",
                onClick = onEnterPreview,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag(LockedTags.PREVIEW)
            )
        }

        if (onOpenDiagnostics != null) {
            CommandAction(
                label = "OPEN DIAGNOSTICS",
                onClick = onOpenDiagnostics,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

    }
}
