package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BackupPackageSummary
import com.example.data.RestoreSelection

@Composable
fun RestoreDatabaseSelectionDialog(
    summary: BackupPackageSummary,
    onDismiss: () -> Unit,
    onConfirmRestore: (RestoreSelection) -> Unit
) {
    var restoreFlights by remember { mutableStateOf(summary.flightLogsCount > 0 || summary.notesCount > 0) }
    var restoreAirports by remember { mutableStateOf(summary.airportsCount > 0) }
    var restoreAircrafts by remember { mutableStateOf(summary.aircraftsCount > 0 || summary.aircraftTypesCount > 0) }
    var restoreUserProfile by remember { mutableStateOf(summary.hasUserProfile) }
    var restoreAppSettings by remember { mutableStateOf(summary.hasAppSettings) }
    var replaceExisting by remember { mutableStateOf(false) }

    val selectedCount = listOf(
        restoreFlights,
        restoreAirports,
        restoreAircrafts,
        restoreUserProfile,
        restoreAppSettings
    ).count { it }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("restore_database_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C28)),
            border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Restore Database",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Select data categories to import",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Backup package info banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BACKUP PACKAGE INFO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = summary.exportDate,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        if (summary.userProfileName != null) {
                            Text(
                                text = "Pilot Profile: ${summary.userProfileName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "${summary.flightLogsCount} Flights • ${summary.airportsCount} Airports • ${summary.aircraftsCount} Aircraft",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Selection Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DATA CATEGORIES ($selectedCount of 5 selected)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select All",
                            color = Color(0xFFFFB300),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    restoreFlights = true
                                    restoreAirports = true
                                    restoreAircrafts = true
                                    restoreUserProfile = true
                                    restoreAppSettings = true
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "|",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Clear All",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    restoreFlights = false
                                    restoreAirports = false
                                    restoreAircrafts = false
                                    restoreUserProfile = false
                                    restoreAppSettings = false
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Category Checkboxes
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category 1: Flight Logs
                    RestoreCategoryItem(
                        icon = Icons.Default.Flight,
                        title = "Flight Logs & Records",
                        subtitle = if (summary.flightLogsCount > 0) "${summary.flightLogsCount} flight entries available" else "No flight logs in backup",
                        available = summary.flightLogsCount > 0 || summary.notesCount > 0,
                        checked = restoreFlights,
                        onCheckedChange = { restoreFlights = it },
                        accentColor = Color(0xFF38BDF8)
                    )

                    // Category 2: Airport Database
                    RestoreCategoryItem(
                        icon = Icons.Default.LocationOn,
                        title = "Airports Database",
                        subtitle = if (summary.airportsCount > 0) "${summary.airportsCount} airport records available" else "No airports in backup",
                        available = summary.airportsCount > 0,
                        checked = restoreAirports,
                        onCheckedChange = { restoreAirports = it },
                        accentColor = Color(0xFF10B981)
                    )

                    // Category 3: Aircraft Fleet & Types
                    RestoreCategoryItem(
                        icon = Icons.Default.AirplanemodeActive,
                        title = "Aircraft Fleet & Types",
                        subtitle = if (summary.aircraftsCount > 0 || summary.aircraftTypesCount > 0) {
                            "${summary.aircraftsCount} registrations, ${summary.aircraftTypesCount} aircraft types"
                        } else {
                            "No aircraft in backup"
                        },
                        available = summary.aircraftsCount > 0 || summary.aircraftTypesCount > 0,
                        checked = restoreAircrafts,
                        onCheckedChange = { restoreAircrafts = it },
                        accentColor = Color(0xFF818CF8)
                    )

                    // Category 4: User Profile
                    RestoreCategoryItem(
                        icon = Icons.Default.Person,
                        title = "User Information & Profile",
                        subtitle = if (summary.hasUserProfile) "Pilot name, airline, role, limits & experience" else "No user profile in backup",
                        available = summary.hasUserProfile,
                        checked = restoreUserProfile,
                        onCheckedChange = { restoreUserProfile = it },
                        accentColor = Color(0xFFFFB300)
                    )

                    // Category 5: App Settings
                    RestoreCategoryItem(
                        icon = Icons.Default.Settings,
                        title = "App Settings & Preferences",
                        subtitle = if (summary.hasAppSettings) "Prefixes, crew size, defaults & wallet docs" else "No app settings in backup",
                        available = summary.hasAppSettings,
                        checked = restoreAppSettings,
                        onCheckedChange = { restoreAppSettings = it },
                        accentColor = Color(0xFFF472B6)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Import Mode (Merge vs Replace)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "IMPORT MODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )

                            // Merge Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { replaceExisting = false }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = !replaceExisting,
                                    onClick = { replaceExisting = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFB300))
                                )
                                Column {
                                    Text(
                                        text = "Merge with existing data (Recommended)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Keeps current records and adds or updates with backup data",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Replace Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { replaceExisting = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = replaceExisting,
                                    onClick = { replaceExisting = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
                                )
                                Column {
                                    Text(
                                        text = "Replace existing data for selected categories",
                                        color = if (replaceExisting) MaterialTheme.colorScheme.error else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Overwrites selected categories with the backup contents",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val selection = RestoreSelection(
                                restoreFlightLogs = restoreFlights,
                                restoreAirports = restoreAirports,
                                restoreAircrafts = restoreAircrafts,
                                restoreUserProfile = restoreUserProfile,
                                restoreAppSettings = restoreAppSettings,
                                replaceExisting = replaceExisting
                            )
                            onConfirmRestore(selection)
                        },
                        enabled = selectedCount > 0,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(46.dp)
                            .testTag("confirm_restore_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB300),
                            contentColor = Color(0xFF111827),
                            disabledContainerColor = Color(0xFFFFB300).copy(alpha = 0.3f),
                            disabledContentColor = Color(0xFF111827).copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedCount > 0) "Restore Selected ($selectedCount)" else "Select Categories",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    available: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = available) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked && available) Color(0xFF1B2332) else Color(0xFF151C26)
        ),
        border = BorderStroke(
            1.dp,
            if (checked && available) accentColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = checked && available,
                onCheckedChange = if (available) onCheckedChange else null,
                enabled = available,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    uncheckedColor = Color.White.copy(alpha = 0.3f),
                    checkmarkColor = Color(0xFF111827)
                )
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (available) accentColor else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (available) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (available) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.25f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
