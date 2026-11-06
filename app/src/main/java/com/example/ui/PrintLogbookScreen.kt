package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CosmicDatePickerDialog
import com.example.FlightLog
import com.example.calculateProratedMinutes
import com.example.calculateTimeDiffInMinutes
import com.example.compareFlightLogsRecentToOld
import com.example.data.UserProfileSettings
import com.example.formatMinutesToHoursClean
import com.example.getCrewCount
import com.example.parseBlockMinutesFromLog
import com.example.parseCrewAndTrainee
import com.example.parseLogDate
import com.example.util.DateRangePreset
import com.example.util.LogbookPrintFormat
import com.example.util.LogbookPrintGenerator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PrintLogbookScreen(
    allLogs: List<FlightLog>,
    profile: UserProfileSettings? = null,
    previousExperiences: List<com.example.data.PreviousExperience> = profile?.getPreviousExperiences() ?: emptyList(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val filenameDateFormat = remember { SimpleDateFormat("yyyyMMdd", Locale.US) }

    // Find earliest and latest dates from database records
    val parsedDateLogs = remember(allLogs) {
        allLogs.mapNotNull { log ->
            parseLogDate(log.date)?.let { d -> Pair(d, log) }
        }.sortedBy { it.first.time }
    }

    val earliestRecordDate = remember(parsedDateLogs) {
        parsedDateLogs.firstOrNull()?.first ?: Date()
    }
    val latestRecordDate = remember(parsedDateLogs) {
        parsedDateLogs.lastOrNull()?.first ?: Date()
    }

    // Default to Current Month
    val now = remember { Calendar.getInstance() }
    val defaultMonthStart = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }
    val defaultMonthEnd = remember(now) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }

    var selectedPreset by remember { mutableStateOf(DateRangePreset.CURRENT_MONTH) }
    var startDate by remember { mutableStateOf(defaultMonthStart) }
    var endDate by remember { mutableStateOf(defaultMonthEnd) }
    var selectedFormat by remember { mutableStateOf(LogbookPrintFormat.STANDARD_PILOT_LOGBOOK) }
    var sortChronological by remember { mutableStateOf(true) } // Official standard is earliest to latest
    var showTablePreview by remember { mutableStateOf(false) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Prior logs before selected start date to be forwarded on page 1
    val priorLogs = remember(allLogs, startDate) {
        val startCal = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        allLogs.filter { log ->
            val logDate = parseLogDate(log.date)
            logDate != null && logDate.before(startCal.time)
        }
    }

    // Filter logs according to the chosen range
    val filteredLogs = remember(allLogs, startDate, endDate, sortChronological) {
        val startCal = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            time = endDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val filtered = allLogs.filter { log ->
            val logDate = parseLogDate(log.date)
            if (logDate != null) {
                !logDate.before(startCal.time) && !logDate.after(endCal.time)
            } else {
                false
            }
        }

        if (sortChronological) {
            // Oldest to newest
            filtered.sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l2, l1) }
        } else {
            // Newest to oldest
            filtered.sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l1, l2) }
        }
    }

    val totals = remember(filteredLogs) {
        LogbookPrintGenerator.calculateTotals(filteredLogs)
    }

    val cumulativeTotals = remember(filteredLogs, previousExperiences, priorLogs) {
        LogbookPrintGenerator.calculateTotals(filteredLogs, previousExperiences, priorLogs)
    }

    val forwardedOnlyTotals = remember(previousExperiences, priorLogs) {
        LogbookPrintGenerator.calculateTotals(emptyList(), previousExperiences, priorLogs)
    }

    val generatedHtml = remember(filteredLogs, selectedFormat, startDate, endDate, profile, priorLogs, previousExperiences) {
        LogbookPrintGenerator.generateHtml(
            logs = filteredLogs,
            format = selectedFormat,
            startDate = startDate,
            endDate = endDate,
            profile = profile,
            priorLogs = priorLogs,
            previousExperiences = previousExperiences
        )
    }

    // HTML Export file launcher
    val exportHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(generatedHtml.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Logbook HTML report saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header Card ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("print_logbook_header_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
            border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFB300).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print Logbook",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prepare Logbook Printout",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Generate and print official pilot logbook reports (EASA, FAA, Jeppesen, Detailed)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // --- 2. Date Selection Section ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("print_logbook_date_section"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF3CD070),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Date Selection",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF3CD070)
                    )
                }

                // Presets Segmented Row
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRangePreset.values().forEach { preset ->
                        val isSelected = selectedPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPreset = preset
                                when (preset) {
                                    DateRangePreset.CURRENT_MONTH -> {
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.DAY_OF_MONTH, 1)
                                        startDate = cal.time
                                        val endCal = Calendar.getInstance()
                                        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                        endDate = endCal.time
                                    }
                                    DateRangePreset.CURRENT_YEAR -> {
                                        val cal = Calendar.getInstance()
                                        cal.set(Calendar.MONTH, Calendar.JANUARY)
                                        cal.set(Calendar.DAY_OF_MONTH, 1)
                                        startDate = cal.time
                                        val endCal = Calendar.getInstance()
                                        endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                                        endCal.set(Calendar.DAY_OF_MONTH, 31)
                                        endDate = endCal.time
                                    }
                                    DateRangePreset.ENTIRE_LOGBOOK -> {
                                        startDate = earliestRecordDate
                                        endDate = latestRecordDate
                                    }
                                    DateRangePreset.CUSTOM -> {
                                        // Keep custom values
                                    }
                                }
                            },
                            label = { Text(preset.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFB300),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF283141),
                                labelColor = Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                        )
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.08f))

                // Start Date Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Start Date",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF283141))
                                .clickable { showStartDatePicker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("start_date_picker_btn"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Start Date",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = displayDateFormat.format(startDate),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Options for First Record and Last Record for Start Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Set to:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                        SuggestionChip(
                            onClick = {
                                startDate = earliestRecordDate
                                selectedPreset = DateRangePreset.CUSTOM
                            },
                            label = { Text("First Record (${displayDateFormat.format(earliestRecordDate)})", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF283141),
                                labelColor = Color(0xFF3CD070)
                            ),
                            border = BorderStroke(0.5.dp, Color(0xFF3CD070).copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("start_date_first_record_btn")
                        )
                        SuggestionChip(
                            onClick = {
                                startDate = latestRecordDate
                                selectedPreset = DateRangePreset.CUSTOM
                            },
                            label = { Text("Last Record", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF283141),
                                labelColor = Color(0xFFFFB300)
                            ),
                            border = BorderStroke(0.5.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("start_date_last_record_btn")
                        )
                    }
                }

                // End Date Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "End Date",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF283141))
                                .clickable { showEndDatePicker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("end_date_picker_btn"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select End Date",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = displayDateFormat.format(endDate),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Options for First Record and Last Record for End Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Set to:",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                        SuggestionChip(
                            onClick = {
                                endDate = earliestRecordDate
                                selectedPreset = DateRangePreset.CUSTOM
                            },
                            label = { Text("First Record", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF283141),
                                labelColor = Color(0xFF3CD070)
                            ),
                            border = BorderStroke(0.5.dp, Color(0xFF3CD070).copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("end_date_first_record_btn")
                        )
                        SuggestionChip(
                            onClick = {
                                endDate = latestRecordDate
                                selectedPreset = DateRangePreset.CUSTOM
                            },
                            label = { Text("Last Record (${displayDateFormat.format(latestRecordDate)})", fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF283141),
                                labelColor = Color(0xFFFFB300)
                            ),
                            border = BorderStroke(0.5.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("end_date_last_record_btn")
                        )
                    }
                }

                // Sort Direction Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort Order:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF283141))
                            .padding(2.dp)
                    ) {
                        val chronActive = sortChronological
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (chronActive) Color(0xFFFFB300) else Color.Transparent)
                                .clickable { sortChronological = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Earliest First",
                                fontSize = 11.sp,
                                fontWeight = if (chronActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (chronActive) Color.Black else Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!chronActive) Color(0xFFFFB300) else Color.Transparent)
                                .clickable { sortChronological = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Newest First",
                                fontSize = 11.sp,
                                fontWeight = if (!chronActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (!chronActive) Color.Black else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Official Logbook Format Selection ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("print_logbook_format_section"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Official Logbook Format",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF818CF8)
                    )
                }

                LogbookPrintFormat.values().forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFormat = fmt }
                            .testTag("format_card_${fmt.name.lowercase()}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF818CF8).copy(alpha = 0.15f) else Color(0xFF283141)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFormat = fmt },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF818CF8),
                                    unselectedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fmt.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF818CF8).copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = fmt.standardCode,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF818CF8),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = fmt.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Live Statistics Summary Card ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("print_logbook_summary_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
            border = BorderStroke(1.dp, Color(0xFF3CD070).copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FILTERED SUMMARY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = Color(0xFF3CD070)
                    )
                    Text(
                        text = "${filteredLogs.size} flights selected",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "No flight records found within the selected date range (${displayDateFormat.format(startDate)} – ${displayDateFormat.format(endDate)}). Adjust the dates above.",
                        color = Color(0xFFFFB300),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryMetricBox(
                            label = "Block Time",
                            value = formatMinutesToHoursClean(totals.totalBlockMinutes),
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricBox(
                            label = "Flight Time",
                            value = formatMinutesToHoursClean(totals.totalFlightMinutes),
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricBox(
                            label = "Night Time",
                            value = formatMinutesToHoursClean(totals.totalNightMinutes),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryMetricBox(
                            label = "Landings (D/N)",
                            value = "${totals.totalDayLandings} / ${totals.totalNightLandings}",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricBox(
                            label = "PIC Hours",
                            value = formatMinutesToHoursClean(totals.totalPicMinutes),
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricBox(
                            label = "SIC Hours",
                            value = formatMinutesToHoursClean(totals.totalSicMinutes),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (totals.totalFiMinutes > 0 || totals.totalSimMinutes > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (totals.totalFiMinutes > 0) {
                                SummaryMetricBox(
                                    label = "Instructor (FI)",
                                    value = formatMinutesToHoursClean(totals.totalFiMinutes),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (totals.totalSimMinutes > 0) {
                                SummaryMetricBox(
                                    label = "Flight Simulator",
                                    value = formatMinutesToHoursClean(totals.totalSimMinutes),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Forwarded and Cumulative Section if prior logs or previous experience exist
                    if (priorLogs.isNotEmpty() || previousExperiences.isNotEmpty()) {
                        Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AMT. FORWARDED (PAGE 1) & TO DATE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = Color(0xFFFFB300)
                            )
                            Text(
                                text = "${priorLogs.size} prior logs + ${previousExperiences.size} prev exp",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryMetricBox(
                                label = "Fwd Block",
                                value = formatMinutesToHoursClean(forwardedOnlyTotals.totalBlockMinutes),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricBox(
                                label = "Fwd PIC",
                                value = formatMinutesToHoursClean(forwardedOnlyTotals.totalPicMinutes),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricBox(
                                label = "Fwd Sim",
                                value = formatMinutesToHoursClean(forwardedOnlyTotals.totalSimMinutes),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryMetricBox(
                                label = "Total to Date",
                                value = formatMinutesToHoursClean(cumulativeTotals.totalBlockMinutes),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricBox(
                                label = "PIC to Date",
                                value = formatMinutesToHoursClean(cumulativeTotals.totalPicMinutes),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricBox(
                                label = "Sim to Date",
                                value = formatMinutesToHoursClean(cumulativeTotals.totalSimMinutes),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // --- 5. Action Buttons (Print, Export HTML, Share) ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (filteredLogs.isEmpty()) {
                        Toast.makeText(context, "No flight logs selected for printing", Toast.LENGTH_SHORT).show()
                    } else {
                        val jobName = "Pilot_Logbook_${selectedFormat.name}_${filenameDateFormat.format(startDate)}_${filenameDateFormat.format(endDate)}"
                        LogbookPrintGenerator.print(context, generatedHtml, jobName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("print_logbook_execute_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB300),
                    contentColor = Color.Black
                ),
                enabled = filteredLogs.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Print / Save as PDF (${filteredLogs.size} Flights)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val filename = "pilot_logbook_${selectedFormat.name.lowercase()}_${filenameDateFormat.format(startDate)}_${filenameDateFormat.format(endDate)}.html"
                        exportHtmlLauncher.launch(filename)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("export_logbook_html_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    enabled = filteredLogs.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF3CD070)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save HTML", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        val title = "Pilot Logbook (${selectedFormat.title}) - ${displayDateFormat.format(startDate)} to ${displayDateFormat.format(endDate)}"
                        LogbookPrintGenerator.share(context, generatedHtml, title)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("share_logbook_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    enabled = filteredLogs.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF818CF8)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontSize = 13.sp)
                }
            }

            // Toggle In-App Table Preview
            TextButton(
                onClick = { showTablePreview = !showTablePreview },
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("toggle_preview_table_btn")
            ) {
                Icon(
                    imageVector = if (showTablePreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showTablePreview) "Hide Table Preview" else "Preview Logbook Table (${filteredLogs.size} Rows)",
                    color = Color(0xFFFFB300),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- 6. In-App Interactive Table Preview ---
        if (showTablePreview && filteredLogs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("print_logbook_table_preview"),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "TABLE PREVIEW &bull; ${selectedFormat.title}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF818CF8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF283141))
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TableCell("Date", 85.dp, isHeader = true)
                                TableCell("Flt #", 70.dp, isHeader = true)
                                TableCell("Type", 55.dp, isHeader = true)
                                TableCell("Tail", 70.dp, isHeader = true)
                                TableCell("Route", 90.dp, isHeader = true)
                                TableCell("Block", 65.dp, isHeader = true)
                                TableCell("Night", 55.dp, isHeader = true)
                                TableCell("Ldg", 50.dp, isHeader = true)
                                TableCell("Role", 55.dp, isHeader = true)
                                TableCell("Crew", 110.dp, isHeader = true)
                                TableCell("Trainee", 90.dp, isHeader = true)
                                TableCell("Remarks", 120.dp, isHeader = true)
                            }
                            Divider(color = Color.White.copy(alpha = 0.2f))

                            // Data Rows (limit preview to 25 items for smooth scrolling)
                            filteredLogs.take(25).forEachIndexed { idx, log ->
                                val crewCount = getCrewCount(log.crew)
                                val rawBlockMin = log.splitMinutes
                                    ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                                    ?: parseBlockMinutesFromLog(log.blockHours)
                                    ?: 0
                                val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
                                val rawNightMin = if (log.nightTime.isNotBlank()) {
                                    val clean = log.nightTime.trim()
                                    if (clean.contains(":")) {
                                        val p = clean.split(":")
                                        (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
                                    } else {
                                        (clean.toDoubleOrNull()?.times(60))?.toInt() ?: 0
                                    }
                                } else 0
                                val nightMin = calculateProratedMinutes(rawNightMin, crewCount)
                                val (crewPart, traineePart) = parseCrewAndTrainee(log.crew)
                                Row(
                                    modifier = Modifier
                                        .background(if (idx % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.02f))
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TableCell(log.date, 85.dp)
                                    TableCell(log.flightNum.ifBlank { "-" }, 70.dp, bold = true)
                                    TableCell(log.aircraftType, 55.dp)
                                    TableCell(log.tailNumber, 70.dp)
                                    TableCell("${log.fromCode} - ${log.toCode}", 90.dp)
                                    TableCell(formatMinutesToHoursClean(blockMin), 65.dp, bold = true)
                                    TableCell(if (nightMin > 0) formatMinutesToHoursClean(nightMin) else "-", 55.dp)
                                    TableCell("${log.landingDay}/${log.landingNight}", 50.dp)
                                    TableCell(log.pilotRole.ifBlank { "PIC" }, 55.dp)
                                    TableCell(crewPart.ifBlank { "Self" }, 110.dp)
                                    TableCell(traineePart.ifBlank { "-" }, 90.dp, color = if (traineePart.isNotBlank()) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.4f))
                                    TableCell(log.remarks.ifBlank { "-" }, 120.dp)
                                }
                                Divider(color = Color.White.copy(alpha = 0.05f))
                            }

                            if (filteredLogs.size > 25) {
                                Text(
                                    text = "... and ${filteredLogs.size - 25} more flights (all ${filteredLogs.size} will be included in the printout/PDF)",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        CosmicDatePickerDialog(
            initialDate = startDate,
            onDateSelected = { pickedDate ->
                startDate = pickedDate
                selectedPreset = DateRangePreset.CUSTOM
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        CosmicDatePickerDialog(
            initialDate = endDate,
            onDateSelected = { pickedDate ->
                endDate = pickedDate
                selectedPreset = DateRangePreset.CUSTOM
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

@Composable
private fun SummaryMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    bold: Boolean = false,
    color: Color = Color.White
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontSize = if (isHeader) 10.sp else 11.sp,
        fontWeight = if (isHeader || bold) FontWeight.Bold else FontWeight.Normal,
        color = if (isHeader) Color(0xFFFFB300) else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
