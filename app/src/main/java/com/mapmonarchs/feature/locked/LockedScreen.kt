package com.mapmonarchs.feature.locked

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mapmonarchs.feature.shell.FeatureLock
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandAction
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.HexagonShape
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.TextMuted

object LockedTags {
    const val STATUS = "locked_status"
    const val WHY = "locked_why"
    const val UNLOCK = "locked_unlock"
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
    modifier: Modifier = Modifier
) {
    CommandScreen(title = title, onBack = onBack, modifier = modifier) {

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hollow hex: the same shape used for territory you do not hold, and
            // for identity you do not have. Unowned is unowned.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(HexagonShape)
                    .background(Color.Transparent)
                    .border(1.dp, SlateEdge, HexagonShape)
            )
            Text(
                text = "LOCKED",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .testTag(LockedTags.STATUS)
            )
        }

        SectionHeader("WHY")
        BodyNote(lock.why, modifier = Modifier.testTag(LockedTags.WHY))

        SectionHeader("UNLOCKS WHEN")
        BodyNote(lock.unlocksWhen, modifier = Modifier.testTag(LockedTags.UNLOCK))

        SectionHeader("FOR NOW")
        BodyNote(lock.insteadNow)

        if (onOpenDiagnostics != null) {
            CommandAction(
                label = "OPEN DIAGNOSTICS",
                onClick = onOpenDiagnostics,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Box(modifier = Modifier.padding(bottom = 40.dp))
    }
}