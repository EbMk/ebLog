package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

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

        // 3-crew augmented: 2/3 credit
        assertEquals(400, calculateProratedMinutes(600, 3))

        // 4-crew augmented: 1/2 credit
        assertEquals(300, calculateProratedMinutes(600, 4))
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
}
