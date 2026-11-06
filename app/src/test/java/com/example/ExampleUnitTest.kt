package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test
import com.example.util.LogbookPrintGenerator
import com.example.util.LogbookPrintFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testSelectAllDataOnDoubleTap() {
        val sampleData = "KJFK"
        val valueState = TextFieldValue(text = sampleData, selection = TextRange(sampleData.length))
        
        // Simulating double-tap select all on field with data
        val updatedState = if (valueState.text.isNotEmpty()) {
            valueState.copy(selection = TextRange(0, valueState.text.length))
        } else {
            valueState
        }

        assertEquals(0, updatedState.selection.start)
        assertEquals(sampleData.length, updatedState.selection.end)
        assertEquals(sampleData, updatedState.text.substring(updatedState.selection.start, updatedState.selection.end))
    }

    @Test
    fun testSelectAllOnEmptyFieldDoesNotChangeSelection() {
        val emptyData = ""
        val valueState = TextFieldValue(text = emptyData, selection = TextRange(0))
        
        val updatedState = if (valueState.text.isNotEmpty()) {
            valueState.copy(selection = TextRange(0, valueState.text.length))
        } else {
            valueState
        }

        assertEquals(0, updatedState.selection.start)
        assertEquals(0, updatedState.selection.end)
    }

    @Test
    fun testFlightInstructorRoleDetection() {
        assertTrue(isFlightInstructorRole("FI"))
        assertTrue(isFlightInstructorRole("fi"))
        assertTrue(isFlightInstructorRole("FI (Instructor)"))
        assertTrue(isFlightInstructorRole("Flight Instructor"))
        assertTrue(isFlightInstructorRole("Instructor"))
        assertTrue(isFlightInstructorRole("FI + PIC"))
        assertFalse(isFlightInstructorRole("PIC"))
        assertFalse(isFlightInstructorRole("SIC"))
        assertFalse(isFlightInstructorRole("Co-Pilot"))
    }

    @Test
    fun testPicRoleCalculationConsidersFI() {
        assertTrue(isPicRole("PIC"))
        assertTrue(isPicRole("Captain"))
        assertTrue(isPicRole("FI", includeFI = true))
        assertTrue(isPicRole("FI (Instructor)", includeFI = true))
        assertTrue(isPicRole("Flight Instructor", includeFI = true))
        assertFalse(isPicRole("SIC", includeFI = true))
        assertFalse(isPicRole("Co-Pilot", includeFI = true))
        assertFalse(isPicRole("Dual", includeFI = true))
    }

    @Test
    fun testFormatPilotRoleDisplay() {
        assertEquals("FI + PIC", formatPilotRoleDisplay("FI"))
        assertEquals("FI + PIC", formatPilotRoleDisplay("FI (Instructor)"))
        assertEquals("FI + PIC", formatPilotRoleDisplay("Flight Instructor"))
        assertEquals("PIC", formatPilotRoleDisplay("PIC"))
        assertEquals("SIC", formatPilotRoleDisplay("SIC"))
        assertEquals("Co-Pilot", formatPilotRoleDisplay("Co-Pilot"))
    }

    @Test
    fun testRoleFilterMatchesFIAndPIC() {
        // When filter is empty -> all match
        assertTrue(matchesFlightRole("FI", emptySet()))
        assertTrue(matchesFlightRole("PIC", emptySet()))
        assertTrue(matchesFlightRole("SIC", emptySet()))

        // When FI only is selected: ONLY FI flights match, pure PIC does NOT match
        val fiOnlyFilter = setOf("FI")
        assertTrue(matchesFlightRole("FI", fiOnlyFilter))
        assertTrue(matchesFlightRole("FI (Instructor)", fiOnlyFilter))
        assertFalse(matchesFlightRole("PIC", fiOnlyFilter))
        assertFalse(matchesFlightRole("SIC", fiOnlyFilter))

        // When PIC is selected: both PIC and all FI flights match
        val picFilter = setOf("PIC")
        assertTrue(matchesFlightRole("PIC", picFilter))
        assertTrue(matchesFlightRole("FI", picFilter))
        assertTrue(matchesFlightRole("FI (Instructor)", picFilter))
        assertFalse(matchesFlightRole("SIC", picFilter))

        // When SIC is selected: only SIC matches
        val sicFilter = setOf("SIC")
        assertFalse(matchesFlightRole("PIC", sicFilter))
        assertFalse(matchesFlightRole("FI", sicFilter))
        assertTrue(matchesFlightRole("SIC", sicFilter))
    }

    @Test
    fun testFormatApproachTypeDisplay() {
        assertEquals("None / Visual", formatApproachTypeDisplay(""))
        assertEquals("None / Visual", formatApproachTypeDisplay("None"))
        assertEquals("ILS Cat III", formatApproachTypeDisplay("ILS Cat III"))
        assertEquals("(A/L)P (Automatic Landing Practice)", formatApproachTypeDisplay("(A/L)P"))
        assertEquals("(A/L)P - Rwy 27L (Automatic Landing Practice)", formatApproachTypeDisplay("(A/L)P - Rwy 27L"))
    }

    @Test
    fun testProratedMinutesCalculationForAugmentedCrew() {
        // Standard 2-crew or single pilot: 100% credit
        assertEquals(600, calculateProratedMinutes(600, 2))
        assertEquals(600, calculateProratedMinutes(600, 1))
        assertEquals(15, calculateProratedMinutes(15, 2))
        assertEquals(7, calculateProratedMinutes(7, 1))

        // 3-crew augmented: 2/3 credit (standard calculation without rounding up)
        assertEquals(400, calculateProratedMinutes(600, 3)) // 600 * 2/3 = 400
        assertEquals(4, calculateProratedMinutes(6, 3))     // 6 * 2/3 = 4
        assertEquals(6, calculateProratedMinutes(10, 3))    // 10 * 2/3 = 6.666... -> 6
        assertEquals(400, calculateProratedMinutes(601, 3)) // 601 * 2/3 = 400.666... -> 400

        // 4-crew augmented: 1/2 credit (standard calculation without rounding up)
        assertEquals(300, calculateProratedMinutes(600, 4)) // 600 / 2 = 300
        assertEquals(7, calculateProratedMinutes(14, 4))    // 14 / 2 = 7
        assertEquals(7, calculateProratedMinutes(15, 4))    // 15 / 2 = 7.5 -> 7
        assertEquals(300, calculateProratedMinutes(601, 4)) // 601 / 2 = 300.5 -> 300
    }

    @Test
    fun testRollingLimitProratedMinutesInPeriod() {
        val boundaryCal = java.util.Calendar.getInstance()
        boundaryCal.set(2026, java.util.Calendar.JUNE, 1, 0, 0, 0)
        val boundaryDate = boundaryCal.time

        // 4-crew flight: 600 total minutes (10h actual) should only count as 300 minutes (5h prorated)
        val log4Crew = FlightLog(
            id = 1,
            date = "15 Jun 2026",
            outTime = "08:00",
            inTime = "18:00",
            crew = "Capt A, FO B, FO C, Relief D"
        )
        val proratedMin = calculateProratedMinutesInPeriod(log4Crew, boundaryDate)
        assertEquals(300, proratedMin)
        assertEquals(5.0, proratedMin / 60.0, 0.001)
    }

    @Test
    fun testTraineeSeparationAndCrewCountExclusion() {
        val rawCrewWithTrainee = "Self, FO Smith | Student Pilot Bob"
        val (crewPart, traineePart) = parseCrewAndTrainee(rawCrewWithTrainee)
        assertEquals("Self, FO Smith", crewPart)
        assertEquals("Student Pilot Bob", traineePart)

        // Verify getCrewMembersOnly does NOT include the trainee
        val members = getCrewMembersOnly(rawCrewWithTrainee)
        assertEquals(listOf("Self", "FO Smith"), members)

        // Verify getCrewCount excludes the trainee: exactly 2 crew
        val crewCount = getCrewCount(rawCrewWithTrainee)
        assertEquals(2, crewCount)

        // Trainee formatted back matches storage
        val formatted = formatCrewWithTrainee("Self, FO Smith", "Student Pilot Bob")
        assertEquals("Self, FO Smith | Student Pilot Bob", formatted)

        // 600 min flight with 2 crew + 1 trainee must receive 100% credit (600 mins), NOT 66% (400 mins)
        val boundaryCal = java.util.Calendar.getInstance()
        boundaryCal.set(2026, java.util.Calendar.JUNE, 1, 0, 0, 0)
        val logWithTrainee = FlightLog(
            id = 2,
            date = "15 Jun 2026",
            outTime = "08:00",
            inTime = "18:00",
            crew = rawCrewWithTrainee,
            pilotRole = "FI"
        )
        val proratedMin = calculateProratedMinutesInPeriod(logWithTrainee, boundaryCal.time)
        assertEquals(600, proratedMin) // 2-crew gives 100% prorated time
    }

    @Test
    fun testParseCrewAndTraineeVariousFormats() {
        // Standard pipe delimiter
        val (c1, t1) = parseCrewAndTrainee("Capt Jones, FO Brown | Trainee Davis")
        assertEquals("Capt Jones, FO Brown", c1)
        assertEquals("Trainee Davis", t1)
        assertEquals(2, getCrewCount("Capt Jones, FO Brown | Trainee Davis"))

        // Hash delimiter support
        val (c2, t2) = parseCrewAndTrainee("Capt Jones, FO Brown # Trainee Davis")
        assertEquals("Capt Jones, FO Brown", c2)
        assertEquals("Trainee Davis", t2)
        assertEquals(2, getCrewCount("Capt Jones, FO Brown # Trainee Davis"))

        // Single pilot instructor + trainee
        val (c3, t3) = parseCrewAndTrainee("Self | Trainee Davis")
        assertEquals("Self", c3)
        assertEquals("Trainee Davis", t3)
        assertEquals(1, getCrewCount("Self | Trainee Davis"))

        // No trainee present
        val (c4, t4) = parseCrewAndTrainee("Self, FO Brown")
        assertEquals("Self, FO Brown", c4)
        assertEquals("", t4)
        assertEquals(2, getCrewCount("Self, FO Brown"))

        // Blank string
        val (c5, t5) = parseCrewAndTrainee("")
        assertEquals("", c5)
        assertEquals("", t5)
        assertEquals(2, getCrewCount(""))
    }

    @Test
    fun testNormalizeTimeStringVariousFormats() {
        // Standard formats
        assertEquals("14:30", normalizeTimeString("14:30"))
        assertEquals("08:45", normalizeTimeString("08:45"))
        assertEquals("08:45", normalizeTimeString("8:45"))
        assertEquals("14:30", normalizeTimeString("1430"))
        assertEquals("08:45", normalizeTimeString("0845"))
        assertEquals("08:45", normalizeTimeString("845"))
        assertEquals("00:00", normalizeTimeString("0000"))
        assertEquals("00:30", normalizeTimeString("0030"))

        // Time with seconds
        assertEquals("14:30", normalizeTimeString("14:30:00"))
        assertEquals("14:30", normalizeTimeString("14:30:00.000"))

        // AM / PM
        assertEquals("14:30", normalizeTimeString("2:30 PM"))
        assertEquals("02:30", normalizeTimeString("2:30 AM"))
        assertEquals("00:15", normalizeTimeString("12:15 AM"))
        assertEquals("12:15", normalizeTimeString("12:15 PM"))

        // Timezone / UTC indicators
        assertEquals("14:30", normalizeTimeString("1430Z"))
        assertEquals("14:30", normalizeTimeString("14:30 UTC"))
        assertEquals("14:30", normalizeTimeString("14:30 (UTC)"))
        assertEquals("14:30", normalizeTimeString("14:30+00:00"))

        // Full datetimes
        assertEquals("14:30", normalizeTimeString("2023-10-15 14:30"))
        assertEquals("14:30", normalizeTimeString("2023-10-15 14:30:00"))
        assertEquals("14:30", normalizeTimeString("15/10/2023 14:30"))
        assertEquals("14:30", normalizeTimeString("15.10.2023 14:30"))
        assertEquals("14:30", normalizeTimeString("15 Oct 2023 14:30"))
        assertEquals("14:30", normalizeTimeString("2023-10-15T14:30:00Z"))

        // Excel decimal time fraction (0.604166666666667 is 14:30:00)
        assertEquals("14:30", normalizeTimeString("0.604166666666667"))

        // Decimal hours vs dot format
        assertEquals("14:30", normalizeTimeString("14.30"))
        assertEquals("14:30", normalizeTimeString("14.5"))
        assertEquals("14:45", normalizeTimeString("14.75"))

        // Takeoff / Landing counts must NOT be treated as times
        assertEquals("", normalizeTimeString("1"))
        assertEquals("", normalizeTimeString("0"))
        assertEquals("", normalizeTimeString("2"))
        assertEquals("", normalizeTimeString("3"))

        // Empty / placeholders
        assertEquals("", normalizeTimeString(""))
        assertEquals("", normalizeTimeString("-"))
        assertEquals("", normalizeTimeString("--:--"))
        assertEquals("", normalizeTimeString("null"))
        assertEquals("", normalizeTimeString("N/A"))
    }

    @Test
    fun testExtractDateFromDateTime() {
        assertEquals("15 Oct 23", extractDateFromDateTime("2023-10-15 14:30"))
        assertEquals("15 Oct 23", extractDateFromDateTime("2023-10-15T14:30:00Z"))
        assertEquals("15 Oct 23", extractDateFromDateTime("15/10/2023 14:30"))
        assertNull(extractDateFromDateTime("14:30"))
        assertNull(extractDateFromDateTime("1430"))
    }

    @Test
    fun testParseCsvLineWithDifferentDelimiters() {
        val commaLine = "123,03 Oct 26,ET-AOS,B788,14:30,16:45"
        val parsedComma = parseCsvLine(commaLine, ',')
        assertEquals(6, parsedComma.size)
        assertEquals("14:30", parsedComma[4])
        assertEquals("16:45", parsedComma[5])

        val semiLine = "123;03 Oct 26;ET-AOS;B788;14:30;16:45"
        val parsedSemi = parseCsvLine(semiLine, ';')
        assertEquals(6, parsedSemi.size)
        assertEquals("14:30", parsedSemi[4])
        assertEquals("16:45", parsedSemi[5])
    }

    @Test
    fun testGetMatchedFlightLogDatabaseData() {
        val testLog = FlightLog(
            id = 1,
            flightNum = "BA123",
            date = "05 Oct 26",
            tailNumber = "G-ABCD",
            aircraftType = "Boeing 737-800",
            crew = "Self, Captain John Smith, FO David Miller",
            remarks = "Autoland crosswind test at runway 27R",
            fromCode = "EGLL",
            toCode = "KJFK"
        )

        // Matching crew name returns ONLY the full crew name of the searched item (leaving out other crew members like 'Self')
        assertEquals("Captain John Smith", getMatchedFlightLogDatabaseData(testLog, "Smith"))
        assertEquals("Captain John Smith", getMatchedFlightLogDatabaseData(testLog, "John"))
        assertEquals("FO David Miller", getMatchedFlightLogDatabaseData(testLog, "David"))
        assertEquals("FO David Miller", getMatchedFlightLogDatabaseData(testLog, "Miller"))

        // Matching remarks returns full remarks stored in database
        assertEquals("Autoland crosswind test at runway 27R", getMatchedFlightLogDatabaseData(testLog, "crosswind"))

        // Matching aircraft returns full aircraftType stored in database
        assertEquals("Boeing 737-800", getMatchedFlightLogDatabaseData(testLog, "737"))

        // Matching tail number returns full tailNumber stored in database
        assertEquals("G-ABCD", getMatchedFlightLogDatabaseData(testLog, "ABCD"))

        // Empty filter returns empty string
        assertEquals("", getMatchedFlightLogDatabaseData(testLog, ""))
    }

    @Test
    fun testViewContinuityTargetRecordLookupInAllRecords() {
        val log1 = FlightLog(id = 1, date = "2024-01-10", outTime = "10:00", inTime = "12:00")
        val log2 = FlightLog(id = 2, date = "2024-02-15", outTime = "14:00", inTime = "16:00")
        val log3 = FlightLog(id = 3, date = "2024-03-20", outTime = "08:00", inTime = "10:00")
        val allLogs = listOf(log1, log2, log3)

        // When log2 is on top of the view, switching to All Records finds itemIdx == 1
        val targetId = 2
        val targetLog = allLogs.find { it.id == targetId }
        assertNotNull(targetLog)
        val itemIdx = allLogs.indexOfFirst { it.id == targetLog!!.id }
        assertEquals(1, itemIdx)
    }

    @Test
    fun testViewContinuityTargetRecordLookupAcrossSortToggle() {
        val log1 = FlightLog(id = 1, date = "2024-01-10", outTime = "10:00", inTime = "12:00")
        val log2 = FlightLog(id = 2, date = "2024-02-15", outTime = "14:00", inTime = "16:00")
        val log3 = FlightLog(id = 3, date = "2024-03-20", outTime = "08:00", inTime = "10:00")

        // Recent to Last sort (descending)
        val recentFirstList = listOf(log3, log2, log1)
        val topLogBeforeToggle = recentFirstList.first() // log3 is at top
        assertEquals(3, topLogBeforeToggle.id)

        // Toggled to First to Last (ascending)
        val lastFirstList = listOf(log1, log2, log3)
        // Finding the record that was on top of the view in the new sort order
        val targetIdxAfterToggle = lastFirstList.indexOfFirst { it.id == topLogBeforeToggle.id }
        assertEquals(2, targetIdxAfterToggle)
        assertEquals(topLogBeforeToggle.id, lastFirstList[targetIdxAfterToggle].id)
    }

    @Test
    fun testViewContinuityTargetRecordLookupInMonthGrouping() {
        val logJan = FlightLog(id = 10, date = "2024-01-15", flightNum = "BA101")
        val logFeb1 = FlightLog(id = 20, date = "2024-02-10", flightNum = "BA201")
        val logFeb2 = FlightLog(id = 21, date = "2024-02-20", flightNum = "BA202")
        val logMar = FlightLog(id = 30, date = "2024-03-05", flightNum = "BA301")

        // Suppose user is looking at logFeb2 in All Records or Month view
        val targetId = 21
        val targetLog = listOf(logJan, logFeb1, logFeb2, logMar).find { it.id == targetId }
        assertNotNull(targetLog)

        // Simulated grouped logs
        val groupJan = FlightLogGroup("January 2024", 1704067200000L, listOf(logJan), 0, 0, 0, 0, 0, 0)
        val groupFeb = FlightLogGroup("February 2024", 1706745600000L, listOf(logFeb1, logFeb2), 0, 0, 0, 0, 0, 0)
        val groupMar = FlightLogGroup("March 2024", 1709251200000L, listOf(logMar), 0, 0, 0, 0, 0, 0)
        val groupedLogs = listOf(groupMar, groupFeb, groupJan) // Recent first

        val groupIdx = groupedLogs.indexOfFirst { g -> g.logs.any { it.id == targetLog!!.id } }
        assertEquals(1, groupIdx) // groupFeb is at index 1

        val itemIdxInGroup = groupedLogs[groupIdx].logs.indexOfFirst { it.id == targetLog!!.id }
        assertEquals(1, itemIdxInGroup) // logFeb2 is at index 1 in groupFeb
    }

    @Test
    fun testViewContinuityTargetRecordLookupInYearGrouping() {
        val log2023 = FlightLog(id = 101, date = "2023-11-20", flightNum = "SQ101")
        val log2024A = FlightLog(id = 201, date = "2024-02-15", flightNum = "SQ201")
        val log2024B = FlightLog(id = 202, date = "2024-08-10", flightNum = "SQ202")

        val targetLog = log2024B

        val group2024 = FlightLogGroup("2024", 1704067200000L, listOf(log2024B, log2024A), 0, 0, 0, 0, 0, 0)
        val group2023 = FlightLogGroup("2023", 1672531200000L, listOf(log2023), 0, 0, 0, 0, 0, 0)
        val groupedYears = listOf(group2024, group2023)

        val yearGroupIdx = groupedYears.indexOfFirst { g -> g.logs.any { it.id == targetLog.id } }
        assertEquals(0, yearGroupIdx)

        val itemIdx = groupedYears[yearGroupIdx].logs.indexOfFirst { it.id == targetLog.id }
        assertEquals(0, itemIdx)
    }

    @Test
    fun testViewContinuitySortToggleInMonthGroup() {
        val logA = FlightLog(id = 301, date = "2024-05-02", outTime = "09:00", inTime = "11:00")
        val logB = FlightLog(id = 302, date = "2024-05-15", outTime = "14:00", inTime = "16:00")
        val logC = FlightLog(id = 303, date = "2024-05-28", outTime = "18:00", inTime = "20:00")

        // In Newest First sort: logC, logB, logA
        val monthLogsNewest = listOf(logC, logB, logA)
        // Top visible log is logB
        val topLogId = monthLogsNewest[1].id
        assertEquals(302, topLogId)

        // When toggled to Oldest First (First to Last): logA, logB, logC
        val monthLogsOldest = listOf(logA, logB, logC)
        val newTargetIdx = monthLogsOldest.indexOfFirst { it.id == topLogId }
        assertEquals(1, newTargetIdx)
        assertEquals(302, monthLogsOldest[newTargetIdx].id)
    }

    @Test
    fun testParseBlockMinutesFromLog() {
        assertEquals(150, parseBlockMinutesFromLog("02:30"))
        assertEquals(150, parseBlockMinutesFromLog("2:30"))
        assertEquals(150, parseBlockMinutesFromLog("2.5"))
        assertEquals(150, parseBlockMinutesFromLog("2.50"))
        assertEquals(60, parseBlockMinutesFromLog("1.0"))
        assertEquals(60, parseBlockMinutesFromLog("01:00"))
        assertEquals(0, parseBlockMinutesFromLog("00:00"))
        assertEquals(null, parseBlockMinutesFromLog(""))
        assertEquals(null, parseBlockMinutesFromLog("abc"))
    }

    @Test
    fun testFormatMinutesToHoursClean() {
        assertEquals("02:30", formatMinutesToHoursClean(150))
        assertEquals("00:05", formatMinutesToHoursClean(5))
        assertEquals("00:00", formatMinutesToHoursClean(0))
        assertEquals("150:00", formatMinutesToHoursClean(9000))
        assertEquals("10:00", formatMinutesToHoursClean(600))
    }

    @Test
    fun testNormalizeBlockHours() {
        assertEquals("02:30", normalizeBlockHours("2.5"))
        assertEquals("02:30", normalizeBlockHours("2.50"))
        assertEquals("02:30", normalizeBlockHours("02:30"))
        assertEquals("02:30", normalizeBlockHours("2:30"))
        assertEquals("01:45", normalizeBlockHours("1.75"))
        assertEquals("", normalizeBlockHours(""))
    }

    @Test
    fun testLogbookPrintTotalsCalculation() {
        val testLogs = listOf(
            FlightLog(
                id = 1,
                date = "2026-05-10",
                outTime = "08:00",
                inTime = "10:30",
                offTime = "08:15",
                onTime = "10:15",
                blockHours = "02:30",
                landingDay = 1,
                landingNight = 0,
                nightTime = "00:00",
                pilotRole = "PIC",
                crew = "Capt Pilot | Trainee Mark"
            ),
            FlightLog(
                id = 2,
                date = "2026-05-11",
                outTime = "18:00",
                inTime = "21:30",
                offTime = "18:15",
                onTime = "21:15",
                blockHours = "03:30",
                landingDay = 0,
                landingNight = 1,
                nightTime = "01:30",
                pilotRole = "FI",
                crew = "Capt Pilot, FO Jane | Trainee Luke"
            ),
            FlightLog(
                id = 3,
                date = "2026-05-12",
                outTime = "12:00",
                inTime = "14:00",
                offTime = "12:10",
                onTime = "13:50",
                blockHours = "02:00",
                landingDay = 1,
                landingNight = 0,
                nightTime = "00:00",
                pilotRole = "SIC",
                crew = "Capt Other, Pilot Pilot"
            )
        )

        val totals = com.example.util.LogbookPrintGenerator.calculateTotals(testLogs)

        assertEquals(3, totals.totalFlights)
        assertEquals(480, totals.totalBlockMinutes) // 150 + 210 + 120 = 480 mins = 08:00
        assertEquals("08:00", formatMinutesToHoursClean(totals.totalBlockMinutes))
        assertEquals(400, totals.totalFlightMinutes) // (10:15-8:15)=120 + (21:15-18:15)=180 + (13:50-12:10)=100 = 400 mins = 06:40
        assertEquals("06:40", formatMinutesToHoursClean(totals.totalFlightMinutes))
        assertEquals(90, totals.totalNightMinutes) // 1h30 = 90 min
        assertEquals("01:30", formatMinutesToHoursClean(totals.totalNightMinutes))
        assertEquals(2, totals.totalDayLandings)
        assertEquals(1, totals.totalNightLandings)
        assertEquals(3, totals.totalDayLandings + totals.totalNightLandings)
        assertEquals(360, totals.totalPicMinutes) // Log 1 (PIC: 150) + Log 2 (FI: 210, FI gets PIC credit) = 360
        assertEquals(120, totals.totalSicMinutes) // Log 3: 02:00
        assertEquals(210, totals.totalFiMinutes) // Log 2: 03:30
        assertEquals("06:00", formatMinutesToHoursClean(totals.totalPicMinutes))
        assertEquals("02:00", formatMinutesToHoursClean(totals.totalSicMinutes))
        assertEquals("03:30", formatMinutesToHoursClean(totals.totalFiMinutes))
    }

    @Test
    fun testLogbookPrintGeneratorHtmlAllFormats() {
        val testLogs = listOf(
            FlightLog(
                id = 1,
                date = "2026-05-10",
                aircraftType = "B738",
                tailNumber = "ET-ABC",
                fromCode = "HAAB",
                toCode = "HKJK",
                outTime = "08:00",
                inTime = "10:30",
                offTime = "08:15",
                onTime = "10:15",
                blockHours = "02:30",
                landingDay = 1,
                landingNight = 0,
                pilotRole = "FI",
                crew = "Self | Trainee Alice",
                remarks = "Training flight"
            )
        )

        val profile = com.example.data.UserProfileSettings(
            fullName = "Captain Pilot",
            role = "Captain",
            airline = "Ethiopian Airlines"
        )
        val startDate = java.util.Calendar.getInstance().apply { set(2026, 4, 1) }.time
        val endDate = java.util.Calendar.getInstance().apply { set(2026, 4, 31) }.time

        // Check EASA format
        val easaHtml = com.example.util.LogbookPrintGenerator.generateHtml(
            logs = testLogs,
            format = com.example.util.LogbookPrintFormat.EASA_FCL_050,
            startDate = startDate,
            endDate = endDate,
            profile = profile
        )
        assertTrue(easaHtml.contains("EASA Format"))
        assertTrue(easaHtml.contains("AMC1 FCL.050"))
        assertTrue(easaHtml.contains("HAAB"))
        assertTrue(easaHtml.contains("HKJK"))
        assertTrue(easaHtml.contains("02:30"))
        assertTrue(easaHtml.contains("Trainee"))
        assertTrue(easaHtml.contains("Trainee Alice"))
        assertTrue(easaHtml.contains("Captain Pilot"))

        // Check FAA format
        val faaHtml = com.example.util.LogbookPrintGenerator.generateHtml(
            logs = testLogs,
            format = com.example.util.LogbookPrintFormat.FAA_14_CFR_61,
            startDate = startDate,
            endDate = endDate,
            profile = profile
        )
        assertTrue(faaHtml.contains("14 CFR § 61.51"))
        assertTrue(faaHtml.contains("ET-ABC"))

        // Check Jeppesen format
        val jepHtml = com.example.util.LogbookPrintGenerator.generateHtml(
            logs = testLogs,
            format = com.example.util.LogbookPrintFormat.JEPPESEN_PRO,
            startDate = startDate,
            endDate = endDate,
            profile = profile
        )
        assertTrue(jepHtml.contains("Jeppesen Professional"))

        // Check Comprehensive format
        val compHtml = com.example.util.LogbookPrintGenerator.generateHtml(
            logs = testLogs,
            format = com.example.util.LogbookPrintFormat.COMPREHENSIVE_AIRLINE,
            startDate = startDate,
            endDate = endDate,
            profile = profile
        )
        assertTrue(compHtml.contains("Comprehensive Airline"))

        // Check Standard Pilot Logbook (Black & White 14-sector paginated ledger format)
        val stdLedgerHtml = com.example.util.LogbookPrintGenerator.generateHtml(
            logs = testLogs,
            format = com.example.util.LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = startDate,
            endDate = endDate,
            profile = profile
        )
        assertTrue(stdLedgerHtml.contains("Standard Pilot Logbook"))
        assertTrue(stdLedgerHtml.contains("bw-ledger-theme"))
        assertTrue(stdLedgerHtml.contains("bw-ledger-table"))
        assertTrue(stdLedgerHtml.contains("Page 1 of 1"))
        // Verify header banner, summary cards, and year label are removed
        assertFalse(stdLedgerHtml.contains("class=\"header-banner\""))
        assertFalse(stdLedgerHtml.contains("class=\"summary-cards\""))
        assertFalse(stdLedgerHtml.contains("YEAR:"))
        assertFalse(stdLedgerHtml.contains("#EBF5ED"))
        assertFalse(stdLedgerHtml.contains("#1B5E20"))
        // Verify columns and totals with standard abbreviations
        assertTrue(stdLedgerHtml.contains("AIRCRAFT CATEGORY & CLASS"))
        assertTrue(stdLedgerHtml.contains("SEL"))
        assertTrue(stdLedgerHtml.contains("SEA"))
        assertTrue(stdLedgerHtml.contains("MEL"))
        assertTrue(stdLedgerHtml.contains("ACTUAL<br>INST"))
        assertTrue(stdLedgerHtml.contains("SIM<br>INST<br>(HOOD)"))
        assertTrue(stdLedgerHtml.contains("FLIGHT<br>SIM"))
        assertTrue(stdLedgerHtml.contains("CONDITIONS OF FLIGHT"))
        assertTrue(stdLedgerHtml.contains("TYPE OF PILOTING TIME"))
        assertTrue(stdLedgerHtml.contains("TOTALS THIS PAGE"))
        assertTrue(stdLedgerHtml.contains("AMT. FORWARDED FROM PREVIOUS"))
        assertTrue(stdLedgerHtml.contains("TOTALS TO DATE"))
        assertTrue(stdLedgerHtml.contains("rowspan=\"3\" class=\"cert-signature-cell\""))
        assertTrue(compHtml.contains("Trainee: Alice") || compHtml.contains("Trainee"))
    }

    @Test
    fun testStandardPilotLogbookBlackAndWhitePaginationAndForwardedTotals() {
        // Create 26 flights (Page 1 has 24 sectors, Page 2 has 2 sectors + 22 blank rows)
        val multiLogs = (1..26).map { i ->
            FlightLog(
                id = i,
                date = "2026-06-%02d".format(if (i <= 28) i else 28),
                outTime = "08:00",
                inTime = "10:00", // 2h = 120min each
                offTime = "08:10",
                onTime = "09:50",
                flightNum = "FL%02d".format(i),
                fromCode = "JFK",
                toCode = "BOS",
                aircraftType = "C172",
                tailNumber = "N1234$i",
                blockHours = "02:00",
                landingDay = 1,
                pilotRole = "PIC"
            )
        }

        val startDate = java.util.Calendar.getInstance().apply { set(2026, 5, 1) }.time
        val endDate = java.util.Calendar.getInstance().apply { set(2026, 5, 30) }.time

        val html = com.example.util.LogbookPrintGenerator.generateHtml(
            logs = multiLogs,
            format = com.example.util.LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = startDate,
            endDate = endDate
        )

        // 1. Black and white: no green color
        assertFalse(html.contains("#EBF5ED"))
        assertFalse(html.contains("#1B5E20"))
        assertFalse(html.contains("#C8E6C9"))
        assertTrue(html.contains("bw-ledger-theme"))

        // 2. Remove entire header section
        assertFalse(html.contains("class=\"header-banner\""))

        // 3. Remove summary chips on top
        assertFalse(html.contains("class=\"summary-cards\""))

        // 4. Remove Year and Standard Pilot Log label on top
        assertFalse(html.contains("YEAR:"))
        assertFalse(html.contains("ledger-top-bar"))

        // 5. Add page number on top right corner
        assertTrue(html.contains("Page 1 of 2"))
        assertTrue(html.contains("Page 2 of 2"))
        assertTrue(html.contains("class=\"page-top-bar\""))
        assertTrue(html.contains("class=\"page-number\""))

        // 6. Every page displays 24 sectors, and totals at bottom
        assertTrue(html.contains("blank-row"))
        assertTrue(html.contains("TOTALS THIS PAGE"))
        assertTrue(html.contains("AMT. FORWARDED FROM PREVIOUS"))
        assertTrue(html.contains("TOTALS TO DATE"))

        // Page 1 duration: 24 flights * 2h = 48h -> 48:00
        // Page 1 forwarded: 00:00
        // Page 1 totals to date: 48:00
        // Page 2 forwarded: 48:00 (forwarded from Page 1 totals to date!)
        // Page 2 duration: 2 flights * 2h = 4h -> 04:00
        // Page 2 totals to date: 48h + 4h = 52h -> 52:00
        assertTrue("Page 1 has 48:00 block total", html.contains("48:00"))
        assertTrue("Page 2 has 04:00 page total", html.contains("04:00"))
        assertTrue("Page 2 has 52:00 to-date total", html.contains("52:00"))
    }

    @Test
    fun testLogbookCustomDateFrontStartAndToEndFilter() {
        val log1 = FlightLog(id = 1, date = "2024-01-10", outTime = "08:00", inTime = "10:00")
        val log2 = FlightLog(id = 2, date = "2025-06-15", outTime = "12:00", inTime = "14:00")
        val log3 = FlightLog(id = 3, date = "2026-03-20", outTime = "16:00", inTime = "18:00")
        val allLogs = listOf(log2, log1, log3) // unsorted

        val parsedDateLogs = allLogs.mapNotNull { log ->
            parseLogDate(log.date)?.let { d -> Pair(d, log) }
        }.sortedBy { it.first.time }

        val earliestDate = parsedDateLogs.first().first
        val latestDate = parsedDateLogs.last().first

        // Format dates
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        assertEquals("2024-01-10", sdf.format(earliestDate))
        assertEquals("2026-03-20", sdf.format(latestDate))

        // Test filtering logic for Front Start to To End (matches all 3 logs)
        val filteredFullSpan = allLogs.filter { log ->
            val logDate = parseLogDate(log.date) ?: return@filter false
            val startCal = Calendar.getInstance().apply { time = earliestDate }
            val endCal = Calendar.getInstance().apply {
                time = latestDate
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            val logCal = Calendar.getInstance().apply { time = logDate }
            !logCal.before(startCal) && !logCal.after(endCal)
        }
        assertEquals(3, filteredFullSpan.size)

        // Test Front Start to 2025-06-15 (matches log1 and log2)
        val intermediateDate = sdf.parse("2025-06-15")!!
        val filteredFrontStartToMid = allLogs.filter { log ->
            val logDate = parseLogDate(log.date) ?: return@filter false
            val startCal = Calendar.getInstance().apply { time = earliestDate }
            val endCal = Calendar.getInstance().apply {
                time = intermediateDate
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            val logCal = Calendar.getInstance().apply { time = logDate }
            !logCal.before(startCal) && !logCal.after(endCal)
        }
        assertEquals(2, filteredFrontStartToMid.size)
        assertTrue(filteredFrontStartToMid.any { it.id == 1 })
        assertTrue(filteredFrontStartToMid.any { it.id == 2 })

        // Test from 2025-06-15 To End (matches log2 and log3)
        val filteredMidToEnd = allLogs.filter { log ->
            val logDate = parseLogDate(log.date) ?: return@filter false
            val startCal = Calendar.getInstance().apply { time = intermediateDate }
            val endCal = Calendar.getInstance().apply {
                time = latestDate
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            val logCal = Calendar.getInstance().apply { time = logDate }
            !logCal.before(startCal) && !logCal.after(endCal)
        }
        assertEquals(2, filteredMidToEnd.size)
        assertTrue(filteredMidToEnd.any { it.id == 2 })
        assertTrue(filteredMidToEnd.any { it.id == 3 })
    }

    @Test
    fun testMonthSplitFlightSortingTopAndBottom() {
        // Month 1 (January):
        val janLogA = FlightLog(id = 1, date = "05 Jan 2026", outTime = "10:00", inTime = "12:00")
        val janLogB = FlightLog(id = 2, date = "20 Jan 2026", outTime = "14:00", inTime = "16:00")
        val janLogC = FlightLog(id = 3, date = "31 Jan 2026", outTime = "22:00", inTime = "23:30")
        val janLogD_part1 = FlightLog(
            id = 4,
            date = "31 Jan 2026",
            outTime = "21:00",
            inTime = "00:00",
            splitPart = 1,
            splitMinutes = 180
        )

        val janLogs = listOf(janLogA, janLogB, janLogC, janLogD_part1)

        // 1. Month 1: Sort Recent to Last (sortRecentFirst = true)
        // Part 1 (Up to 00:00) represents the final flight of the month (ending at 24:00), so it belongs at the TOP!
        val janRecentFirst = janLogs.sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l1, l2) }
        assertEquals("In starting month, splitPart == 1 must be at the TOP when sorting Recent to Last", 4, janRecentFirst.first().id)
        assertEquals(1, janRecentFirst.first().splitPart)
        assertEquals(4, janRecentFirst[0].id)
        assertEquals(3, janRecentFirst[1].id)
        assertEquals(2, janRecentFirst[2].id)
        assertEquals(1, janRecentFirst[3].id)

        // 2. Month 1: Sort First to Last (sortRecentFirst = false)
        // Part 1 (Up to 00:00) belongs at the BOTTOM!
        val janOldFirst = janLogs.sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l2, l1) }
        assertEquals("In starting month, splitPart == 1 must be at the BOTTOM when sorting First to Last", 4, janOldFirst.last().id)
        assertEquals(1, janOldFirst.last().splitPart)
        assertEquals(1, janOldFirst[0].id)
        assertEquals(2, janOldFirst[1].id)
        assertEquals(3, janOldFirst[2].id)
        assertEquals(4, janOldFirst[3].id)

        // Month 2 (February):
        val febLogD_part2 = FlightLog(
            id = 4,
            date = "01 Feb 2026",
            displayDate = "2026-02-01",
            outTime = "00:00",
            inTime = "03:00",
            splitPart = 2,
            splitMinutes = 180
        )
        val febLogE = FlightLog(id = 5, date = "01 Feb 2026", outTime = "08:00", inTime = "11:00")
        val febLogF = FlightLog(id = 6, date = "15 Feb 2026", outTime = "13:00", inTime = "15:00")
        val febLogG = FlightLog(id = 7, date = "28 Feb 2026", outTime = "18:00", inTime = "20:00")

        val febLogs = listOf(febLogD_part2, febLogE, febLogF, febLogG)

        // 3. Month 2: Sort Recent to Last (sortRecentFirst = true)
        // Part 2 (After 00:00) started at 00:00 on Feb 1, so it is the earliest flight of the month -> belongs at the BOTTOM!
        val febRecentFirst = febLogs.sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l1, l2) }
        assertEquals("In new month, splitPart == 2 must be at the BOTTOM when sorting Recent to Last", 4, febRecentFirst.last().id)
        assertEquals(2, febRecentFirst.last().splitPart)
        assertEquals(7, febRecentFirst[0].id)
        assertEquals(6, febRecentFirst[1].id)
        assertEquals(5, febRecentFirst[2].id)
        assertEquals(4, febRecentFirst[3].id)

        // 4. Month 2: Sort First to Last (sortRecentFirst = false)
        // Part 2 (After 00:00) belongs at the TOP!
        val febOldFirst = febLogs.sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l2, l1) }
        assertEquals("In new month, splitPart == 2 must be at the TOP when sorting First to Last", 4, febOldFirst.first().id)
        assertEquals(2, febOldFirst.first().splitPart)
        assertEquals(4, febOldFirst[0].id)
        assertEquals(5, febOldFirst[1].id)
        assertEquals(6, febOldFirst[2].id)
        assertEquals(7, febOldFirst[3].id)

        // 5. Cross-month sequence: Feb 1 Part 2 is more recent than Jan 31 Part 1 in Recent-to-Old
        val crossList = listOf(janLogD_part1, febLogD_part2).sortedWith { l1, l2 -> compareFlightLogsRecentToOld(l1, l2) }
        assertEquals(febLogD_part2.splitPart, crossList[0].splitPart)
        assertEquals(janLogD_part1.splitPart, crossList[1].splitPart)
    }

    @Test
    fun testStandardPilotLogbookMergedSignatureRow() {
        val testLog = FlightLog(
            id = 1,
            date = "10 May 2026",
            aircraftType = "B738",
            tailNumber = "N12345",
            fromCode = "KLAX",
            toCode = "KSFO",
            outTime = "09:00",
            inTime = "10:30",
            crew = "Self",
            remarks = "Smooth flight"
        )

        val html = LogbookPrintGenerator.generateHtml(
            logs = listOf(testLog),
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )

        // 1. Must contain rowspan="3" on the cert-signature-cell spanning the 3 bottom rows under remarks
        assertTrue("Must contain rowspan=\"3\" on signature cell", html.contains("rowspan=\"3\" class=\"cert-signature-cell\""))

        // 2. Must contain the exact certification statement
        assertTrue(
            "Must contain certification statement",
            html.contains("I certify that the entries in this log are correct.")
        )

        // 3. Must contain the PILOT'S SIGNATURE prompt
        assertTrue(
            "Must contain PILOT\'S SIGNATURE prompt",
            html.contains("PILOT&#39;S SIGNATURE:") || html.contains("PILOT'S SIGNATURE:")
        )

        // 4. Must NOT contain the old separate outer cert box
        assertFalse(
            "Must not contain separate ledger-cert-container div below table",
            html.contains("ledger-cert-container")
        )
    }

    @Test
    fun testStandardPilotLogbookRemarksSectionOnlyDisplaysDatabaseRemarks() {
        val testLog = FlightLog(
            id = 1,
            date = "12 Jun 2026",
            aircraftType = "A320",
            tailNumber = "N999AA",
            fromCode = "KJFK",
            toCode = "KBOS",
            outTime = "12:00",
            inTime = "13:15",
            flightNum = "AA100",
            crew = "Capt Sully, FO Skiles | Trainee Dave",
            remarks = "Visual approach runway 22L"
        )

        val html = LogbookPrintGenerator.generateHtml(
            logs = listOf(testLog),
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )

        // Remarks must strictly display the database remarks
        assertTrue(
            "Remarks cell must display the database remarks",
            html.contains("<td class=\"align-left\">Visual approach runway 22L</td>")
        )

        // Must NOT include the flightNum, crew prefix, or trainee tag inside the standard logbook remarks
        assertFalse(
            "Remarks must not include flight number in standard logbook",
            html.contains("AA100 &bull; Visual approach")
        )
        assertFalse(
            "Remarks must not include crew tag in standard logbook",
            html.contains("Crew: Capt Sully")
        )
    }

    @Test
    fun testAllPrintingUsesProratedTimeOnly() {
        // 4-crew flight: 10 hours actual (600 minutes) -> 50% prorated credit = 5 hours (300 minutes)
        val log4Crew = FlightLog(
            id = 1,
            date = "15 Jul 2026",
            aircraftType = "B77W",
            tailNumber = "N777AA",
            fromCode = "KORD",
            toCode = "EGLL",
            outTime = "10:00",
            inTime = "20:00",
            crew = "Capt Alpha, FO Beta, Relief Gamma, Relief Delta",
            nightTime = "04:00", // 240 mins raw -> 120 mins prorated (02:00)
            pilotRole = "PIC"
        )

        // 3-crew flight: 6 hours actual (360 minutes) -> 2/3 prorated credit = 4 hours (240 minutes)
        val log3Crew = FlightLog(
            id = 2,
            date = "16 Jul 2026",
            aircraftType = "A359",
            tailNumber = "N350DL",
            fromCode = "KATL",
            toCode = "LFPG",
            outTime = "01:00",
            inTime = "07:00",
            crew = "Capt Alpha, FO Beta, Relief Gamma",
            nightTime = "03:00", // 180 mins raw -> 120 mins prorated (02:00)
            pilotRole = "SIC"
        )

        val logs = listOf(log4Crew, log3Crew)

        // Verify calculateTotals uses prorated minutes:
        // Total Block = 300 + 240 = 540 min (09:00)
        // Total Night = 120 + 120 = 240 min (04:00)
        // Total PIC = 300 min (05:00)
        // Total SIC = 240 min (04:00)
        val totals = LogbookPrintGenerator.calculateTotals(logs)
        assertEquals(540, totals.totalBlockMinutes)
        assertEquals(240, totals.totalNightMinutes)
        assertEquals(300, totals.totalPicMinutes)
        assertEquals(240, totals.totalSicMinutes)

        // Verify Standard Pilot Logbook HTML contains prorated values
        val standardHtml = LogbookPrintGenerator.generateHtml(
            logs = logs,
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )

        // 4-crew duration should show 05:00 (prorated), NOT 10:00
        assertTrue("4-crew flight duration must show 05:00", standardHtml.contains("<strong>05:00</strong>"))
        // 3-crew duration should show 04:00 (prorated), NOT 06:00
        assertTrue("3-crew flight duration must show 04:00", standardHtml.contains("<strong>04:00</strong>"))

        // Totals row should show 09:00 (540 mins)
        assertTrue("Totals must show 09:00 prorated", standardHtml.contains("<strong>09:00</strong>"))

        // Verify Jeppesen table HTML also uses prorated values
        val jeppHtml = LogbookPrintGenerator.generateHtml(
            logs = logs,
            format = LogbookPrintFormat.JEPPESEN_PRO,
            startDate = Date(0),
            endDate = Date()
        )
        assertTrue("Jeppesen 4-crew block must show 05:00", jeppHtml.contains("<strong>05:00</strong>"))
        assertTrue("Jeppesen totals block must show 09:00", jeppHtml.contains("<td>09:00</td>"))
    }

    @Test
    fun testActualInstrumentTimeDisplaysProratedFlightTime() {
        // Flight details:
        // Out: 10:00, Off: 10:30, On: 12:00, In: 12:30
        // Raw Block Time = 150 mins (02:30)
        // Raw Flight Time = 90 mins (01:30)
        // 3-Crew: 2/3 credit:
        // Prorated Block Time = 150 * 2/3 = 100 mins (01:40)
        // Prorated Flight Time = 90 * 2/3 = 60 mins (01:00)
        // Under IFR: Actual Instrument Time MUST display the prorated flight time (01:00), NOT block time (01:40)!
        val ifrLog = FlightLog(
            id = 1,
            date = "20 May 2026",
            aircraftType = "A320",
            tailNumber = "N12345",
            fromCode = "KJFK",
            toCode = "KORD",
            outTime = "10:00",
            offTime = "10:30",
            onTime = "12:00",
            inTime = "12:30",
            flightRules = "IFR",
            crew = "Capt Sully, FO Skiles, Relief Miller",
            pilotRole = "PIC"
        )

        // 1. Standard Pilot Logbook
        val stdHtml = LogbookPrintGenerator.generateHtml(
            logs = listOf(ifrLog),
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )
        // Duration is prorated block time = 01:40
        assertTrue("Duration must show prorated block time 01:40", stdHtml.contains("<strong>01:40</strong>"))
        // Actual instrument time column must show prorated flight time = 01:00
        assertTrue("Actual instrument time must show prorated flight time 01:00", stdHtml.contains("<td>01:00</td>"))

        // 2. EASA Format
        val easaHtml = LogbookPrintGenerator.generateHtml(
            logs = listOf(ifrLog),
            format = LogbookPrintFormat.EASA_FCL_050,
            startDate = Date(0),
            endDate = Date()
        )
        assertTrue("EASA IFR must show prorated flight time 01:00", easaHtml.contains("<td>01:00</td>"))

        // 3. FAA Format
        val faaHtml = LogbookPrintGenerator.generateHtml(
            logs = listOf(ifrLog),
            format = LogbookPrintFormat.FAA_14_CFR_61,
            startDate = Date(0),
            endDate = Date()
        )
        assertTrue("FAA Actual Inst. must show prorated flight time 01:00", faaHtml.contains("<td>01:00</td>"))
    }

    @Test
    fun testStandardLogbookPrintLineThicknessAndPageSizing() {
        val testLogs = (1..24).map { i ->
            FlightLog(
                id = i,
                date = "2026-07-%02d".format(if (i <= 28) i else 28),
                outTime = "08:00",
                inTime = "09:00",
                aircraftType = "B738",
                tailNumber = "N737$i",
                fromCode = "LAX",
                toCode = "SFO"
            )
        }

        val html = LogbookPrintGenerator.generateHtml(
            logs = testLogs,
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )

        // Exactly 24 rows fit on 1 page
        assertTrue("24 logs must fit on exactly 1 page", html.contains("Page 1 of 1"))

        // 25 logs must break onto Page 2
        val test25Logs = testLogs + listOf(
            FlightLog(id = 25, date = "2026-07-29", outTime = "10:00", inTime = "11:00", aircraftType = "B738", fromCode = "SFO", toCode = "SEA")
        )
        val html2Pages = LogbookPrintGenerator.generateHtml(
            logs = test25Logs,
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )
        assertTrue("25 logs must paginate to 2 pages", html2Pages.contains("Page 1 of 2"))
        assertTrue("25 logs must paginate to 2 pages", html2Pages.contains("Page 2 of 2"))

        // Verify uniform border thickness in print CSS
        assertTrue("Print CSS must specify 1px solid border", html.contains("border: 1px solid #000000 !important;"))
        assertTrue("Print CSS must enforce page-break-after: always", html.contains("page-break-after: always;"))

        // Verify standard abbreviations are present in headers
        assertTrue(html.contains(">SEL</th>"))
        assertTrue(html.contains(">SEA</th>"))
        assertTrue(html.contains(">MEL</th>"))
        assertTrue(html.contains("ACTUAL<br>INST"))
        assertTrue(html.contains("SIM<br>INST<br>(HOOD)"))
        assertTrue(html.contains("FLIGHT<br>SIM"))
    }

    @Test
    fun testSimulatorSessionParsingAndPrintTotals() {
        val simLog = FlightLog(
            id = 1,
            date = "01 Jan 25",
            flightNum = "",
            tailNumber = "",
            aircraftType = "A320",
            fromCode = "OMDB",
            toCode = "OMDB",
            outTime = "00:00",
            offTime = "00:00",
            onTime = "02:30",
            inTime = "02:30",
            blockHours = "02:30",
            remarks = "Level D FFS recurrent check",
            isSim = true
        )

        // Verify calculateTotals puts simLog into totalSimMinutes
        val totals = LogbookPrintGenerator.calculateTotals(listOf(simLog))
        assertEquals(150, totals.totalSimMinutes) // 2 hours 30 mins = 150 mins

        // Generate Standard FAA Green Ledger (now B&W)
        val html = LogbookPrintGenerator.generateHtml(
            logs = listOf(simLog),
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = Date(0),
            endDate = Date()
        )

        // Verify FLIGHT SIM column contains 02:30
        assertTrue("Print HTML must contain 02:30 in FLIGHT SIM column", html.contains("02:30"))
        // Aircraft ident displays SIM when tail is blank
        assertTrue("Print HTML must show SIM ident", html.contains("SIM"))
        // Remarks are printed
        assertTrue("Print HTML must contain remarks", html.contains("Level D FFS recurrent check"))
    }

    @Test
    fun testFlightLogSimModel() {
        val simLog = FlightLog(
            id = 101,
            date = "05 Jan 25",
            flightNum = "",
            tailNumber = "",
            aircraftType = "B738",
            fromCode = "EGLL",
            toCode = "EGLL",
            outTime = "00:00",
            inTime = "03:00",
            blockHours = "03:00",
            remarks = "Type Rating Sim",
            isSim = true
        )
        assertTrue(simLog.isSim)
        assertEquals("B738", simLog.aircraftType)
        assertEquals("EGLL", simLog.fromCode)
        assertEquals("EGLL", simLog.toCode)
        assertEquals("03:00", simLog.blockHours)
        assertEquals("Type Rating Sim", simLog.remarks)
    }

    @Test
    fun testPreviousExperienceWithSim() {
        val prevExpSim = com.example.data.PreviousExperience(
            id = 1,
            aircraftType = "A320",
            pilotRole = "SIC",
            totalHours = 40.0,
            isSim = true
        )

        assertTrue(prevExpSim.isSim)
        assertEquals("A320", prevExpSim.aircraftType)
        assertEquals(40.0, prevExpSim.totalHours, 0.001)
    }

    @Test
    fun testSimLogsAndPrevExpAddedTogetherAndNotAddedToActualFlights() {
        val actualLog = FlightLog(
            id = 1,
            date = "10 Jan 25",
            flightNum = "ET101",
            tailNumber = "ET-ABC",
            aircraftType = "B738",
            fromCode = "HAAB",
            toCode = "HKJK",
            outTime = "00:00",
            inTime = "02:00",
            blockHours = "02:00",
            pilotRole = "PIC"
        )
        val priorActualLog = FlightLog(
            id = 2,
            date = "01 Jan 25",
            flightNum = "",
            tailNumber = "N1234",
            aircraftType = "C172",
            fromCode = "KJFK",
            toCode = "KBOS",
            outTime = "00:00",
            inTime = "01:30",
            blockHours = "01:30",
            pilotRole = "PIC"
        )
        val simLog = FlightLog(
            id = 3,
            date = "05 Jan 25",
            flightNum = "",
            tailNumber = "",
            aircraftType = "B738",
            fromCode = "HAAB",
            toCode = "HAAB",
            outTime = "00:00",
            inTime = "03:00",
            blockHours = "03:00",
            remarks = "Type Rating Sim",
            isSim = true,
            pilotRole = "PIC"
        )
        val prevExpFlight = com.example.data.PreviousExperience(
            id = 1,
            aircraftType = "C172",
            pilotRole = "PIC",
            totalHours = 50.0,
            isSim = false
        )
        val prevExpSim = com.example.data.PreviousExperience(
            id = 2,
            aircraftType = "A320",
            pilotRole = "PIC",
            totalHours = 20.0,
            isSim = true
        )

        val totals = LogbookPrintGenerator.calculateTotals(
            logs = listOf(actualLog, simLog),
            previousExperiences = listOf(prevExpFlight, prevExpSim),
            priorLogs = listOf(priorActualLog)
        )

        // Only actual flights are counted in totalFlights
        assertEquals(2, totals.totalFlights)
        // Actual block minutes = 2:00 (120) + 1:30 (90) + 50h (3000) = 3210 min (53:30). Simulator time is NOT added to actual flights!
        assertEquals(3210, totals.totalBlockMinutes)
        assertEquals(3210, totals.totalFlightMinutes)
        assertEquals(3210, totals.totalPicMinutes)
        assertEquals(0, totals.totalSicMinutes)
        // Simulator logs (3h = 180m) + simulator previous experience (20h = 1200m) are added together: 1380m (23:00)
        assertEquals(1380, totals.totalSimMinutes)
    }

    @Test
    fun testStandardPilotLogbookPrintForwardedTotalsWithPriorLogsAndPreviousExperience() {
        val currentLog = FlightLog(
            id = 1,
            date = "15 Jan 25",
            aircraftType = "B738",
            tailNumber = "ET-ABC",
            fromCode = "HAAB",
            toCode = "HKJK",
            outTime = "00:00",
            inTime = "02:00",
            blockHours = "02:00",
            pilotRole = "PIC"
        )
        val priorLog = FlightLog(
            id = 2,
            date = "02 Jan 25",
            aircraftType = "C172",
            tailNumber = "N1234",
            fromCode = "KJFK",
            toCode = "KBOS",
            outTime = "00:00",
            inTime = "01:30",
            blockHours = "01:30",
            pilotRole = "PIC"
        )
        val prevExpFlight = com.example.data.PreviousExperience(
            id = 1,
            aircraftType = "C172",
            pilotRole = "PIC",
            totalHours = 50.0,
            isSim = false
        )
        val prevExpSim = com.example.data.PreviousExperience(
            id = 2,
            aircraftType = "A320",
            pilotRole = "SIC",
            totalHours = 10.0,
            isSim = true
        )

        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JANUARY, 10)
        val startDate = cal.time
        cal.set(2025, Calendar.JANUARY, 20)
        val endDate = cal.time

        val html = LogbookPrintGenerator.generateHtml(
            logs = listOf(currentLog),
            format = LogbookPrintFormat.STANDARD_PILOT_LOGBOOK,
            startDate = startDate,
            endDate = endDate,
            priorLogs = listOf(priorLog),
            previousExperiences = listOf(prevExpFlight, prevExpSim)
        )

        // Verify HTML table structure and forwarded totals
        assertTrue(html.contains("AMT. FORWARDED FROM PREVIOUS"))
        assertTrue(html.contains("TOTALS THIS PAGE"))
        assertTrue(html.contains("TOTALS TO DATE"))
        // Forwarded flight duration = 50:00 + 01:30 = 51:30
        assertTrue(html.contains("51:30"))
        // Forwarded simulator time = 10:00
        assertTrue(html.contains("10:00"))
        // Page totals duration = 02:00
        assertTrue(html.contains("02:00"))
        // Totals to date duration = 51:30 + 02:00 = 53:30
        assertTrue(html.contains("53:30"))
    }
}




