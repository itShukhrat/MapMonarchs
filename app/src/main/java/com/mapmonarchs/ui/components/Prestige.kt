package com.mapmonarchs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmonarchs.ui.theme.EdgeLit
import com.mapmonarchs.ui.theme.MapMonarchsBrush
import com.mapmonarchs.ui.theme.Obsidian
import com.mapmonarchs.ui.theme.SlateEdge
import com.mapmonarchs.ui.theme.Sovereign
import com.mapmonarchs.ui.theme.SovereignGlow
import com.mapmonarchs.ui.theme.SovereignHalo
import com.mapmonarchs.ui.theme.TextMuted
import com.mapmonarchs.ui.theme.TextPrimary

/**
 * A tier / standing pill.
 *
 * WHY IT IS NOT A COLOURED CHIP: the same rule CommandAction is built around --
 * the accent is 0xFF4C8DFF, which is also TERRITORY_PALETTE[0], so a saturated
 * accent fill would read as ground, not status. Emphasis therefore comes from
 * GEOMETRY and EDGE: an active tier gets a lit border, a filled hex mark, and a
 * low accent halo; an inactive one is a quiet hollow mark on a grounded surface.
 * That is the one selection language the dock, ChoiceRow and CommandAction all
 * already speak, applied to "what you are" instead of "what you pressed".
 *
 * PREMIUM WITHOUT PUNISHING FREE. Active feels elevated; inactive still reads as
 * a deliberate, finished state rather than a broken or greyed-out one -- a free
 * player is not made to feel the screen is missing something.
 */
@Composable
fun TierBadge(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val edge = if (active) Sovereign else SlateEdge
    val fg = if (active) SovereignGlow else TextMuted
    Row(
        modifier = modifier
            .then(
                if (active) {
                    Modifier.shadow(8.dp, shape, ambientColor = SovereignHalo, spotColor = SovereignHalo)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(if (active) MapMonarchsBrush.actionReady else SolidColor(Obsidian))
            .border(if (active) 1.5.dp else 1.dp, edge, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 11dp, not smaller: below ~10dp the six sides stop resolving and the mark
        // reads as a dot -- the same floor CommandAction documents.
        Box(
            modifier = Modifier
                .size(11.dp)
                .rotate(90f)
                .clip(HexagonShape)
                .background(if (active) Sovereign else Color.Transparent)
                .border(1.dp, edge, HexagonShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(start = 9.dp)
        )
    }
}

/**
 * A single instrument readout: a big number over a tracked label, on a raised,
 * lit-edged panel.
 *
 * The number uses the display scale with tabular figures -- Type.kt's own note is
 * that "numbers are the emotional payload of a territory game", so held ground and
 * standing get shown as instruments, not list rows.
 *
 * UNKNOWN IS NOT ZERO, AND NOT SET BIG. An unknown value renders small and muted,
 * for the reason Holdings documents: a lone em dash at display size is a floating
 * bar that reads as a rendering fault. Unknown is a STATE, so it is set at label
 * weight; a number earns the display scale because a number is a quantity. The
 * caller passes [unknown] so this distinction is made once, here.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    unknown: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MapMonarchsBrush.card)
            .border(1.dp, EdgeLit, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .semantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = value,
            style = if (unknown) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMedium,
            color = if (unknown) TextMuted else valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
