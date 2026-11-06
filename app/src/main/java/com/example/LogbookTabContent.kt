package com.example

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Airport
import com.example.ui.EbLogViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Immutable
data class FlightLogGroup(
    val groupKey: String,
    val groupSortEpoch: Long,
    val logs: List<FlightLog>,
    val totalMin: Int,
    val proratedTotalMin: Int,
    val picMin: Int,
    val proratedPicMin: Int,
    val sicMin: Int,
    val proratedSicMin: Int,
    val fiMin: Int = 0,
    val proratedFiMin: Int = 0
)

@Immutable
data class LogbookSummaryStats(
    val picMin: Int,
    val picProMin: Int,
    val sicMin: Int,
    val sicProMin: Int,
    val totalMin: Int,
    val totalProMin: Int,
    val fiMin: Int = 0,
    val fiProMin: Int = 0
)

@Composable
fun PeriodNavigatorBar(
    currentGroup: FlightLogGroup,
    currentIndex: Int,
    totalGroups: Int,
    isYear: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSelectGroupIndex: ((Int) -> Unit)? = null,
    allGroupKeys: List<String> = emptyList()
) {
    var showGroupDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("period_navigator_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isYear) Color(0xFF192231) else Color(0xFF17202E)
        ),
        border = BorderStroke(
            1.dp,
            if (isYear) Color(0xFFFFB300).copy(alpha = 0.45f) else Color(0xFF38BDF8).copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Navigation Controls: < [Title & Count] >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Navigation Button
                IconButton(
                    onClick = onPrevious,
                    enabled = hasPrevious,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (hasPrevious) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                            shape = CircleShape
                        )
                        .testTag("period_nav_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Period",
                        tint = if (hasPrevious) Color(0xFFFFB300) else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Center Period Title & Quick Dropdown Selector
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (allGroupKeys.size > 1) {
                                    showGroupDropdown = true
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isYear) Icons.Default.DateRange else Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (isYear) Color(0xFFFFB300) else Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = currentGroup.groupKey,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.testTag("period_nav_title")
                            )
                            if (allGroupKeys.size > 1) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select period",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isYear) Color(0xFFFFB300).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (currentGroup.logs.size == 1) "1 Flight" else "${currentGroup.logs.size} Flights",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                                    color = if (isYear) Color(0xFFFFB300) else Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (totalGroups > 1) {
                                Text(
                                    text = "${currentIndex + 1} of $totalGroups",
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Dropdown for quickly jumping between available periods
                    if (allGroupKeys.size > 1) {
                        DropdownMenu(
                            expanded = showGroupDropdown,
                            onDismissRequest = { showGroupDropdown = false },
                            modifier = Modifier
                                .background(Color(0xFF1E2530))
                                .heightIn(max = 280.dp)
                        ) {
                            allGroupKeys.forEachIndexed { index, groupName ->
                                val isSelected = (index == currentIndex)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = groupName,
                                                color = if (isSelected) Color(0xFFFFB300) else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFB300),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectGroupIndex?.invoke(index)
                                        showGroupDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Next Navigation Button
                IconButton(
                    onClick = onNext,
                    enabled = hasNext,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (hasNext) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                            shape = CircleShape
                        )
                        .testTag("period_nav_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Period",
                        tint = if (hasNext) Color(0xFFFFB300) else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Period Subtotals Row
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // PIC (Actual & Prorated)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "PIC:",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatMinutesToHoursClean(currentGroup.picMin),
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Pro: " + formatMinutesToHoursClean(currentGroup.proratedPicMin),
                            color = Color(0xFF34D399).copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // SIC (Actual & Prorated)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SIC:",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatMinutesToHoursClean(currentGroup.sicMin),
                                color = Color(0xFF60A5FA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Pro: " + formatMinutesToHoursClean(currentGroup.proratedSicMin),
                            color = Color(0xFF60A5FA).copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // FI (Actual & Prorated)
                    if (currentGroup.fiMin > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "FI:",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatMinutesToHoursClean(currentGroup.fiMin),
                                    color = Color(0xFFF59E0B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Pro: " + formatMinutesToHoursClean(currentGroup.proratedFiMin),
                                color = Color(0xFFF59E0B).copy(alpha = 0.75f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Period Total (Actual & Prorated)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Period Total:",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatMinutesToHoursClean(currentGroup.totalMin),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Pro: " + formatMinutesToHoursClean(currentGroup.proratedTotalMin),
                        color = Color(0xFFFFB300),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LogbookTabContent(
    viewModel: EbLogViewModel,
    onEdit: (FlightLog) -> Unit,
    onNext: (FlightLog) -> Unit,
    onReturn: (FlightLog) -> Unit,
    onDuplicate: (FlightLog) -> Unit,
    onDeleteRequest: (FlightLog) -> Unit,
    showFilters: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    incompleteOnly: Boolean,
    onIncompleteOnlyChange: (Boolean) -> Unit,
    selectedAircraftTypes: Set<String>,
    onSelectedAircraftTypesChange: (Set<String>) -> Unit,
    selectedPeriods: Set<String>,
    onSelectedPeriodsChange: (Set<String>) -> Unit,
    customStartDate: java.util.Date?,
    onCustomStartDateChange: (java.util.Date?) -> Unit,
    customEndDate: java.util.Date?,
    onCustomEndDateChange: (java.util.Date?) -> Unit,
    selectedPilotRoles: Set<String>,
    onSelectedPilotRolesChange: (Set<String>) -> Unit,
    selectedBlockTimes: Set<String>,
    onSelectedBlockTimesChange: (Set<String>) -> Unit,
    selectedCrews: Set<String> = emptySet(),
    onSelectedCrewsChange: (Set<String>) -> Unit = {},
    sortRecentFirst: Boolean,
    onSortRecentFirstChange: (Boolean) -> Unit,
    viewBy: String = "All Records",
    onViewByChange: (String) -> Unit = {},
    activeGroupIndex: Int = 0,
    onActiveGroupIndexChange: (Int) -> Unit = {},
    allLogs: List<FlightLog>,
    filteredLogs: List<FlightLog>,
    onClearAllFilters: () -> Unit,
    scrollState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
    val dbAirports by viewModel.airports.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val logbookDateFormatter = remember { SimpleDateFormat("dd MMM yy", Locale.US) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val parsedDateLogs = remember(allLogs) {
        allLogs.mapNotNull { log ->
            parseLogDate(log.date)?.let { d -> Pair(d, log) }
        }.sortedBy { it.first.time }
    }
    val earliestRecordDate = remember(parsedDateLogs) {
        parsedDateLogs.firstOrNull()?.first
    }
    val latestRecordDate = remember(parsedDateLogs) {
        parsedDateLogs.lastOrNull()?.first
    }

    val isFrontStartSelected = remember(customStartDate, earliestRecordDate) {
        if (customStartDate == null || earliestRecordDate == null) false
        else {
            val c1 = java.util.Calendar.getInstance().apply { time = customStartDate }
            val c2 = java.util.Calendar.getInstance().apply { time = earliestRecordDate }
            c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
            c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }

    val isToEndSelected = remember(customEndDate, latestRecordDate) {
        if (customEndDate == null || latestRecordDate == null) false
        else {
            val c1 = java.util.Calendar.getInstance().apply { time = customEndDate }
            val c2 = java.util.Calendar.getInstance().apply { time = latestRecordDate }
            c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
            c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }

    val activeFilterCriteriaText = remember(
        searchQuery,
        selectedAircraftTypes,
        selectedCrews,
        selectedPilotRoles,
        selectedPeriods,
        selectedBlockTimes,
        incompleteOnly,
        customStartDate,
        customEndDate,
        isFrontStartSelected,
        isToEndSelected
    ) {
        val q = searchQuery.trim()
        val otherParts = mutableListOf<String>()
        if (selectedCrews.isNotEmpty()) otherParts.add(selectedCrews.joinToString(", "))
        if (selectedAircraftTypes.isNotEmpty()) otherParts.add(selectedAircraftTypes.joinToString(", "))
        if (selectedPilotRoles.isNotEmpty()) otherParts.add(selectedPilotRoles.joinToString(", "))
        if (selectedBlockTimes.isNotEmpty()) otherParts.add(selectedBlockTimes.joinToString(", "))
        if (selectedPeriods.isNotEmpty()) {
            if (selectedPeriods.contains("Custom")) {
                val nonCustom = selectedPeriods.filter { it != "Custom" }
                val customParts = mutableListOf<String>()
                if (isFrontStartSelected && isToEndSelected) {
                    customParts.add("All: Front Start → To End")
                } else {
                    val s = if (isFrontStartSelected) "Front Start" else customStartDate?.let { logbookDateFormatter.format(it) }
                    val e = if (isToEndSelected) "To End" else customEndDate?.let { logbookDateFormatter.format(it) }
                    if (s != null && e != null) customParts.add("$s to $e")
                    else if (s != null) customParts.add("From $s")
                    else if (e != null) customParts.add("Until $e")
                    else customParts.add("Custom")
                }
                otherParts.addAll(nonCustom)
                otherParts.addAll(customParts)
            } else {
                otherParts.add(selectedPeriods.joinToString(", "))
            }
        }
        if (incompleteOnly) otherParts.add("Incomplete")

        if (q.isNotEmpty()) {
            q
        } else if (otherParts.isNotEmpty()) {
            otherParts.joinToString(" • ")
        } else {
            ""
        }
    }

    // Grouping calculations based on viewBy ("All Records", "Month", "Year")
    val groupedLogs = remember(filteredLogs, viewBy, sortRecentFirst) {
        if (viewBy.equals("Month", ignoreCase = true)) {
            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
            val map = mutableMapOf<String, MutableList<FlightLog>>()
            val epochMap = mutableMapOf<String, Long>()

            filteredLogs.forEach { log ->
                val split = getMonthSplitInfo(log)
                if (split != null && split.extendsToNextMonth) {
                    // 1. Starting month portion (credits time up to 00:00)
                    val startLog = log.copy(
                        splitPart = 1,
                        splitMinutes = split.startingMinutes,
                        inTime = "00:00",
                        onTime = "00:00",
                        landingDay = 0,
                        landingNight = 0
                    )
                    map.getOrPut(split.startingMonthKey) { mutableListOf() }.add(startLog)
                    if (!epochMap.containsKey(split.startingMonthKey)) {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = split.startingDate
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        epochMap[split.startingMonthKey] = cal.timeInMillis
                    }

                    // 2. Next month portion (credits time after 00:00)
                    val nextDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val nextDateStr = nextDateFormat.format(split.nextDate)
                    val nextLog = log.copy(
                        splitPart = 2,
                        splitMinutes = split.nextMinutes,
                        displayDate = nextDateStr,
                        date = SimpleDateFormat("dd MMM yyyy", Locale.US).format(split.nextDate),
                        dateEpoch = split.nextDate.time,
                        outTime = "00:00",
                        offTime = "00:00",
                        outMinutes = 0,
                        takeoffDay = 0,
                        takeoffNight = 0
                    )
                    map.getOrPut(split.nextMonthKey) { mutableListOf() }.add(nextLog)
                    if (!epochMap.containsKey(split.nextMonthKey)) {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = split.nextDate
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        epochMap[split.nextMonthKey] = cal.timeInMillis
                    }
                } else {
                    val d = parseLogDate(log.date)
                    val key = if (d != null) monthFormat.format(d) else "Unspecified Date"
                    map.getOrPut(key) { mutableListOf() }.add(log)
                    if (d != null && !epochMap.containsKey(key)) {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = d
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        epochMap[key] = cal.timeInMillis
                    }
                }
            }

            map.map { (key, logsList) ->
                var pMin = 0
                var pProMin = 0
                var sMin = 0
                var sProMin = 0
                var fMin = 0
                var fProMin = 0
                var tMin = 0
                var tProMin = 0
                logsList.forEach { log ->
                    val blockMin = log.splitMinutes
                        ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                        ?: calculateTimeDiffInMinutes(log.offTime, log.onTime)
                        ?: parseBlockMinutesFromLog(log.blockHours)
                        ?: 0
                    val crewCount = getCrewCount(log.crew)
                    val proMin = calculateProratedMinutes(blockMin, crewCount)
                    tMin += blockMin
                    tProMin += proMin
                    val role = log.pilotRole.trim()
                    val isPic = isPicRole(role, includeFI = true)
                    if (isPic) {
                        pMin += blockMin
                        pProMin += proMin
                    } else {
                        sMin += blockMin
                        sProMin += proMin
                    }
                    if (isFlightInstructorRole(role)) {
                        fMin += blockMin
                        fProMin += proMin
                    }
                }
                val sortedList = logsList.sortedWith { l1, l2 ->
                    if (sortRecentFirst) {
                        compareFlightLogsRecentToOld(l1, l2)
                    } else {
                        compareFlightLogsRecentToOld(l2, l1)
                    }
                }
                FlightLogGroup(
                    groupKey = key,
                    groupSortEpoch = epochMap[key] ?: 0L,
                    logs = sortedList,
                    totalMin = tMin,
                    proratedTotalMin = tProMin,
                    picMin = pMin,
                    proratedPicMin = pProMin,
                    sicMin = sMin,
                    proratedSicMin = sProMin,
                    fiMin = fMin,
                    proratedFiMin = fProMin
                )
            }.sortedWith { g1, g2 ->
                if (sortRecentFirst) {
                    g2.groupSortEpoch.compareTo(g1.groupSortEpoch)
                } else {
                    g1.groupSortEpoch.compareTo(g2.groupSortEpoch)
                }
            }
        } else if (viewBy.equals("Year", ignoreCase = true)) {
            val yearFormat = SimpleDateFormat("yyyy", Locale.US)
            val map = mutableMapOf<String, MutableList<FlightLog>>()
            val epochMap = mutableMapOf<String, Long>()

            filteredLogs.forEach { log ->
                val split = getMonthSplitInfo(log)
                if (split != null && split.extendsToNextMonth && split.startingYearKey != split.nextYearKey) {
                    val startLog = log.copy(
                        splitPart = 1,
                        splitMinutes = split.startingMinutes,
                        inTime = "00:00",
                        onTime = "00:00",
                        landingDay = 0,
                        landingNight = 0
                    )
                    map.getOrPut(split.startingYearKey) { mutableListOf() }.add(startLog)
                    if (!epochMap.containsKey(split.startingYearKey)) {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = split.startingDate
                            set(java.util.Calendar.MONTH, 0)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        epochMap[split.startingYearKey] = cal.timeInMillis
                    }

                    val nextDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val nextDateStr = nextDateFormat.format(split.nextDate)
                    val nextLog = log.copy(
                        splitPart = 2,
                        splitMinutes = split.nextMinutes,
                        displayDate = nextDateStr,
                        date = SimpleDateFormat("dd MMM yyyy", Locale.US).format(split.nextDate),
                        dateEpoch = split.nextDate.time,
                        outTime = "00:00",
                        offTime = "00:00",
                        outMinutes = 0,
                        takeoffDay = 0,
                        takeoffNight = 0
                    )
                    map.getOrPut(split.nextYearKey) { mutableListOf() }.add(nextLog)
                    if (!epochMap.containsKey(split.nextYearKey)) {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = split.nextDate
                            set(java.util.Calendar.MONTH, 0)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        epochMap[split.nextYearKey] = cal.timeInMillis
                    }
                } else {
                    val d = parseLogDate(log.date)
                    val key = if (d != null) yearFormat.format(d) else "Unspecified Year"
                    map.getOrPut(key) { mutableListOf() }.add(log)
                    if (d != null && !epochMap.containsKey(key)) {
                        val cal = java.util.Calendar.getInstance().apply {
                            time = d
                            set(java.util.Calendar.MONTH, 0)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        epochMap[key] = cal.timeInMillis
                    }
                }
            }

            map.map { (key, logsList) ->
                var pMin = 0
                var pProMin = 0
                var sMin = 0
                var sProMin = 0
                var fMin = 0
                var fProMin = 0
                var tMin = 0
                var tProMin = 0
                logsList.forEach { log ->
                    val blockMin = log.splitMinutes
                        ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                        ?: calculateTimeDiffInMinutes(log.offTime, log.onTime)
                        ?: parseBlockMinutesFromLog(log.blockHours)
                        ?: 0
                    val crewCount = getCrewCount(log.crew)
                    val proMin = calculateProratedMinutes(blockMin, crewCount)
                    tMin += blockMin
                    tProMin += proMin
                    val role = log.pilotRole.trim()
                    val isPic = isPicRole(role, includeFI = true)
                    if (isPic) {
                        pMin += blockMin
                        pProMin += proMin
                    } else {
                        sMin += blockMin
                        sProMin += proMin
                    }
                    if (isFlightInstructorRole(role)) {
                        fMin += blockMin
                        fProMin += proMin
                    }
                }
                val sortedList = logsList.sortedWith { l1, l2 ->
                    if (sortRecentFirst) {
                        compareFlightLogsRecentToOld(l1, l2)
                    } else {
                        compareFlightLogsRecentToOld(l2, l1)
                    }
                }
                FlightLogGroup(
                    groupKey = key,
                    groupSortEpoch = epochMap[key] ?: 0L,
                    logs = sortedList,
                    totalMin = tMin,
                    proratedTotalMin = tProMin,
                    picMin = pMin,
                    proratedPicMin = pProMin,
                    sicMin = sMin,
                    proratedSicMin = sProMin,
                    fiMin = fMin,
                    proratedFiMin = fProMin
                )
            }.sortedWith { g1, g2 ->
                if (sortRecentFirst) {
                    g2.groupSortEpoch.compareTo(g1.groupSortEpoch)
                } else {
                    g1.groupSortEpoch.compareTo(g2.groupSortEpoch)
                }
            }
        } else {
            emptyList()
        }
    }

    val isNavigableGroupView = viewBy.equals("Month", ignoreCase = true) || viewBy.equals("Year", ignoreCase = true)
    val isYear = viewBy.equals("Year", ignoreCase = true)

    // Manage active group index locally with immediate state update to prevent recomposition latency
    var localActiveGroupIndex by remember { mutableIntStateOf(activeGroupIndex) }

    LaunchedEffect(activeGroupIndex) {
        if (localActiveGroupIndex != activeGroupIndex) {
            localActiveGroupIndex = activeGroupIndex
        }
    }

    // Anchor flight log ID: the ID of the record currently visible at the top of the view list
    var stableAnchorLogId by remember { mutableStateOf<Int?>(null) }
    var targetScrollLogId by remember { mutableStateOf<Int?>(null) }
    var previousViewBy by remember { mutableStateOf(viewBy) }
    var previousSortRecentFirst by remember { mutableStateOf(sortRecentFirst) }

    // When viewBy ("All Records", "Month", "Year") or sortRecentFirst (Recent to Last / First to Last) changes:
    // Seamlessly transition the view to show the flight record currently on top of the view list!
    val viewChanged = !viewBy.equals(previousViewBy, ignoreCase = true)
    val sortChanged = (sortRecentFirst != previousSortRecentFirst)

    if (viewChanged || sortChanged) {
        val anchorId = stableAnchorLogId
        previousViewBy = viewBy
        previousSortRecentFirst = sortRecentFirst

        if (anchorId != null) {
            targetScrollLogId = anchorId
            if (isNavigableGroupView && groupedLogs.isNotEmpty()) {
                val groupIdx = groupedLogs.indexOfFirst { g ->
                    g.logs.any { it.id == anchorId }
                }
                if (groupIdx >= 0) {
                    localActiveGroupIndex = groupIdx
                }
            }
        }
    }

    // Adjust active index if out of bounds
    val safeGroupIndex = if (groupedLogs.isNotEmpty()) {
        localActiveGroupIndex.coerceIn(0, groupedLogs.size - 1)
    } else {
        0
    }

    var previousNavGroupIndex by remember { mutableIntStateOf(safeGroupIndex) }

    // Synchronize active group index back to parent when transition takes place
    SideEffect {
        if (viewChanged || sortChanged) {
            onActiveGroupIndexChange(safeGroupIndex)
            previousNavGroupIndex = safeGroupIndex
        }
    }

    val setGroupIndex: (Int) -> Unit = { newIdx ->
        val safe = if (groupedLogs.isNotEmpty()) newIdx.coerceIn(0, groupedLogs.size - 1) else 0
        localActiveGroupIndex = safe
        onActiveGroupIndexChange(safe)
    }

    val currentGroup = if (isNavigableGroupView && groupedLogs.isNotEmpty()) {
        groupedLogs[safeGroupIndex]
    } else {
        null
    }

    val displayedLogs = if (isNavigableGroupView) {
        currentGroup?.logs ?: emptyList()
    } else {
        filteredLogs
    }

    // Summary calculations for records selected/displayed on page (reflects active period when View By is active)
    val activeLogsForStats = displayedLogs

    // Continuously keep stableAnchorLogId synchronized with the top visible item in the viewport during normal scrolling
    LaunchedEffect(scrollState, displayedLogs) {
        snapshotFlow {
            scrollState.firstVisibleItemIndex
        }.collect { firstIdx ->
            if (targetScrollLogId == null) {
                val log = displayedLogs.getOrNull(firstIdx) ?: displayedLogs.firstOrNull()
                if (log != null) {
                    stableAnchorLogId = log.id
                }
            }
        }
    }

    // Ensure stableAnchorLogId is initialized when displayedLogs is populated
    LaunchedEffect(displayedLogs) {
        if (targetScrollLogId == null && stableAnchorLogId == null) {
            val log = displayedLogs.getOrNull(scrollState.firstVisibleItemIndex) ?: displayedLogs.firstOrNull()
            if (log != null) {
                stableAnchorLogId = log.id
            }
        }
    }

    // When the user explicitly changes period using navigation controls (< / > / dropdown), reset scroll to top of that period
    LaunchedEffect(safeGroupIndex) {
        if (safeGroupIndex != previousNavGroupIndex) {
            val isManualNav = (targetScrollLogId == null)
            previousNavGroupIndex = safeGroupIndex
            if (isManualNav) {
                scrollState.scrollToItem(0)
                displayedLogs.firstOrNull()?.id?.let { stableAnchorLogId = it }
            }
        }
    }

    // Scroll directly to the target flight record once displayedLogs is ready
    LaunchedEffect(targetScrollLogId, displayedLogs) {
        val targetId = targetScrollLogId ?: return@LaunchedEffect
        val targetIdx = displayedLogs.indexOfFirst { it.id == targetId }
        if (targetIdx >= 0) {
            scrollState.scrollToItem(targetIdx)
            kotlinx.coroutines.delay(25)
            if (scrollState.firstVisibleItemIndex != targetIdx) {
                scrollState.scrollToItem(targetIdx)
            }
            stableAnchorLogId = targetId
            targetScrollLogId = null
        } else if (displayedLogs.isNotEmpty() && filteredLogs.none { it.id == targetId }) {
            // Target log no longer exists in dataset
            targetScrollLogId = null
        }
    }

    val summaryStats = remember(activeLogsForStats) {
        var picMin = 0
        var picProMin = 0
        var sicMin = 0
        var sicProMin = 0
        var fiMin = 0
        var fiProMin = 0
        var totalMin = 0
        var totalProMin = 0
        activeLogsForStats.forEach { log ->
            val blockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: calculateTimeDiffInMinutes(log.offTime, log.onTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val crewCount = getCrewCount(log.crew)
            val proMin = calculateProratedMinutes(blockMin, crewCount)
            totalMin += blockMin
            totalProMin += proMin
            val role = log.pilotRole.trim()
            val isPic = isPicRole(role, includeFI = true)
            if (isPic) {
                picMin += blockMin
                picProMin += proMin
            } else {
                sicMin += blockMin
                sicProMin += proMin
            }
            if (isFlightInstructorRole(role)) {
                fiMin += blockMin
                fiProMin += proMin
            }
        }
        LogbookSummaryStats(
            picMin = picMin,
            picProMin = picProMin,
            sicMin = sicMin,
            sicProMin = sicProMin,
            totalMin = totalMin,
            totalProMin = totalProMin,
            fiMin = fiMin,
            fiProMin = fiProMin
        )
    }

    val totalPicMin = summaryStats.picMin
    val totalPicProMin = summaryStats.picProMin
    val totalSicMin = summaryStats.sicMin
    val totalSicProMin = summaryStats.sicProMin
    val totalFiMin = summaryStats.fiMin
    val totalFiProMin = summaryStats.fiProMin
    val totalBlockMin = summaryStats.totalMin
    val totalProMin = summaryStats.totalProMin

    // Gesture swipe detection for horizontal navigation between periods
    var totalDragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fixed Top Tab / Summary Bar (Hidden when View By Month or Year is active)
        if (!isNavigableGroupView) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logbook_summary_tab"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E2530)
                ),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Records Metric
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "RECORDS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "${activeLogsForStats.size}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "Logs",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        // PIC Hours Metric (Actual & Prorated)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "PIC HOURS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF34D399),
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatMinutesToHoursClean(totalPicMin),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF34D399),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Pro: " + formatMinutesToHoursClean(totalPicProMin),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF34D399).copy(alpha = 0.8f)
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        // SIC Hours Metric (Actual & Prorated)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "SIC HOURS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF60A5FA),
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatMinutesToHoursClean(totalSicMin),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF60A5FA),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Pro: " + formatMinutesToHoursClean(totalSicProMin),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF60A5FA).copy(alpha = 0.8f)
                            )
                        }

                        // FI Hours Metric (Actual & Prorated) if FI hours available
                        if (totalFiMin > 0) {
                            // Divider
                            Box(
                                modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = "FI HOURS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF59E0B),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = formatMinutesToHoursClean(totalFiMin),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFF59E0B),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Pro: " + formatMinutesToHoursClean(totalFiProMin),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF59E0B).copy(alpha = 0.8f)
                                )
                            }
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        // Total Hours Metric (Actual & Prorated)
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "TOTAL",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatMinutesToHoursClean(totalBlockMin),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Pro: " + formatMinutesToHoursClean(totalProMin),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }
            }
        }

        // View By Selector Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("logbook_view_by_bar"),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C28)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewAgenda,
                        contentDescription = "View By",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "VIEW BY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All Records", "Month", "Year").forEach { option ->
                        val isSelected = (viewBy.equals(option, ignoreCase = true) || (option == "All Records" && (viewBy.isBlank() || viewBy == "all")))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFFFFB300) else Color(0xFF1E2530),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .testTag("logbook_view_by_${option.lowercase().replace(" ", "_")}")
                                .clickable {
                                    val topId = displayedLogs.getOrNull(scrollState.firstVisibleItemIndex)?.id ?: displayedLogs.firstOrNull()?.id
                                    if (topId != null) {
                                        stableAnchorLogId = topId
                                    }
                                    onViewByChange(option)
                                }
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF111827) else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Period Navigator Bar (when Month or Year is selected)
        if (isNavigableGroupView && currentGroup != null && groupedLogs.isNotEmpty()) {
            PeriodNavigatorBar(
                currentGroup = currentGroup,
                currentIndex = safeGroupIndex,
                totalGroups = groupedLogs.size,
                isYear = isYear,
                onPrevious = {
                    if (safeGroupIndex > 0) {
                        setGroupIndex(safeGroupIndex - 1)
                    }
                },
                onNext = {
                    if (safeGroupIndex < groupedLogs.size - 1) {
                        setGroupIndex(safeGroupIndex + 1)
                    }
                },
                hasPrevious = safeGroupIndex > 0,
                hasNext = safeGroupIndex < groupedLogs.size - 1,
                onSelectGroupIndex = { idx ->
                    setGroupIndex(idx)
                },
                allGroupKeys = groupedLogs.map { it.groupKey }
            )
        }

        if (showFilters) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .testTag("logbook_filters_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2530)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search Query Box
                    SelectableOutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search aircraft reg, airport, pilot name...", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("logbook_filter_search"),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF111827),
                            unfocusedContainerColor = Color(0xFF111827)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    )

                    // Sort Order Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (sortRecentFirst) Color(0xFFFFB300) else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val topId = displayedLogs.getOrNull(scrollState.firstVisibleItemIndex)?.id ?: displayedLogs.firstOrNull()?.id
                                    if (topId != null) {
                                        stableAnchorLogId = topId
                                    }
                                    onSortRecentFirstChange(true)
                                }
                        ) {
                            Text(
                                text = "Newest First",
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = if (sortRecentFirst) FontWeight.Bold else FontWeight.Normal,
                                color = if (sortRecentFirst) Color(0xFF111827) else Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (!sortRecentFirst) Color(0xFFFFB300) else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val topId = displayedLogs.getOrNull(scrollState.firstVisibleItemIndex)?.id ?: displayedLogs.firstOrNull()?.id
                                    if (topId != null) {
                                        stableAnchorLogId = topId
                                    }
                                    onSortRecentFirstChange(false)
                                }
                        ) {
                            Text(
                                text = "Oldest First",
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                fontWeight = if (!sortRecentFirst) FontWeight.Bold else FontWeight.Normal,
                                color = if (!sortRecentFirst) Color(0xFF111827) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Incomplete Logs Only Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIncompleteOnlyChange(!incompleteOnly) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = incompleteOnly,
                            onCheckedChange = { onIncompleteOnlyChange(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFFFB300),
                                uncheckedColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Text(
                            text = "Incomplete Logs Only (Missing times/reg)",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    // Aircraft Type Dropdown Filter (Multi-select)
                    val uniqueAircrafts = remember(allLogs) {
                        allLogs.map { it.aircraftType.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
                    }
                    MultiSelectDropdown(
                        label = "Aircraft Type",
                        options = uniqueAircrafts,
                        selectedOptions = selectedAircraftTypes,
                        onSelectionChange = onSelectedAircraftTypesChange,
                        placeholder = "All Aircraft Types"
                    )

                    // Crew Member Dropdown Filter (Multi-select)
                    val uniqueCrews = remember(allLogs) {
                        allLogs.flatMap { log ->
                            val (crewPart, traineePart) = parseCrewAndTrainee(log.crew)
                            val crewMembers = crewPart.split(",").map { it.trim() }
                            if (traineePart.isNotBlank()) crewMembers + traineePart else crewMembers
                        }.filter { it.isNotBlank() && it != "-" && !it.equals("Self", ignoreCase = true) }
                        .distinct()
                        .sorted()
                    }
                    if (uniqueCrews.isNotEmpty()) {
                        MultiSelectDropdown(
                            label = "Crew Member",
                            options = uniqueCrews,
                            selectedOptions = selectedCrews,
                            onSelectionChange = onSelectedCrewsChange,
                            placeholder = "All Crew Members"
                        )
                    }



                    // Period Dropdown Filter (Multi-select)
                    val periodOptions = listOf("Last Week", "Last Month", "Last Year", "Custom")
                    MultiSelectDropdown(
                        label = "Period",
                        options = periodOptions,
                        selectedOptions = selectedPeriods,
                        onSelectionChange = onSelectedPeriodsChange,
                        placeholder = "All Periods"
                    )

                    // Custom Date Pickers (Shown if "Custom" is selected in Period dropdown)
                    if (selectedPeriods.contains("Custom")) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF161F2E), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Quick Select Strip for "Front Start" and "To End"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CUSTOM DATE RANGE",
                                    color = Color(0xFFFFB300),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                // Full Span (Front to End) chip
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isFrontStartSelected && isToEndSelected) Color(0xFF6366F1).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isFrontStartSelected && isToEndSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            if (earliestRecordDate != null && latestRecordDate != null) {
                                                onCustomStartDateChange(earliestRecordDate)
                                                onCustomEndDateChange(latestRecordDate)
                                            } else {
                                                Toast.makeText(context, "No flight records found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("custom_date_full_span_chip"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Front to End",
                                        color = if (isFrontStartSelected && isToEndSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // FROM DATE column with dedicated "Front Start" option
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "FROM DATE",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        // Quick clickable "Front Start" button
                                        Text(
                                            text = if (isFrontStartSelected) "✓ Front Start" else "Front Start",
                                            color = if (isFrontStartSelected) Color(0xFFFFB300) else Color(0xFFFFB300).copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    if (earliestRecordDate != null) {
                                                        onCustomStartDateChange(earliestRecordDate)
                                                    } else {
                                                        Toast.makeText(context, "No flight records found", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                                .testTag("quick_front_start_btn")
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(Color(0xFF111827), shape = RoundedCornerShape(8.dp))
                                            .border(
                                                1.dp,
                                                if (isFrontStartSelected) Color(0xFFFFB300).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                showStartDatePicker = true
                                            }
                                            .padding(horizontal = 12.dp)
                                            .testTag("custom_start_date_box"),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = if (isFrontStartSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = customStartDate?.let { logbookDateFormatter.format(it) } ?: "Pick Date",
                                                    color = if (customStartDate == null) Color.White.copy(alpha = 0.4f) else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (customStartDate != null) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                            if (customStartDate != null) {
                                                IconButton(
                                                    onClick = { onCustomStartDateChange(null) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = Color.White.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Dedicated quick chip for Front Start
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isFrontStartSelected) Color(0xFFFFB300).copy(alpha = 0.15f) else Color(0xFF1F2937),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isFrontStartSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                if (earliestRecordDate != null) {
                                                    onCustomStartDateChange(earliestRecordDate)
                                                } else {
                                                    Toast.makeText(context, "No flight records found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                            .testTag("front_start_chip"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FirstPage,
                                            contentDescription = "Front Start",
                                            tint = if (isFrontStartSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (earliestRecordDate != null) "Front Start (${logbookDateFormatter.format(earliestRecordDate)})" else "Front Start",
                                            color = if (isFrontStartSelected) Color(0xFFFFB300) else Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            fontWeight = if (isFrontStartSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // TO DATE column with dedicated "To End" option
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "TO DATE",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        // Quick clickable "To End" button
                                        Text(
                                            text = if (isToEndSelected) "✓ To End" else "To End",
                                            color = if (isToEndSelected) Color(0xFF3CD070) else Color(0xFF3CD070).copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable {
                                                    if (latestRecordDate != null) {
                                                        onCustomEndDateChange(latestRecordDate)
                                                    } else {
                                                        Toast.makeText(context, "No flight records found", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                                .testTag("quick_to_end_btn")
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .background(Color(0xFF111827), shape = RoundedCornerShape(8.dp))
                                            .border(
                                                1.dp,
                                                if (isToEndSelected) Color(0xFF3CD070).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                showEndDatePicker = true
                                            }
                                            .padding(horizontal = 12.dp)
                                            .testTag("custom_end_date_box"),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = if (isToEndSelected) Color(0xFF3CD070) else Color.White.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = customEndDate?.let { logbookDateFormatter.format(it) } ?: "Pick Date",
                                                    color = if (customEndDate == null) Color.White.copy(alpha = 0.4f) else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (customEndDate != null) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                            if (customEndDate != null) {
                                                IconButton(
                                                    onClick = { onCustomEndDateChange(null) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear",
                                                        tint = Color.White.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Dedicated quick chip for To End
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isToEndSelected) Color(0xFF3CD070).copy(alpha = 0.15f) else Color(0xFF1F2937),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isToEndSelected) Color(0xFF3CD070) else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                if (latestRecordDate != null) {
                                                    onCustomEndDateChange(latestRecordDate)
                                                } else {
                                                    Toast.makeText(context, "No flight records found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                            .testTag("to_end_chip"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LastPage,
                                            contentDescription = "To End",
                                            tint = if (isToEndSelected) Color(0xFF3CD070) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (latestRecordDate != null) "To End (${logbookDateFormatter.format(latestRecordDate)})" else "To End",
                                            color = if (isToEndSelected) Color(0xFF3CD070) else Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            fontWeight = if (isToEndSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (showStartDatePicker) {
                            CosmicDatePickerDialog(
                                initialDate = customStartDate ?: earliestRecordDate ?: java.util.Date(),
                                onDateSelected = { date ->
                                    onCustomStartDateChange(date)
                                    showStartDatePicker = false
                                },
                                onDismiss = { showStartDatePicker = false }
                            )
                        }

                        if (showEndDatePicker) {
                            CosmicDatePickerDialog(
                                initialDate = customEndDate ?: latestRecordDate ?: java.util.Date(),
                                onDateSelected = { date ->
                                    onCustomEndDateChange(date)
                                    showEndDatePicker = false
                                },
                                onDismiss = { showEndDatePicker = false }
                            )
                        }
                    }

                    // PilotFunction Dropdown Filter (Multi-select)
                    val uniquePilotRoles = remember(allLogs) {
                        val roles = linkedSetOf("PIC", "FI", "SIC", "Co-Pilot", "Dual")
                        allLogs.forEach { log ->
                            val r = log.pilotRole.trim()
                            if (r.isNotEmpty()) {
                                if (isFlightInstructorRole(r)) {
                                    roles.add("FI")
                                } else if (!r.equals("PIC", ignoreCase = true)) {
                                    roles.add(r)
                                }
                            }
                        }
                        roles.toList()
                    }
                    MultiSelectDropdown(
                        label = "Pilot Function",
                        options = uniquePilotRoles,
                        selectedOptions = selectedPilotRoles,
                        onSelectionChange = onSelectedPilotRolesChange,
                        placeholder = "All Pilot Functions"
                    )

                    // BlockTime Dropdown Filter (Multi-select)
                    val blockTimeOptions = listOf("Below 9:30", "9:00 - 13:00", "12:00 - 14:00", "More than 13:00")
                    MultiSelectDropdown(
                        label = "Block Time",
                        options = blockTimeOptions,
                        selectedOptions = selectedBlockTimes,
                        onSelectionChange = onSelectedBlockTimesChange,
                        placeholder = "All Block Times"
                    )

                    // Clear All Filters Button
                    Button(
                        onClick = onClearAllFilters,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Filters", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }



        if (displayedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (allLogs.isEmpty()) {
                            "No Flight Logs"
                        } else if (isNavigableGroupView && currentGroup != null) {
                            "No logs in ${currentGroup.groupKey}"
                        } else {
                            "No matching records found"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (allLogs.isEmpty()) {
                            "Tap the '+' button at the bottom to record your first flight log."
                        } else {
                            "Try adjusting your filters or navigate to another period."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // LazyColumn supporting swipe gesture navigation for Period view
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(isNavigableGroupView, safeGroupIndex, groupedLogs.size) {
                        if (isNavigableGroupView && groupedLogs.size > 1) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (totalDragOffset > 80f) {
                                        // Swiped Right -> Go to Previous Period
                                        if (safeGroupIndex > 0) {
                                            setGroupIndex(safeGroupIndex - 1)
                                        }
                                    } else if (totalDragOffset < -80f) {
                                        // Swiped Left -> Go to Next Period
                                        if (safeGroupIndex < groupedLogs.size - 1) {
                                            setGroupIndex(safeGroupIndex + 1)
                                        }
                                    }
                                    totalDragOffset = 0f
                                },
                                onDragCancel = {
                                    totalDragOffset = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragOffset += dragAmount
                                }
                            )
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = displayedLogs,
                    key = { "${it.id}_${it.splitPart ?: 0}" },
                    contentType = { "flight_log_card" }
                ) { log ->
                    FlightLogCard(
                        log = log,
                        onDelete = { onDeleteRequest(log) },
                        onClick = { onEdit(log) },
                        onNext = { onNext(log) },
                        onReturn = { onReturn(log) },
                        onDuplicate = { onDuplicate(log) },
                        dbAirports = dbAirports,
                        filterCriteriaText = activeFilterCriteriaText
                    )
                }
            }
        }
    }
}
