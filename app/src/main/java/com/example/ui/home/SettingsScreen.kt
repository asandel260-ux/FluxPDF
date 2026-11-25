package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.data.ReaderPreferences
import com.example.data.ReadingColorMode
import com.example.data.ScrollMode
import com.example.engine.HardwareTierPreference

@Composable
fun SettingsScreen(
    preferences: ReaderPreferences,
    diskCacheKb: Long,
    onSetAppThemeMode: (AppThemeMode) -> Unit,
    onSetReadingColorMode: (ReadingColorMode) -> Unit,
    onSetScrollMode: (ScrollMode) -> Unit,
    onSetAutoCrop: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetShowTelemetryHud: (Boolean) -> Unit,
    onSetVolumeKeyNav: (Boolean) -> Unit,
    onSetReflowFontScale: (Float) -> Unit,
    onSetHardwareTierOverride: (HardwareTierPreference) -> Unit,
    onClearCaches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CONFIGURATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Visual & Optical Mode Section
        item {
            SettingsSectionCard(title = "APPEARANCE & OPTICAL PIPELINE") {
                Text(
                    text = "Interface Theme",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.appThemeMode == mode,
                            onClick = { onSetAppThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}"),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Default Reading Optical Theme",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReadingColorMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.readingColorMode == mode,
                            onClick = { onSetReadingColorMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        // Reading Physics & Accessibility
        item {
            SettingsSectionCard(title = "READING PHYSICS & ACCESSIBILITY") {
                Text(
                    text = "Default Page Layout",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScrollMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.scrollMode == mode,
                            onClick = { onSetScrollMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                ToggleSettingRow(
                    title = "Auto Margin Crop",
                    subtitle = "Detects and trims blank borders on academic papers and dense PDFs",
                    checked = preferences.autoCropMargins,
                    onCheckedChange = onSetAutoCrop,
                    testTag = "setting_auto_crop",
                )
                ToggleSettingRow(
                    title = "Keep Screen Awake",
                    subtitle = "Prevents screen dimming while actively reading",
                    checked = preferences.keepScreenOn,
                    onCheckedChange = onSetKeepScreenOn,
                    testTag = "setting_keep_awake",
                )
                ToggleSettingRow(
                    title = "Volume Key Page Navigation",
                    subtitle = "Use hardware volume rocker to step forward and backward",
                    checked = preferences.volumeKeyNavigation,
                    onCheckedChange = onSetVolumeKeyNav,
                    testTag = "setting_volume_nav",
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Reflow Typography Scale",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${(preferences.reflowFontScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = preferences.reflowFontScale,
                    onValueChange = onSetReflowFontScale,
                    valueRange = 0.85f..1.80f,
                )
            }
        }

        // Engine Performance & Cache
        item {
            SettingsSectionCard(title = "ENGINE & RESOURCE BUDGET") {
                Text(
                    text = "Hardware Scaling Tier",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HardwareTierPreference.entries.forEach { tier ->
                        FilterChip(
                            selected = preferences.hardwareTierOverride == tier,
                            onClick = { onSetHardwareTierOverride(tier) },
                            label = { Text(tier.label) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                ToggleSettingRow(
                    title = "Live Telemetry HUD in Reader",
                    subtitle = "Overlay real-time render latency (ms) and LRU cache MB while reading",
                    checked = preferences.showTelemetryHud,
                    onCheckedChange = onSetShowTelemetryHud,
                    testTag = "setting_telemetry_hud",
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Disk Thumbnail & Export Cache",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$diskCacheKb KB currently stored on device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onClearCaches,
                        modifier = Modifier.testTag("clear_cache_button"),
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
