package com.uplb.punla.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import com.uplb.punla.data.BackgroundStyle
import com.uplb.punla.data.BackupManager
import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.FontChoice
import com.uplb.punla.data.ThemePreset
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.Palettes
import com.uplb.punla.ui.theme.PunlaBody
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.ui.theme.LocalPunlaPalette
import org.json.JSONArray
import java.time.DayOfWeek
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(vm: PunlaViewModel) {
    var userNameInput by remember { mutableStateOf(vm.userName) }
    var budgetInput by remember {
        mutableStateOf(vm.monthlyBudget.let {
            if (it > 0) it.toInt().toString() else ""
        })
    }
    var targetInput by remember { mutableStateOf(vm.chedTarget?.let { "%.2f".format(it) } ?: "") }
    var weeklyBudgetInput by remember {
        mutableStateOf(vm.weeklyBudgetOverride?.let { if (it > 0) it.toInt().toString() else "" } ?: "")
    }

    val archives by vm.archives.collectAsState()

    var showArchiveConfirm by remember { mutableStateOf(false) }
    var archiveLabel by remember { mutableStateOf("") }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val backupResult = vm.backupResult

    var showCustomColorDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it } }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = PunlaDisplay),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
        }

        // Profile Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "PROFILE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    PunlaField(
                        "Display Name",
                        userNameInput,
                        {
                            userNameInput = it
                            vm.updateUserName(it)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Notifications Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "NOTIFICATIONS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Deadline Reminders", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = vm.notificationsEnabled,
                            onCheckedChange = { vm.toggleNotifications(it) }
                        )
                    }
                    Text(
                        "Daily checks for deadlines due within 3 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Appearance Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "APPEARANCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose a color palette, or pick your own accent color.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PresetSwatch(
                            label = "Field Notebook",
                            swatchColor = Palettes.FieldNotebook.leaf,
                            selected = vm.themePreset == ThemePreset.FIELD_NOTEBOOK,
                            onClick = { vm.updateThemePreset(ThemePreset.FIELD_NOTEBOOK) }
                        )
                        PresetSwatch(
                            label = "Ocean",
                            swatchColor = Palettes.Ocean.leaf,
                            selected = vm.themePreset == ThemePreset.OCEAN,
                            onClick = { vm.updateThemePreset(ThemePreset.OCEAN) }
                        )
                        PresetSwatch(
                            label = "Sunset",
                            swatchColor = Palettes.Sunset.leaf,
                            selected = vm.themePreset == ThemePreset.SUNSET,
                            onClick = { vm.updateThemePreset(ThemePreset.SUNSET) }
                        )
                        PresetSwatch(
                            label = "Orchid",
                            swatchColor = Palettes.Orchid.leaf,
                            selected = vm.themePreset == ThemePreset.ORCHID,
                            onClick = { vm.updateThemePreset(ThemePreset.ORCHID) }
                        )
                        PresetSwatch(
                            label = "Slate",
                            swatchColor = Palettes.Slate.leaf,
                            selected = vm.themePreset == ThemePreset.SLATE,
                            onClick = { vm.updateThemePreset(ThemePreset.SLATE) }
                        )
                        PresetSwatch(
                            label = "Custom",
                            swatchColor = vm.customSeedColor?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.outline,
                            selected = vm.themePreset == ThemePreset.CUSTOM,
                            onClick = { showCustomColorDialog = true }
                        )
                    }
                }
            }
        }

        // Background Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "BACKGROUND",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Applies behind the app and on the home-screen widgets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackgroundStyleOptionRow(
                            label = "Minimal",
                            description = "A flat, distraction-free fill.",
                            selected = vm.backgroundStyle == BackgroundStyle.MINIMAL,
                            onClick = { vm.updateBackgroundStyle(BackgroundStyle.MINIMAL) }
                        )
                        BackgroundStyleOptionRow(
                            label = "Ambient",
                            description = "Soft tinted blobs, slowly drifting.",
                            selected = vm.backgroundStyle == BackgroundStyle.AMBIENT,
                            onClick = { vm.updateBackgroundStyle(BackgroundStyle.AMBIENT) }
                        )
                        BackgroundStyleOptionRow(
                            label = "Starfield",
                            description = "A quiet field of twinkling stars.",
                            selected = vm.backgroundStyle == BackgroundStyle.STARFIELD,
                            onClick = { vm.updateBackgroundStyle(BackgroundStyle.STARFIELD) }
                        )
                        BackgroundStyleOptionRow(
                            label = "Paper Grain",
                            description = "A static notebook-paper texture. No animation.",
                            selected = vm.backgroundStyle == BackgroundStyle.PAPER_GRAIN,
                            onClick = { vm.updateBackgroundStyle(BackgroundStyle.PAPER_GRAIN) }
                        )
                        BackgroundStyleOptionRow(
                            label = "Rain",
                            description = "Thin streaks falling at a steady rate.",
                            selected = vm.backgroundStyle == BackgroundStyle.RAIN,
                            onClick = { vm.updateBackgroundStyle(BackgroundStyle.RAIN) }
                        )
                    }
                }
            }
        }

        // Font Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "FONT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose the typeface used across the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontOptionRow(
                            label = "Default",
                            description = "Fraunces headings, Inter body — the original look.",
                            previewFamily = PunlaDisplay,
                            selected = vm.fontChoice == FontChoice.DEFAULT,
                            onClick = { vm.updateFontChoice(FontChoice.DEFAULT) }
                        )
                        FontOptionRow(
                            label = "Sans",
                            description = "Inter throughout, for a cleaner, uniform look.",
                            previewFamily = PunlaBody,
                            selected = vm.fontChoice == FontChoice.SANS,
                            onClick = { vm.updateFontChoice(FontChoice.SANS) }
                        )
                        FontOptionRow(
                            label = "Serif",
                            description = "Fraunces throughout, for a more editorial feel.",
                            previewFamily = PunlaDisplay,
                            selected = vm.fontChoice == FontChoice.SERIF,
                            onClick = { vm.updateFontChoice(FontChoice.SERIF) }
                        )
                        FontOptionRow(
                            label = "Mono",
                            description = "IBM Plex Mono throughout, for a technical feel.",
                            previewFamily = PunlaMono,
                            selected = vm.fontChoice == FontChoice.MONO,
                            onClick = { vm.updateFontChoice(FontChoice.MONO) }
                        )
                        FontOptionRow(
                            label = "System",
                            description = "Use your device's default font.",
                            previewFamily = FontFamily.Default,
                            selected = vm.fontChoice == FontChoice.SYSTEM,
                            onClick = { vm.updateFontChoice(FontChoice.SYSTEM) }
                        )
                    }
                }
            }
        }

        // Pomodoro Section — roadmap Pomodoro 1.5, durations linked out from
        // the timer screen to keep that screen's layout uncluttered.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "POMODORO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tune how long focus blocks and breaks run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PunlaField(
                            "Focus (min)",
                            vm.pomodoroWorkMinutes.toString(),
                            { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { vm.updatePomodoroWorkMinutes(it.coerceIn(1, 180)) } },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        PunlaField(
                            "Short break",
                            vm.pomodoroShortBreakMinutes.toString(),
                            { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { vm.updatePomodoroShortBreakMinutes(it.coerceIn(1, 60)) } },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        PunlaField(
                            "Long break",
                            vm.pomodoroLongBreakMinutes.toString(),
                            { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { vm.updatePomodoroLongBreakMinutes(it.coerceIn(1, 90)) } },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PunlaField(
                        "Cycles before a long break",
                        vm.pomodoroCyclesBeforeLongBreak.toString(),
                        { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { vm.updatePomodoroCyclesBeforeLongBreak(it.coerceIn(1, 12)) } },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-start next phase", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(
                                "Skip the tap between focus and break.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vm.pomodoroAutoStartNext,
                            onCheckedChange = { vm.updatePomodoroAutoStartNext(it) }
                        )
                    }
                }
            }
        }

        // Study Goals Section — roadmap Study Habits 2.1, same numeric-field
        // pattern as the Pomodoro card just above.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "STUDY GOALS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How much focus time counts as a good day or week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PunlaField(
                            "Daily goal (min)",
                            vm.dailyStudyGoalMinutes.toString(),
                            { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { vm.updateDailyStudyGoal(it.coerceIn(5, 720)) } },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        PunlaField(
                            "Weekly goal (min)",
                            vm.weeklyStudyGoalMinutes.toString(),
                            { v -> v.filter { it.isDigit() }.toIntOrNull()?.let { vm.updateWeeklyStudyGoal(it.coerceIn(5, 5040)) } },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                }
            }
        }

        // Backup Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "BACKUP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Save all your classes, expenses, deadlines, grades, and archives to a file, or restore them from one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    // Roadmap #6 — passive nudge: surfaces how stale the
                    // last backup is instead of relying purely on memory.
                    val lastBackupAt = vm.lastBackupAt
                    val daysSinceBackup = remember(lastBackupAt) {
                        lastBackupAt?.let {
                            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it)
                        }
                    }
                    Text(
                        when {
                            lastBackupAt == null -> "You haven't backed up yet."
                            daysSinceBackup == 0L -> "Last backed up today."
                            daysSinceBackup == 1L -> "Last backed up 1 day ago."
                            else -> "Last backed up $daysSinceBackup days ago."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (daysSinceBackup != null && daysSinceBackup >= 14) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                    if (backupResult != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (backupResult) {
                                is PunlaViewModel.BackupResult.Success -> backupResult.message
                                is PunlaViewModel.BackupResult.Failure -> backupResult.message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (backupResult) {
                                is PunlaViewModel.BackupResult.Success -> MaterialTheme.colorScheme.primary
                                is PunlaViewModel.BackupResult.Failure -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }

        // Planner Config Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "PLANNER CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PunlaField(
                            "Monthly Budget (₱)",
                            budgetInput,
                            { budgetInput = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            budgetInput.toDoubleOrNull()?.let { vm.setBudget(it) }
                        }) {
                            Text("Set")
                        }
                    }

                    // Weekly Budgeting feature — period picker, week-start
                    // day, optional explicit weekly figure, and rollover.
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "BUDGET PERIOD",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A weekly view surfaces overspending while there's still time to adjust, instead of only at month's end.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackgroundStyleOptionRow(
                            label = "Monthly only",
                            description = "One running total for the month, like before.",
                            selected = vm.budgetPeriod == BudgetPeriod.MONTHLY,
                            onClick = { vm.updateBudgetPeriod(BudgetPeriod.MONTHLY) }
                        )
                        BackgroundStyleOptionRow(
                            label = "Weekly only",
                            description = "Track spend against a week-by-week figure instead.",
                            selected = vm.budgetPeriod == BudgetPeriod.WEEKLY,
                            onClick = { vm.updateBudgetPeriod(BudgetPeriod.WEEKLY) }
                        )
                        BackgroundStyleOptionRow(
                            label = "Both",
                            description = "Show weekly and monthly figures together.",
                            selected = vm.budgetPeriod == BudgetPeriod.BOTH,
                            onClick = { vm.updateBudgetPeriod(BudgetPeriod.BOTH) }
                        )
                    }

                    if (vm.budgetPeriod != BudgetPeriod.MONTHLY) {
                        Spacer(Modifier.height(12.dp))
                        PunlaDropdownField(
                            "Week starts on",
                            WEEK_START_DAY_OPTIONS.first { it.first == vm.weekStartDay }.second,
                            WEEK_START_DAY_OPTIONS.map { it.second },
                            onSelect = { vm.updateWeekStartDay(WEEK_START_DAY_OPTIONS[it].first) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PunlaField(
                                "Weekly Budget (₱)",
                                weeklyBudgetInput,
                                { weeklyBudgetInput = it },
                                placeholder = "Auto (from monthly budget)",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                if (weeklyBudgetInput.isBlank()) vm.updateWeeklyBudgetOverride(null)
                                else weeklyBudgetInput.toDoubleOrNull()?.let { vm.updateWeeklyBudgetOverride(it) }
                            }) {
                                Text("Set")
                            }
                        }
                        Text(
                            "Leave blank to auto-calculate from what's left of your monthly budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Roll over unused weekly budget", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(
                                    "Off resets clean each week; on carries last week's leftover (or overspend) into this one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = vm.weeklyRolloverEnabled,
                                onCheckedChange = { vm.updateWeeklyRolloverEnabled(it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PunlaField(
                            "CHED/Scholarship Target GWA",
                            targetInput,
                            { targetInput = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.updateChedTarget(targetInput.toDoubleOrNull()) }) {
                            Text("Set")
                        }
                    }
                }
            }
        }

        // Archive / Reset Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(
                        1.dp,
                        MaterialTheme.shapes.medium,
                        ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                        spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "NEW SEMESTER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start a new semester by archiving your current schedule and deadlines. This clears your calendar but saves a history snapshot under your Grades archives.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showArchiveConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start New Semester", color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            }
        }

        // Archives list
        if (archives.isNotEmpty()) {
            item {
                SectionLabel("Archived Semesters")
            }
            items(archives, key = { it.id }) { archive ->
                ArchiveCard(archive)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Start New Semester") },
            text = {
                Column {
                    Text("This will clear your current classes and deadlines, moving them to a history archive. Enter a label to identify this semester:")
                    Spacer(Modifier.height(8.dp))
                    PunlaField(
                        "Semester label",
                        archiveLabel,
                        { archiveLabel = it },
                        placeholder = "e.g. AY 2025-2026, 1st Sem",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (archiveLabel.isNotBlank()) {
                            vm.startNewSemester(archiveLabel)
                            archiveLabel = ""
                            showArchiveConfirm = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Archive & Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces all current data on this device — classes, expenses, deadlines, grades, archives, and settings. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.importBackup(pendingImportUri!!)
                        pendingImportUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Replace data")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initialArgb = vm.customSeedColor,
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { argb ->
                vm.updateCustomSeedColor(argb)
                showCustomColorDialog = false
            }
        )
    }
}

/** Options for the "Week starts on" dropdown — every [DayOfWeek], Sunday
 * first since that's the more common start-of-week convention for
 * allowance/stipend-driven budgeting even though Monday is the default. */
private val WEEK_START_DAY_OPTIONS = listOf(
    DayOfWeek.SUNDAY to "Sunday",
    DayOfWeek.MONDAY to "Monday",
    DayOfWeek.TUESDAY to "Tuesday",
    DayOfWeek.WEDNESDAY to "Wednesday",
    DayOfWeek.THURSDAY to "Thursday",
    DayOfWeek.FRIDAY to "Friday",
    DayOfWeek.SATURDAY to "Saturday"
)

/** A selectable row for one [BackgroundStyle] — same selected/unselected
 * treatment as [FontOptionRow], minus the font-family preview since
 * background style has no typeface of its own to show. */
@Composable
private fun BackgroundStyleOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** A selectable row for one [FontChoice] — shows the option's label previewed
 * in its own font (so the picker doubles as a live sample) plus a short
 * description, mirroring [PresetSwatch]'s selected/unselected treatment. */
@Composable
private fun FontOptionRow(
    label: String,
    description: String,
    previewFamily: FontFamily,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = previewFamily, fontWeight = FontWeight.SemiBold)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PresetSwatch(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Full HSV picker: hue, saturation, and brightness are all user-adjustable.
 * Saturation/value used to be pinned to fixed comfortably-saturated,
 * mid-bright values (0.55 / 0.65) so every hue produced a usable accent —
 * that's now just the *default* starting point, not a ceiling. materialkolor
 * still only needs one seed color to derive a full light+dark scheme, so
 * whatever HSV combination the person lands on becomes that seed, and every
 * background style (Ambient wash, Starfield stars, Rain streaks, Paper
 * Grain) inherits it for free since they all read colors off the same
 * resolved [com.uplb.punla.ui.theme.PunlaPalette] — no separate background
 * color picker needed.
 */
@Composable
private fun CustomColorDialog(
    initialArgb: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember {
        val hsv = FloatArray(3)
        if (initialArgb != null) {
            android.graphics.Color.colorToHSV(initialArgb, hsv)
        } else {
            // Defaults into the leaf-green range, close to the original brand
            // color, at the old fixed saturation/brightness.
            hsv[0] = 140f
            hsv[1] = 0.55f
            hsv[2] = 0.65f
        }
        hsv
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    val previewColor = Color.hsv(hue, saturation, brightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(previewColor)
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    "Hue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f
                )

                Text(
                    "Saturation — ${(saturation * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f
                )

                Text(
                    "Brightness — ${(brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    // Keep a floor above 0 — a near-black seed produces a
                    // washed-out, low-contrast scheme rather than a "dark"
                    // one, since light/dark variants are both derived from it.
                    valueRange = 0.2f..1f
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    "Light and dark variants — plus every background style — are generated automatically from this color.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(previewColor.toArgb()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ArchiveCard(archive: com.uplb.punla.data.entity.Archive) {
    val dateStr = remember(archive.createdAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(archive.createdAt))
    }
    val classesCount = remember(archive.scheduleJson) {
        runCatching { JSONArray(archive.scheduleJson).length() }.getOrDefault(0)
    }
    val deadlinesCount = remember(archive.deadlinesJson) {
        runCatching { JSONArray(archive.deadlinesJson).length() }.getOrDefault(0)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(
                1.dp,
                MaterialTheme.shapes.medium,
                ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f),
                spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            AccentBar(MaterialTheme.colorScheme.primary)
            Column(Modifier
                .padding(14.dp)
                .fillMaxWidth()) {
                Text(
                    archive.label,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$classesCount classes · $deadlinesCount deadlines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Archived on $dateStr",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
