package com.wedo.schedule.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wedo.schedule.ui.theme.WedoApple
import com.wedo.schedule.ui.theme.WedoAppleType
import com.wedo.schedule.ui.theme.WedoTheme
import com.wedo.schedule.ui.theme.noRippleClickable

@Composable
fun WedoSegmentedSetting(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = WedoAppleType.body(), color = WedoTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            WedoSegmented(options = options, selected = selected, onSelect = onSelect)
        }
        if (showDivider) WedoSettingDivider()
    }
}

@Composable
fun WedoToggleSetting(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String = "",
    showDivider: Boolean = true
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = WedoAppleType.body(), color = WedoTheme.colors.onSurface)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = WedoAppleType.footnote(), color = WedoTheme.colors.onSurfaceVariant)
                }
            }
            WedoToggle(checked = checked, onCheckedChange = onChange)
        }
        if (showDivider) WedoSettingDivider()
    }
}

@Composable
fun WedoSliderSetting(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = WedoAppleType.body(), color = WedoTheme.colors.onSurface)
                Text(valueText, style = WedoAppleType.callout(), color = WedoApple.accent)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                onValueChangeFinished = onFinished,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = WedoApple.accent,
                    activeTrackColor = WedoApple.accent,
                    inactiveTrackColor = WedoTheme.colors.surfaceVariant
                )
            )
        }
        if (showDivider) WedoSettingDivider()
    }
}

@Composable
fun WedoChoiceSetting(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            Modifier.fillMaxWidth().noRippleClickable(onClick).heightIn(min = 44.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = WedoAppleType.body(), color = WedoTheme.colors.onSurface)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = WedoAppleType.footnote(), color = WedoTheme.colors.onSurfaceVariant)
                }
            }
            if (selected) {
                Icon(Icons.Outlined.Check, null, tint = WedoApple.accentIcon, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) WedoSettingDivider()
    }
}

@Composable
fun WedoSettingDivider() {
    HorizontalDivider(
        Modifier.padding(start = 14.dp),
        thickness = 0.5.dp,
        color = WedoTheme.colors.outlineVariant
    )
}

@Composable
fun WedoSettingFooter(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 18.dp),
        style = WedoAppleType.footnote(),
        color = WedoTheme.colors.onSurfaceVariant
    )
}
