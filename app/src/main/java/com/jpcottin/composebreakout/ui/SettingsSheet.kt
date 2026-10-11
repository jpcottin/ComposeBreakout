package com.jpcottin.composebreakout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jpcottin.composebreakout.R
import com.jpcottin.composebreakout.theme.focusRing

/** What the Settings glasses row can say. */
enum class GlassesState { NONE, CONNECTED, ACTIVE }

/**
 * Settings as a Material 3 bottom sheet. Each row is one switch with a title
 * and a hint; the whole row toggles, and each row carries the focus ring so
 * the D-pad can walk it on a TV.
 */
@Composable
fun SettingsSheet(
    settings: Settings,
    onSoundChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    glasses: GlassesState = GlassesState.NONE,
    onGlassesToggle: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SettingsContent(settings, onSoundChange, onAutoPlayChange, onDismiss, glasses = glasses, onGlassesToggle = onGlassesToggle)
    }
}

/**
 * Settings on a wide window (a tablet, a TV, an XR panel, a desktop
 * window): a centred dialog card instead of a sheet sliding up a phone.
 */
@Composable
fun SettingsDialog(
    settings: Settings,
    onSoundChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    glasses: GlassesState = GlassesState.NONE,
    onGlassesToggle: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        SettingsCard(settings, onSoundChange, onAutoPlayChange, onDismiss, glasses = glasses, onGlassesToggle = onGlassesToggle)
    }
}

/** The dialog's card, on its own so previews and tests can show it flat. */
@Composable
fun SettingsCard(
    settings: Settings,
    onSoundChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    glasses: GlassesState = GlassesState.NONE,
    onGlassesToggle: () -> Unit = {},
) {
    Surface(
        modifier = modifier.width(480.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        SettingsContent(
            settings, onSoundChange, onAutoPlayChange, onDone,
            modifier = Modifier.padding(top = 16.dp),
            glasses = glasses, onGlassesToggle = onGlassesToggle,
        )
    }
}

/** The sheet's content, on its own so previews and tests can show it flat. */
@Composable
fun SettingsContent(
    settings: Settings,
    onSoundChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    glasses: GlassesState = GlassesState.NONE,
    onGlassesToggle: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SettingRow(
            icon = Icons.Rounded.VolumeUp,
            title = stringResource(R.string.settings_sound),
            hint = stringResource(R.string.settings_sound_hint),
            checked = settings.soundEnabled,
            onCheckedChange = onSoundChange,
        )
        SettingRow(
            icon = Icons.Rounded.SmartToy,
            title = stringResource(R.string.settings_auto_play),
            hint = stringResource(R.string.settings_auto_play_hint),
            checked = settings.autoPlay,
            onCheckedChange = onAutoPlayChange,
        )
        GlassesRow(glasses, onGlassesToggle)
        Spacer(Modifier.height(16.dp))
        val doneInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = onDone,
            interactionSource = doneInteraction,
            modifier = Modifier
                .align(Alignment.End)
                .focusRing(doneInteraction, RoundedCornerShape(50)),
        ) {
            Text(stringResource(R.string.settings_done))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(interaction, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * The AI Glasses: with a pair connected, PLAY hands the game to the
 * glasses' projected display; while it plays there, PHONE brings it back.
 * With nothing paired the row is informational.
 */
@Composable
private fun GlassesRow(state: GlassesState, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    val enabled = state != GlassesState.NONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(interaction, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onToggle,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_glasses), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    when (state) {
                        GlassesState.NONE -> R.string.settings_glasses_none
                        GlassesState.CONNECTED -> R.string.settings_glasses_connected
                        GlassesState.ACTIVE -> R.string.settings_glasses_active
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                when (state) {
                    GlassesState.NONE -> R.string.settings_glasses_unavailable
                    GlassesState.CONNECTED -> R.string.settings_glasses_play
                    GlassesState.ACTIVE -> R.string.settings_glasses_phone
                },
            ).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
