package com.mapmonarchs.feature.records

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mapmonarchs.R
import com.mapmonarchs.domain.model.PlayerRecords
import com.mapmonarchs.ui.components.BodyNote
import com.mapmonarchs.ui.components.CommandCard
import com.mapmonarchs.ui.components.CommandScreen
import com.mapmonarchs.ui.components.SectionHeader
import com.mapmonarchs.ui.components.StatusRow
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordsTags {
    const val ROOT = "records_root"
    const val EMPTY = "records_empty"
}

/**
 * The intelligence log.
 *
 * It reports game history and nothing else -- claims, holds, streaks. There is
 * no distance, duration, or pace here, because those are a fitness app's numbers
 * and this is a strategy game's ledger. Every row is a fact the server writes
 * from an accepted claim.
 *
 * TODAY IT IS EMPTY, AND IT SAYS SO ONCE, LOUDLY, rather than six times quietly.
 * A stack of "—" rows would make the screen read as broken; a single honest
 * banner explaining that history begins at the first claim reads as designed.
 * Below the banner the log still lists the metrics it WILL hold, with unknown
 * values, so the player learns what the game keeps track of -- a preview of the
 * ledger, not a fabrication of its contents.
 */
@Composable
fun RecordsScreen(
    records: PlayerRecords,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val unknown = stringResource(R.string.profile_unknown)

    fun known(v: String) = v != unknown

    CommandScreen(
        title = stringResource(R.string.route_records),
        onBack = onBack,
        modifier = modifier.testTag(RecordsTags.ROOT)
    ) {
        if (records.isEmpty) {
            SectionHeader(stringResource(R.string.records_none_header))
            BodyNote(
                stringResource(R.string.records_none_body),
                modifier = Modifier.padding(top = 4.dp).testTag(RecordsTags.EMPTY)
            )
        }

        val firstCapture = records.firstCaptureEpochMillis?.let { dateFmt.format(Date(it)) } ?: unknown
        val totalCaptures = records.totalCaptures?.toString() ?: unknown
        val hexesEver = records.totalHexesEverHeld?.toString() ?: unknown
        val peakHeld = records.peakHexesHeld?.toString() ?: unknown
        val currentStreak = records.currentStreakDays
            ?.let { pluralStringResource(R.plurals.records_days, it, it) } ?: unknown
        val longestStreak = records.longestStreakDays
            ?.let { pluralStringResource(R.plurals.records_days, it, it) } ?: unknown

        CommandCard(title = stringResource(R.string.records_field)) {
            StatusRow(stringResource(R.string.records_first_capture), firstCapture,
                valueColor = if (known(firstCapture)) SovereignGlow else TextMuted)
            StatusRow(stringResource(R.string.records_total_captures), totalCaptures,
                valueColor = if (known(totalCaptures)) SovereignGlow else TextMuted)
            StatusRow(stringResource(R.string.records_hexes_ever), hexesEver,
                valueColor = if (known(hexesEver)) SovereignGlow else TextMuted)
            StatusRow(stringResource(R.string.records_peak_held), peakHeld,
                valueColor = if (known(peakHeld)) SovereignGlow else TextMuted)
        }
        CommandCard(title = stringResource(R.string.records_endurance)) {
            StatusRow(stringResource(R.string.records_current_streak), currentStreak,
                valueColor = if (known(currentStreak)) SovereignGlow else TextMuted)
            StatusRow(stringResource(R.string.records_longest_streak), longestStreak,
                valueColor = if (known(longestStreak)) SovereignGlow else TextMuted)
        }
    }
}
