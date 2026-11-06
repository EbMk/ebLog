package com.example.util

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.FlightLog
import com.example.calculateProratedMinutes
import com.example.calculateTimeDiffInMinutes
import com.example.formatMinutesToHoursClean
import com.example.parseBlockMinutesFromLog
import com.example.parseCrewAndTrainee
import com.example.getCrewCount
import com.example.isPicRole
import com.example.isFlightInstructorRole
import com.example.data.UserProfileSettings
import com.example.data.PreviousExperience
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Supported official pilot logbook standards researched online:
 * - EASA AMC1 FCL.050 (European Union standard 12-column layout)
 * - FAA 14 CFR § 61.51 (US Federal Aviation Administration compliant layout)
 * - Jeppesen Professional Pilot Logbook (JAR/FAR Pro commercial airline standard)
 * - Comprehensive Airline Audit Logbook (All flight times, out/off/on/in, crew and remarks)
 */
enum class LogbookPrintFormat(
    val title: String,
    val standardCode: String,
    val description: String
) {
    STANDARD_PILOT_LOGBOOK(
        title = "Standard Pilot Logbook",
        standardCode = "FAA Standard Ledger",
        description = "Official 26-column spread: 24 sectors per page, cumulative forwarded totals, page numbers, black & white"
    ),
    FAA_14_CFR_61(
        title = "FAA Standard",
        standardCode = "14 CFR § 61.51",
        description = "FAA compliant format: Category, Conditions, PIC, SIC, Dual, Landings, Approaches"
    ),
    EASA_FCL_050(
        title = "EASA Format",
        standardCode = "AMC1 FCL.050",
        description = "European standard layout: SE/ME, Multi-pilot, PIC, Landings, Night, IFR, FI"
    ),
    JEPPESEN_PRO(
        title = "Jeppesen Professional",
        standardCode = "JAR / FAR Pro",
        description = "High-density airline standard: Block, Flight, Night, Landings, Capacity, Crew"
    ),
    COMPREHENSIVE_AIRLINE(
        title = "Comprehensive Airline",
        standardCode = "Company Audit",
        description = "Complete flight record with Out/Off/On/In, Block, Flight, Landings, Crew, Trainee & Remarks"
    )
}

enum class DateRangePreset(val label: String) {
    CURRENT_MONTH("Current Month"),
    CURRENT_YEAR("Current Year"),
    ENTIRE_LOGBOOK("Entire Log Book"),
    CUSTOM("Custom Range")
}

data class LogbookPrintTotals(
    val totalFlights: Int,
    val totalBlockMinutes: Int,
    val totalFlightMinutes: Int,
    val totalNightMinutes: Int,
    val totalDayLandings: Int,
    val totalNightLandings: Int,
    val totalPicMinutes: Int,
    val totalSicMinutes: Int,
    val totalFiMinutes: Int,
    val totalSimMinutes: Int = 0
)

object LogbookPrintGenerator {

    /**
     * Calculates totals across the given flight logs, previous experience, and prior logs using prorated time.
     * All times are tracked in integer minutes and formatted as HH:MM.
     */
    fun calculateTotals(
        logs: List<FlightLog>,
        previousExperiences: List<PreviousExperience> = emptyList(),
        priorLogs: List<FlightLog> = emptyList()
    ): LogbookPrintTotals {
        var totalFlights = 0
        var totalBlockMin = 0
        var totalFlightMin = 0
        var totalNightMin = 0
        var totalDayLdg = 0
        var totalNightLdg = 0
        var totalPicMin = 0
        var totalSicMin = 0
        var totalFiMin = 0
        var totalSimMin = 0

        val allFlightLogs = logs + priorLogs
        allFlightLogs.forEach { log ->
            val crewCount = getCrewCount(log.crew)
            val rawBlockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)

            val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
            val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)

            val isSimLog = log.isSim || log.aircraftType.contains("SIM", ignoreCase = true) || log.remarks.contains("SIM", ignoreCase = true) || log.remarks.contains("FFS", ignoreCase = true)
            if (isSimLog) {
                // Simulator logs added together into totalSimMin only; not added to actual flights
                totalSimMin += blockMin
            } else {
                totalFlights += 1
                totalBlockMin += blockMin
                totalFlightMin += flightMin

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
                totalNightMin += nightMin

                totalDayLdg += log.landingDay
                totalNightLdg += log.landingNight

                val role = log.pilotRole.trim()
                if (isPicRole(role, includeFI = true)) {
                    totalPicMin += blockMin
                } else {
                    totalSicMin += blockMin
                }
                if (isFlightInstructorRole(role)) {
                    totalFiMin += blockMin
                }
            }
        }

        // Add previous experience data: sim to totalSimMin only; non-sim to actual flight totals
        previousExperiences.forEach { exp ->
            val expMin = Math.round(exp.totalHours * 60).toInt()
            if (exp.isSim) {
                totalSimMin += expMin
            } else {
                totalBlockMin += expMin
                totalFlightMin += expMin

                val role = exp.pilotRole.trim()
                if (isPicRole(role, includeFI = true)) {
                    totalPicMin += expMin
                } else {
                    totalSicMin += expMin
                }
                if (isFlightInstructorRole(role)) {
                    totalFiMin += expMin
                }
            }
        }

        return LogbookPrintTotals(
            totalFlights = totalFlights,
            totalBlockMinutes = totalBlockMin,
            totalFlightMinutes = totalFlightMin,
            totalNightMinutes = totalNightMin,
            totalDayLandings = totalDayLdg,
            totalNightLandings = totalNightLdg,
            totalPicMinutes = totalPicMin,
            totalSicMinutes = totalSicMin,
            totalFiMinutes = totalFiMin,
            totalSimMinutes = totalSimMin
        )
    }

    /**
     * Generates a fully responsive, print-ready HTML document for the selected official logbook format.
     */
    fun generateHtml(
        logs: List<FlightLog>,
        format: LogbookPrintFormat,
        startDate: Date,
        endDate: Date,
        profile: UserProfileSettings? = null,
        priorLogs: List<FlightLog> = emptyList(),
        previousExperiences: List<PreviousExperience> = emptyList()
    ): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val logDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val genDateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US)

        val pilotName = profile?.fullName?.ifBlank { "Certified Flight Crew" } ?: "Certified Flight Crew"
        val effectivePrevExp = if (previousExperiences.isNotEmpty()) previousExperiences else (profile?.getPreviousExperiences() ?: emptyList())
        val totals = calculateTotals(logs, effectivePrevExp, priorLogs)

        val totalBlockStr = formatMinutesToHoursClean(totals.totalBlockMinutes)
        val totalFlightStr = formatMinutesToHoursClean(totals.totalFlightMinutes)
        val totalNightStr = formatMinutesToHoursClean(totals.totalNightMinutes)
        val totalPicStr = formatMinutesToHoursClean(totals.totalPicMinutes)
        val totalSicStr = formatMinutesToHoursClean(totals.totalSicMinutes)
        val totalFiStr = formatMinutesToHoursClean(totals.totalFiMinutes)

        val isBwStandardLedger = format == LogbookPrintFormat.STANDARD_PILOT_LOGBOOK
        val bodyClass = if (isBwStandardLedger) "class=\"bw-ledger-theme\"" else ""

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<title>${format.title} - ${format.standardCode}</title>\n")
        sb.append("<style>\n")
        sb.append("""
            @page {
                size: A4 landscape;
                margin: 4mm 5mm 4mm 5mm;
            }
            * {
                box-sizing: border-box;
                -webkit-print-color-adjust: exact !important;
                print-color-adjust: exact !important;
            }
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                font-size: 8pt;
                line-height: 1.25;
                color: #111827;
                margin: 0;
                padding: 8px;
                background-color: #ffffff;
            }
            .header-banner {
                display: flex;
                justify-content: space-between;
                align-items: flex-end;
                border-bottom: 2.5px solid #1E293B;
                padding-bottom: 6px;
                margin-bottom: 8px;
            }
            .title-section h1 {
                margin: 0;
                font-size: 15pt;
                font-weight: 800;
                letter-spacing: 0.5px;
                color: #0F172A;
                text-transform: uppercase;
            }
            .title-section .subtitle {
                font-size: 8.5pt;
                font-weight: 600;
                color: #475569;
                margin-top: 2px;
            }
            .meta-section {
                text-align: right;
                font-size: 7.5pt;
                color: #334155;
            }
            .meta-section strong {
                color: #0F172A;
            }
            .summary-cards {
                display: flex;
                gap: 8px;
                margin-bottom: 8px;
            }
            .summary-chip {
                flex: 1;
                background: #F8FAFC;
                border: 1px solid #CBD5E1;
                border-radius: 4px;
                padding: 4px 6px;
                text-align: center;
            }
            .summary-chip .label {
                font-size: 6.5pt;
                font-weight: 700;
                color: #64748B;
                text-transform: uppercase;
            }
            .summary-chip .val {
                font-size: 9.5pt;
                font-weight: 800;
                color: #0F172A;
                margin-top: 1px;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                table-layout: auto;
                font-size: 7pt;
            }
            th, td {
                border: 0.5px solid #94A3B8;
                padding: 3.5px 3px;
                text-align: center;
                vertical-align: middle;
            }
            th {
                background-color: #F1F5F9;
                color: #0F172A;
                font-weight: 700;
                font-size: 6.8pt;
                text-transform: uppercase;
            }
            tr:nth-child(even) td {
                background-color: #FAFAFA;
            }
            .align-left {
                text-align: left;
                padding-left: 4px;
            }
            .totals-row td {
                background-color: #E2E8F0 !important;
                font-weight: 800;
                border-top: 1.5px solid #0F172A;
                border-bottom: 2px solid #0F172A;
            }
            .footer-signoff {
                margin-top: 14px;
                padding-top: 6px;
                display: flex;
                justify-content: space-between;
                align-items: flex-end;
                font-size: 7pt;
                color: #475569;
            }
            .sign-line {
                width: 220px;
                border-bottom: 1px solid #1E293B;
                margin-bottom: 3px;
            }
            .badge-trainee {
                display: inline-block;
                background: #FEF3C7;
                color: #92400E;
                font-weight: 600;
                padding: 1px 3px;
                border-radius: 2px;
                font-size: 6.5pt;
            }

            /* Standard Pilot Logbook - Black & White Pagination Styles */
            body.bw-ledger-theme {
                background-color: #ffffff;
                color: #000000;
                margin: 0;
                padding: 0;
            }
            .bw-ledger-theme .logbook-page {
                page-break-after: always;
                break-after: page;
                page-break-inside: avoid;
                break-inside: avoid;
                margin-bottom: 0;
                box-sizing: border-box;
            }
            .bw-ledger-theme .logbook-page:last-child {
                page-break-after: avoid;
                break-after: avoid;
                margin-bottom: 0;
            }
            .bw-ledger-theme .page-top-bar {
                display: flex;
                justify-content: flex-end;
                align-items: center;
                margin-bottom: 2px;
            }
            .bw-ledger-theme .page-number {
                font-size: 7.5pt;
                font-weight: 800;
                color: #000000;
                letter-spacing: 0.5px;
                line-height: 1;
            }
            .bw-ledger-theme table.bw-ledger-table {
                width: 100%;
                border-collapse: collapse;
                table-layout: fixed;
                font-size: 5.8pt;
                border: 1px solid #000000 !important;
                background-color: #ffffff;
            }
            .bw-ledger-theme table.bw-ledger-table th {
                background-color: #ffffff;
                color: #000000;
                border: 1px solid #000000 !important;
                padding: 2px 1px;
                font-size: 5.0pt;
                font-weight: 800;
                line-height: 1.05;
                text-align: center;
                vertical-align: middle;
                overflow: hidden;
                text-overflow: clip;
                word-wrap: break-word;
                word-break: break-word;
                overflow-wrap: break-word;
                white-space: normal;
                box-sizing: border-box;
            }
            .bw-ledger-theme .v-hdr {
                writing-mode: vertical-rl;
                transform: rotate(180deg);
                white-space: nowrap;
                text-orientation: mixed;
                display: inline-block;
                margin: 0 auto;
                font-size: 4.8pt;
                line-height: 1;
            }
            .bw-ledger-theme table.bw-ledger-table tr {
                page-break-inside: avoid;
                break-inside: avoid;
            }
            .bw-ledger-theme table.bw-ledger-table td {
                border: 1px solid #000000 !important;
                padding: 2px 1px;
                color: #000000;
                font-size: 5.9pt;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
                background-color: #ffffff;
                height: 23px;
                box-sizing: border-box;
            }
            .bw-ledger-theme table.bw-ledger-table td.align-left {
                white-space: normal;
                word-break: break-word;
                font-size: 5.4pt;
                line-height: 1.05;
            }
            .bw-ledger-theme table.bw-ledger-table tr.blank-row td {
                height: 23px;
                background-color: #ffffff;
                border: 1px solid #000000 !important;
            }
            .bw-ledger-theme table.bw-ledger-table .totals-row td {
                background-color: #f7f7f7 !important;
                color: #000000;
                font-weight: 800;
                border: 1px solid #000000 !important;
                height: 23px;
                font-size: 5.9pt;
                padding: 2px 1px;
            }
            .bw-ledger-theme .section-divider {
                border-right: 1px solid #000000 !important;
            }
            .bw-ledger-theme table.bw-ledger-table td.cert-signature-cell {
                background-color: #ffffff !important;
                border: 1px solid #000000 !important;
                text-align: left;
                vertical-align: middle;
                padding: 3px 5px;
                line-height: 1.2;
                white-space: normal;
            }
            .bw-ledger-theme .cert-statement-text {
                font-style: italic;
                font-size: 4.8pt;
                color: #000000;
                margin-bottom: 3px;
            }
            .bw-ledger-theme .cert-sig-row {
                font-weight: 800;
                font-size: 5.0pt;
                color: #000000;
                display: flex;
                align-items: flex-end;
                letter-spacing: 0.3px;
            }
            .bw-ledger-theme .cert-sig-underline {
                display: inline-block;
                border-bottom: 1px solid #000000;
                flex-grow: 1;
                margin-left: 4px;
                height: 1px;
            }
            .bw-ledger-theme .badge-trainee {
                display: inline-block;
                border: 1px solid #000000;
                background: #ffffff;
                color: #000000;
                font-weight: 700;
                padding: 1px 3px;
                border-radius: 2px;
                font-size: 6pt;
            }

            @media print {
                @page {
                    size: A4 landscape;
                    margin: 8mm 6mm 8mm 6mm;
                }
                html, body {
                    margin: 0 !important;
                    padding: 0 !important;
                    background: #ffffff !important;
                    width: 100% !important;
                }
                body.bw-ledger-theme {
                    padding: 0 !important;
                    margin: 0 !important;
                }
                .bw-ledger-theme .logbook-page {
                    page-break-after: always !important;
                    break-after: page !important;
                    page-break-inside: avoid !important;
                    break-inside: avoid !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    height: auto !important;
                    overflow: visible !important;
                }
                .bw-ledger-theme .logbook-page:last-child {
                    page-break-after: avoid !important;
                    break-after: avoid !important;
                }
                .bw-ledger-theme table.bw-ledger-table {
                    border: 1px solid #000000 !important;
                    width: 100% !important;
                }
                .bw-ledger-theme table.bw-ledger-table th,
                .bw-ledger-theme table.bw-ledger-table td,
                .bw-ledger-theme table.bw-ledger-table tr td {
                    border: 1px solid #000000 !important;
                }
                .bw-ledger-theme table.bw-ledger-table tr {
                    page-break-inside: avoid !important;
                    break-inside: avoid !important;
                }
            }
        """.trimIndent())
        sb.append("</style>\n</head>\n<body $bodyClass>\n")

        // Render header banner & summary cards only for non-standard formats
        if (!isBwStandardLedger) {
            sb.append("<div class=\"header-banner\">\n")
            sb.append("  <div class=\"title-section\">\n")
            sb.append("    <h1>${format.title} (${format.standardCode})</h1>\n")
            sb.append("    <div class=\"subtitle\">Official Pilot Flight Logbook Record &bull; ${format.description}</div>\n")
            sb.append("  </div>\n")
            sb.append("  <div class=\"meta-section\">\n")
            sb.append("    <div><strong>Pilot:</strong> $pilotName</div>\n")
            sb.append("    <div><strong>Period:</strong> ${dateFormat.format(startDate)} &ndash; ${dateFormat.format(endDate)}</div>\n")
            sb.append("    <div><strong>Printed:</strong> ${genDateFormat.format(Date())}</div>\n")
            sb.append("  </div>\n")
            sb.append("</div>\n")

            sb.append("<div class=\"summary-cards\">\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">Total Flights</div><div class=\"val\">${totals.totalFlights}</div></div>\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">Total Block Time</div><div class=\"val\">$totalBlockStr</div></div>\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">Total Flight Time</div><div class=\"val\">$totalFlightStr</div></div>\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">Night Time</div><div class=\"val\">$totalNightStr</div></div>\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">Day / Ngt Landings</div><div class=\"val\">${totals.totalDayLandings} / ${totals.totalNightLandings}</div></div>\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">PIC Hours</div><div class=\"val\">$totalPicStr</div></div>\n")
            sb.append("  <div class=\"summary-chip\"><div class=\"label\">SIC / Co-Pilot</div><div class=\"val\">$totalSicStr</div></div>\n")
            if (totals.totalFiMinutes > 0) {
                sb.append("  <div class=\"summary-chip\"><div class=\"label\">Instructor (FI)</div><div class=\"val\">$totalFiStr</div></div>\n")
            }
            sb.append("</div>\n")
        }

        // Table Content by Format
        when (format) {
            LogbookPrintFormat.STANDARD_PILOT_LOGBOOK -> appendStandardFaaLedgerTable(
                sb = sb,
                logs = logs,
                dateFormat = logDateFormat,
                totals = totals,
                startDate = startDate,
                pilotName = pilotName,
                priorLogs = priorLogs,
                previousExperiences = effectivePrevExp
            )
            LogbookPrintFormat.EASA_FCL_050 -> appendEasaTable(sb, logs, logDateFormat, totals)
            LogbookPrintFormat.FAA_14_CFR_61 -> appendFaaTable(sb, logs, logDateFormat, totals)
            LogbookPrintFormat.JEPPESEN_PRO -> appendJeppesenTable(sb, logs, logDateFormat, totals)
            LogbookPrintFormat.COMPREHENSIVE_AIRLINE -> appendComprehensiveTable(sb, logs, logDateFormat, totals)
        }

        // Signoff block (for non-standard formats; standard B&W ledger has its own integrated bottom certification block on each page)
        if (!isBwStandardLedger) {
            sb.append("<div class=\"footer-signoff\">\n")
            sb.append("  <div>\n")
            sb.append("    <div>I certify that the entries and flight hours recorded herein are true and correct.</div>\n")
            sb.append("    <div style=\"margin-top: 18px;\"><div class=\"sign-line\"></div>Pilot Signature &bull; License / Staff ID: _____________________</div>\n")
            sb.append("  </div>\n")
            sb.append("  <div style=\"text-align: right;\">\n")
            sb.append("    <div style=\"margin-top: 18px;\"><div class=\"sign-line\" style=\"margin-left: auto;\"></div>Verification / Instructor Endorsement &bull; Date</div>\n")
            sb.append("  </div>\n")
            sb.append("</div>\n")
        }

        sb.append("</body>\n</html>")
        return sb.toString()
    }

    private class PageCumulativeTotals(
        var durationMin: Int = 0,
        var aselMin: Int = 0,
        var asesMin: Int = 0,
        var amelMin: Int = 0,
        var heliMin: Int = 0,
        var gliderMin: Int = 0,
        var dayLdg: Int = 0,
        var nightLdg: Int = 0,
        var nightMin: Int = 0,
        var actualInstMin: Int = 0,
        var hoodMin: Int = 0,
        var appCount: Int = 0,
        var simMin: Int = 0,
        var xcMin: Int = 0,
        var soloMin: Int = 0,
        var picMin: Int = 0,
        var sicMin: Int = 0,
        var dualMin: Int = 0,
        var fiMin: Int = 0
    ) {
        fun copyFrom(other: PageCumulativeTotals) {
            durationMin = other.durationMin
            aselMin = other.aselMin
            asesMin = other.asesMin
            amelMin = other.amelMin
            heliMin = other.heliMin
            gliderMin = other.gliderMin
            dayLdg = other.dayLdg
            nightLdg = other.nightLdg
            nightMin = other.nightMin
            actualInstMin = other.actualInstMin
            hoodMin = other.hoodMin
            appCount = other.appCount
            simMin = other.simMin
            xcMin = other.xcMin
            soloMin = other.soloMin
            picMin = other.picMin
            sicMin = other.sicMin
            dualMin = other.dualMin
            fiMin = other.fiMin
        }
    }

    private fun populateForwardedTotals(
        target: PageCumulativeTotals,
        priorLogs: List<FlightLog>,
        previousExperiences: List<PreviousExperience>
    ) {
        // 1. Process prior flight logs (before selected start date)
        priorLogs.forEach { log ->
            val crewCount = getCrewCount(log.crew)
            val rawBlockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
            val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
            val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)
            val (crewPart, _) = parseCrewAndTrainee(log.crew)
            val role = log.pilotRole.trim()
            val isPic = isPicRole(role, includeFI = true)
            val isFi = isFlightInstructorRole(role)

            val typeUp = log.aircraftType.uppercase()
            val remUp = log.remarks.uppercase()

            val isSim = log.isSim || typeUp.contains("SIM") || remUp.contains("SIM") || remUp.contains("FFS") || remUp.contains("FTD")

            if (isSim) {
                // Simulator logs added to FLIGHT SIM only, not to actual flights
                target.simMin += blockMin
            } else {
                val isHeli = typeUp.contains("HELI") || typeUp.contains("R22") || typeUp.contains("R44") || typeUp.contains("R66") || typeUp.contains("B206") || typeUp.contains("EC135") || typeUp.contains("H125") || typeUp.contains("ROTOR")
                val isGlider = typeUp.contains("GLID") || typeUp.contains("ASK") || typeUp.contains("SGS") || typeUp.contains("DISCUS") || typeUp.contains("DG-")
                val isSea = typeUp.contains("SEA") || typeUp.contains("FLOAT") || typeUp.contains("AMPHIB")
                val isSingleLand = !isHeli && !isGlider && !isSea && (typeUp.contains("C152") || typeUp.contains("C172") || typeUp.contains("C182") || typeUp.contains("PA28") || typeUp.contains("PA38") || typeUp.contains("SR20") || typeUp.contains("SR22") || typeUp.contains("DA20") || typeUp.contains("DA40") || typeUp.contains("SINGLE") || typeUp.contains("SEP") || typeUp.contains("SEL"))
                val isMultiLand = !isHeli && !isGlider && !isSea && !isSingleLand

                target.durationMin += blockMin
                if (isSingleLand) target.aselMin += blockMin
                if (isSea) target.asesMin += blockMin
                if (isMultiLand) target.amelMin += blockMin
                if (isHeli) target.heliMin += blockMin
                if (isGlider) target.gliderMin += blockMin

                val rawNightMin = parseMinutesFromTime(log.nightTime)
                val nightMin = calculateProratedMinutes(rawNightMin, crewCount)
                target.nightMin += nightMin

                val ifrMin = if (log.flightRules.equals("IFR", ignoreCase = true)) flightMin else 0
                target.actualInstMin += ifrMin

                val isHood = remUp.contains("HOOD") || remUp.contains("SIMULATED INST")
                val hoodMin = if (isHood) blockMin else 0
                target.hoodMin += hoodMin

                if (log.approachType.isNotBlank()) target.appCount += 1

                val isXc = log.fromCode.isNotBlank() && log.toCode.isNotBlank() && !log.fromCode.equals(log.toCode, ignoreCase = true)
                if (isXc) target.xcMin += blockMin

                val isSolo = role.contains("SOLO", ignoreCase = true) || crewPart.equals("SOLO", ignoreCase = true)
                if (isSolo) target.soloMin += blockMin

                val isDual = role.contains("DUAL", ignoreCase = true) || role.contains("STUDENT", ignoreCase = true) || role.contains("TRAINEE", ignoreCase = true)
                if (isDual) target.dualMin += blockMin

                val picMin = if (isPic && !isFi && !isSolo) blockMin else 0
                val sicMin = if (!isPic && !isFi && !isSolo) blockMin else 0
                val fiMin = if (isFi) blockMin else 0

                target.picMin += picMin
                target.sicMin += sicMin
                target.fiMin += fiMin
                target.dayLdg += log.landingDay
                target.nightLdg += log.landingNight
            }
        }

        // 2. Process previous experience entries
        previousExperiences.forEach { exp ->
            val expMins = Math.round(exp.totalHours * 60).toInt()

            if (exp.isSim) {
                // Simulator previous experience added to FLIGHT SIM only, regardless of role or aircraft type
                target.simMin += expMins
            } else {
                target.durationMin += expMins

                val role = exp.pilotRole.trim()
                val isPic = isPicRole(role, includeFI = true)
                val isFi = isFlightInstructorRole(role)
                val isSolo = role.contains("SOLO", ignoreCase = true)
                val isDual = role.contains("DUAL", ignoreCase = true) || role.contains("STUDENT", ignoreCase = true) || role.contains("TRAINEE", ignoreCase = true)

                val typeUp = exp.aircraftType.uppercase()
                val isHeli = typeUp.contains("HELI") || typeUp.contains("R22") || typeUp.contains("R44") || typeUp.contains("R66") || typeUp.contains("B206") || typeUp.contains("EC135") || typeUp.contains("H125") || typeUp.contains("ROTOR")
                val isGlider = typeUp.contains("GLID") || typeUp.contains("ASK") || typeUp.contains("SGS") || typeUp.contains("DISCUS") || typeUp.contains("DG-")
                val isSea = typeUp.contains("SEA") || typeUp.contains("FLOAT") || typeUp.contains("AMPHIB")
                val isSingleLand = !isHeli && !isGlider && !isSea && (typeUp.contains("C152") || typeUp.contains("C172") || typeUp.contains("C182") || typeUp.contains("PA28") || typeUp.contains("PA38") || typeUp.contains("SR20") || typeUp.contains("SR22") || typeUp.contains("DA20") || typeUp.contains("DA40") || typeUp.contains("SINGLE") || typeUp.contains("SEP") || typeUp.contains("SEL"))
                val isMultiLand = !isHeli && !isGlider && !isSea && !isSingleLand

                if (isSingleLand) target.aselMin += expMins
                if (isSea) target.asesMin += expMins
                if (isMultiLand) target.amelMin += expMins
                if (isHeli) target.heliMin += expMins
                if (isGlider) target.gliderMin += expMins

                if (isFi) {
                    target.fiMin += expMins
                } else if (isSolo) {
                    target.soloMin += expMins
                } else if (isDual) {
                    target.dualMin += expMins
                } else if (isPic) {
                    target.picMin += expMins
                } else {
                    target.sicMin += expMins
                }
            }
        }
    }

    private fun appendStandardFaaLedgerTable(
        sb: StringBuilder,
        logs: List<FlightLog>,
        dateFormat: SimpleDateFormat,
        totals: LogbookPrintTotals,
        startDate: Date,
        pilotName: String,
        priorLogs: List<FlightLog> = emptyList(),
        previousExperiences: List<PreviousExperience> = emptyList()
    ) {
        val pageSize = 24
        val totalPages = if (logs.isEmpty()) 1 else (logs.size + pageSize - 1) / pageSize
        val fwdTotals = PageCumulativeTotals()
        populateForwardedTotals(fwdTotals, priorLogs, previousExperiences)

        for (pageNum in 1..totalPages) {
            val startIndex = (pageNum - 1) * pageSize
            val pageLogs = if (startIndex < logs.size) {
                logs.subList(startIndex, minOf(logs.size, startIndex + pageSize))
            } else {
                emptyList()
            }
            val pageTotals = PageCumulativeTotals()

            sb.append("<div class=\"logbook-page\">\n")
            // 5) Add page number on top right corner
            sb.append("  <div class=\"page-top-bar\">\n")
            sb.append("    <div class=\"page-number\">Page $pageNum of $totalPages</div>\n")
            sb.append("  </div>\n")

            sb.append("<table class=\"bw-ledger-table\">\n<thead>\n<tr>\n")
            // Row 1 Header: 26 columns organized in sections
            // Left Page
            sb.append("  <th rowspan=\"2\" style=\"width:4.2%;\">DATE</th>\n")
            sb.append("  <th rowspan=\"2\" style=\"width:5.2%;\">AIRCRAFT<br>MODEL</th>\n")
            sb.append("  <th rowspan=\"2\" style=\"width:4.4%;\">AIRCRAFT<br>IDENT</th>\n")
            sb.append("  <th colspan=\"2\" style=\"width:6.8%;\">ROUTE OF FLIGHT</th>\n")
            sb.append("  <th rowspan=\"2\" class=\"section-divider\" style=\"width:4.6%;\">TOTAL<br>DURATION</th>\n")
            sb.append("  <th colspan=\"5\" class=\"section-divider\" style=\"width:17.0%;\">AIRCRAFT CATEGORY & CLASS</th>\n")
            sb.append("  <th colspan=\"2\" class=\"section-divider\" style=\"width:5.0%;\">LANDINGS</th>\n")
            // Right Page
            sb.append("  <th colspan=\"6\" class=\"section-divider\" style=\"width:20.8%;\">CONDITIONS OF FLIGHT</th>\n")
            sb.append("  <th colspan=\"6\" class=\"section-divider\" style=\"width:21.6%;\">TYPE OF PILOTING TIME</th>\n")
            sb.append("  <th rowspan=\"2\" style=\"width:10.4%;\">REMARKS &<br>ENDORSEMENTS</th>\n")
            sb.append("</tr>\n<tr>\n")

            // Row 2 Subheaders with user-requested standard abbreviations
            // Route
            sb.append("  <th style=\"width:3.4%;\">FROM</th><th style=\"width:3.4%;\">TO</th>\n")
            // Aircraft category and class: SEL, SEA, MEL, HELI, GLIDER
            sb.append("  <th style=\"width:3.4%;\">SEL</th>\n")
            sb.append("  <th style=\"width:3.4%;\">SEA</th>\n")
            sb.append("  <th style=\"width:3.4%;\">MEL</th>\n")
            sb.append("  <th style=\"width:3.4%;\">HELI</th>\n")
            sb.append("  <th class=\"section-divider\" style=\"width:3.4%;\">GLIDER</th>\n")
            // Landings
            sb.append("  <th style=\"width:2.5%;\"><span class=\"v-hdr\">DAY</span></th><th class=\"section-divider\" style=\"width:2.5%;\"><span class=\"v-hdr\">NIGHT</span></th>\n")
            // Conditions of flight: NIGHT, ACTUAL INST, SIM INST (HOOD), NO., TYPE, FLIGHT SIM
            sb.append("  <th style=\"width:3.3%;\"><span class=\"v-hdr\">NIGHT</span></th>\n")
            sb.append("  <th style=\"width:3.7%;\">ACTUAL<br>INST</th>\n")
            sb.append("  <th style=\"width:4.0%;\">SIM<br>INST<br>(HOOD)</th>\n")
            sb.append("  <th style=\"width:2.4%;\">NO.</th><th style=\"width:3.4%;\">TYPE</th>\n")
            sb.append("  <th class=\"section-divider\" style=\"width:4.0%;\">FLIGHT<br>SIM</th>\n")
            // Type of piloting time: CROSS COUNTRY, SOLO, PIC, SIC, DUAL REC, AS FI
            sb.append("  <th style=\"width:3.8%;\">CROSS<br>COUNTRY</th>\n")
            sb.append("  <th style=\"width:3.0%;\">SOLO</th>\n")
            sb.append("  <th style=\"width:3.7%;\">PIC</th>\n")
            sb.append("  <th style=\"width:3.7%;\">SIC</th>\n")
            sb.append("  <th style=\"width:3.7%;\">DUAL<br>REC</th>\n")
            sb.append("  <th class=\"section-divider\" style=\"width:3.7%;\">AS FI</th>\n")
            sb.append("</tr>\n</thead>\n<tbody>\n")

            // Render up to 24 data rows (filling entire page with balanced top & bottom space)
            pageLogs.forEach { log ->
                val crewCount = getCrewCount(log.crew)
                val rawBlockMin = log.splitMinutes
                    ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                    ?: parseBlockMinutesFromLog(log.blockHours)
                    ?: 0
                val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
                val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
                val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)
                val (crewPart, traineePart) = parseCrewAndTrainee(log.crew)
                val role = log.pilotRole.trim()
                val isPic = isPicRole(role, includeFI = true)
                val isFi = isFlightInstructorRole(role)

                val typeUp = log.aircraftType.uppercase()
                val remUp = log.remarks.uppercase()

                val isSim = log.isSim || typeUp.contains("SIM") || remUp.contains("SIM") || remUp.contains("FFS") || remUp.contains("FTD")

                val isHeli = !isSim && (typeUp.contains("HELI") || typeUp.contains("R22") || typeUp.contains("R44") || typeUp.contains("R66") || typeUp.contains("B206") || typeUp.contains("EC135") || typeUp.contains("H125") || typeUp.contains("ROTOR"))
                val isGlider = !isSim && (typeUp.contains("GLID") || typeUp.contains("ASK") || typeUp.contains("SGS") || typeUp.contains("DISCUS") || typeUp.contains("DG-"))
                val isSea = !isSim && (typeUp.contains("SEA") || typeUp.contains("FLOAT") || typeUp.contains("AMPHIB"))
                val isSingleLand = !isSim && !isHeli && !isGlider && !isSea && (typeUp.contains("C152") || typeUp.contains("C172") || typeUp.contains("C182") || typeUp.contains("PA28") || typeUp.contains("PA38") || typeUp.contains("SR20") || typeUp.contains("SR22") || typeUp.contains("DA20") || typeUp.contains("DA40") || typeUp.contains("SINGLE") || typeUp.contains("SEP"))
                val isMultiLand = !isSim && !isHeli && !isGlider && !isSea && !isSingleLand

                val rawNightMin = parseMinutesFromTime(log.nightTime)
                val nightMin = if (!isSim) calculateProratedMinutes(rawNightMin, crewCount) else 0

                val ifrMin = if (!isSim && log.flightRules.equals("IFR", ignoreCase = true)) flightMin else 0
                val isHood = !isSim && (remUp.contains("HOOD") || remUp.contains("SIMULATED INST"))
                val hoodMin = if (isHood) blockMin else 0
                val hasApp = !isSim && log.approachType.isNotBlank()
                val simMin = if (isSim) blockMin else 0
                val isXc = !isSim && log.fromCode.isNotBlank() && log.toCode.isNotBlank() && !log.fromCode.equals(log.toCode, ignoreCase = true)
                val xcMin = if (isXc) blockMin else 0
                val isSolo = !isSim && (role.contains("SOLO", ignoreCase = true) || crewPart.equals("SOLO", ignoreCase = true))
                val soloMin = if (isSolo) blockMin else 0
                val isDual = !isSim && (role.contains("DUAL", ignoreCase = true) || role.contains("STUDENT", ignoreCase = true) || role.contains("TRAINEE", ignoreCase = true))
                val dualMin = if (isDual) blockMin else 0
                val picMin = if (!isSim && isPic && !isFi && !isSolo) blockMin else 0
                val sicMin = if (!isSim && !isPic && !isFi && !isSolo) blockMin else 0
                val fiMin = if (!isSim && isFi) blockMin else 0

                if (!isSim) {
                    pageTotals.durationMin += blockMin
                    if (isSingleLand) pageTotals.aselMin += blockMin
                    if (isSea) pageTotals.asesMin += blockMin
                    if (isMultiLand) pageTotals.amelMin += blockMin
                    if (isHeli) pageTotals.heliMin += blockMin
                    if (isGlider) pageTotals.gliderMin += blockMin

                    pageTotals.nightMin += nightMin
                    pageTotals.actualInstMin += ifrMin
                    pageTotals.hoodMin += hoodMin
                    if (hasApp) pageTotals.appCount += 1
                    pageTotals.xcMin += xcMin
                    pageTotals.soloMin += soloMin
                    pageTotals.dualMin += dualMin
                    pageTotals.picMin += picMin
                    pageTotals.sicMin += sicMin
                    pageTotals.fiMin += fiMin
                    pageTotals.dayLdg += log.landingDay
                    pageTotals.nightLdg += log.landingNight
                } else {
                    // Simulator logs added to FLIGHT SIM only, not to actual flights
                    pageTotals.simMin += simMin
                }

                // Build Row HTML
                sb.append("<tr>\n")
                sb.append("  <td>${formatLogDateDisplay(log.date)}</td>\n")
                sb.append("  <td>${log.aircraftType.ifBlank { "-" }}</td>\n")
                sb.append("  <td><strong>${if (log.tailNumber.isNotBlank()) log.tailNumber else if (isSim) "SIM" else "-"}</strong></td>\n")
                sb.append("  <td><strong>${log.fromCode.ifBlank { "-" }}</strong></td>\n")
                sb.append("  <td><strong>${log.toCode.ifBlank { "-" }}</strong></td>\n")
                sb.append("  <td class=\"section-divider\"><strong>${if (!isSim) formatMinutesToHoursClean(blockMin) else "-"}</strong></td>\n")
                sb.append("  <td>${if (isSingleLand) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td>${if (isSea) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td>${if (isMultiLand) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td>${if (isHeli) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td class=\"section-divider\">${if (isGlider) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td>${if (!isSim && log.landingDay > 0) log.landingDay.toString() else "-"}</td>\n")
                sb.append("  <td class=\"section-divider\">${if (!isSim && log.landingNight > 0) log.landingNight.toString() else "-"}</td>\n")
                sb.append("  <td>${if (!isSim && nightMin > 0) formatMinutesToHoursClean(nightMin) else "-"}</td>\n")
                sb.append("  <td>${if (ifrMin > 0) formatMinutesToHoursClean(ifrMin) else "-"}</td>\n")
                sb.append("  <td>${if (hoodMin > 0) formatMinutesToHoursClean(hoodMin) else "-"}</td>\n")
                sb.append("  <td>${if (hasApp) "1" else "-"}</td>\n")
                sb.append("  <td>${if (hasApp) log.approachType else "-"}</td>\n")
                sb.append("  <td class=\"section-divider\">${if (simMin > 0) formatMinutesToHoursClean(simMin) else "-"}</td>\n")
                sb.append("  <td>${if (isXc) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td>${if (isSolo) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
                sb.append("  <td>${if (picMin > 0) formatMinutesToHoursClean(picMin) else "-"}</td>\n")
                sb.append("  <td>${if (sicMin > 0) formatMinutesToHoursClean(sicMin) else "-"}</td>\n")
                sb.append("  <td>${if (dualMin > 0) formatMinutesToHoursClean(dualMin) else "-"}</td>\n")
                sb.append("  <td class=\"section-divider\">${if (fiMin > 0) formatMinutesToHoursClean(fiMin) else "-"}</td>\n")

                // Remarks section shall only display the remarks in the database
                val remarksHtml = if (log.remarks.isNotBlank()) log.remarks else "-"
                sb.append("  <td class=\"align-left\">$remarksHtml</td>\n")
                sb.append("</tr>\n")
            }

            // Fill remaining rows if fewer than 24 flights to always display 24 sectors
            val blankRowsCount = pageSize - pageLogs.size
            for (i in 0 until blankRowsCount) {
                sb.append("<tr class=\"blank-row\">\n")
                sb.append("  <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>\n")
                sb.append("  <td class=\"section-divider\">&nbsp;</td>\n")
                sb.append("  <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>\n")
                sb.append("  <td class=\"section-divider\">&nbsp;</td>\n")
                sb.append("  <td>&nbsp;</td>\n")
                sb.append("  <td class=\"section-divider\">&nbsp;</td>\n")
                sb.append("  <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>\n")
                sb.append("  <td class=\"section-divider\">&nbsp;</td>\n")
                sb.append("  <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>\n")
                sb.append("  <td class=\"section-divider\">&nbsp;</td>\n")
                sb.append("  <td>&nbsp;</td>\n")
                sb.append("</tr>\n")
            }

            // Calculate Totals to Date
            val toDateTotals = PageCumulativeTotals(
                durationMin = fwdTotals.durationMin + pageTotals.durationMin,
                aselMin = fwdTotals.aselMin + pageTotals.aselMin,
                asesMin = fwdTotals.asesMin + pageTotals.asesMin,
                amelMin = fwdTotals.amelMin + pageTotals.amelMin,
                heliMin = fwdTotals.heliMin + pageTotals.heliMin,
                gliderMin = fwdTotals.gliderMin + pageTotals.gliderMin,
                dayLdg = fwdTotals.dayLdg + pageTotals.dayLdg,
                nightLdg = fwdTotals.nightLdg + pageTotals.nightLdg,
                nightMin = fwdTotals.nightMin + pageTotals.nightMin,
                actualInstMin = fwdTotals.actualInstMin + pageTotals.actualInstMin,
                hoodMin = fwdTotals.hoodMin + pageTotals.hoodMin,
                appCount = fwdTotals.appCount + pageTotals.appCount,
                simMin = fwdTotals.simMin + pageTotals.simMin,
                xcMin = fwdTotals.xcMin + pageTotals.xcMin,
                soloMin = fwdTotals.soloMin + pageTotals.soloMin,
                picMin = fwdTotals.picMin + pageTotals.picMin,
                sicMin = fwdTotals.sicMin + pageTotals.sicMin,
                dualMin = fwdTotals.dualMin + pageTotals.dualMin,
                fiMin = fwdTotals.fiMin + pageTotals.fiMin
            )

            // 1. TOTALS THIS PAGE
            sb.append("<tr class=\"totals-row\">\n")
            sb.append("  <td colspan=\"5\" class=\"align-left\"><strong>TOTALS THIS PAGE</strong></td>\n")
            sb.append("  <td class=\"section-divider\"><strong>${formatMinutesToHoursClean(pageTotals.durationMin)}</strong></td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.aselMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.asesMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.amelMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.heliMin)}</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(pageTotals.gliderMin)}</td>\n")
            sb.append("  <td>${pageTotals.dayLdg}</td>\n")
            sb.append("  <td class=\"section-divider\">${pageTotals.nightLdg}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.nightMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.actualInstMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.hoodMin)}</td>\n")
            sb.append("  <td>${pageTotals.appCount}</td>\n")
            sb.append("  <td>-</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(pageTotals.simMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.xcMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.soloMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.picMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.sicMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(pageTotals.dualMin)}</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(pageTotals.fiMin)}</td>\n")
            // Merged 3 rows under remarks for pilot certification & signature
            sb.append("  <td rowspan=\"3\" class=\"cert-signature-cell\">\n")
            sb.append("    <div class=\"cert-statement-text\">I certify that the entries in this log are correct.</div>\n")
            sb.append("    <div class=\"cert-sig-row\">PILOT\'S SIGNATURE: <span class=\"cert-sig-underline\">&nbsp;</span></div>\n")
            sb.append("  </td>\n")
            sb.append("</tr>\n")

            // 2. AMT. FORWARDED FROM PREVIOUS (Column 26 omitted as spanned by Row 1)
            sb.append("<tr class=\"totals-row\">\n")
            sb.append("  <td colspan=\"5\" class=\"align-left\"><strong>AMT. FORWARDED FROM PREVIOUS</strong></td>\n")
            sb.append("  <td class=\"section-divider\"><strong>${formatMinutesToHoursClean(fwdTotals.durationMin)}</strong></td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.aselMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.asesMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.amelMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.heliMin)}</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(fwdTotals.gliderMin)}</td>\n")
            sb.append("  <td>${fwdTotals.dayLdg}</td>\n")
            sb.append("  <td class=\"section-divider\">${fwdTotals.nightLdg}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.nightMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.actualInstMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.hoodMin)}</td>\n")
            sb.append("  <td>${fwdTotals.appCount}</td>\n")
            sb.append("  <td>-</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(fwdTotals.simMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.xcMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.soloMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.picMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.sicMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(fwdTotals.dualMin)}</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(fwdTotals.fiMin)}</td>\n")
            sb.append("</tr>\n")

            // 3. TOTALS TO DATE (Column 26 omitted as spanned by Row 1)
            sb.append("<tr class=\"totals-row\">\n")
            sb.append("  <td colspan=\"5\" class=\"align-left\"><strong>TOTALS TO DATE</strong></td>\n")
            sb.append("  <td class=\"section-divider\"><strong>${formatMinutesToHoursClean(toDateTotals.durationMin)}</strong></td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.aselMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.asesMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.amelMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.heliMin)}</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(toDateTotals.gliderMin)}</td>\n")
            sb.append("  <td>${toDateTotals.dayLdg}</td>\n")
            sb.append("  <td class=\"section-divider\">${toDateTotals.nightLdg}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.nightMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.actualInstMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.hoodMin)}</td>\n")
            sb.append("  <td>${toDateTotals.appCount}</td>\n")
            sb.append("  <td>-</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(toDateTotals.simMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.xcMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.soloMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.picMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.sicMin)}</td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(toDateTotals.dualMin)}</td>\n")
            sb.append("  <td class=\"section-divider\">${formatMinutesToHoursClean(toDateTotals.fiMin)}</td>\n")
            sb.append("</tr>\n")

            sb.append("</tbody>\n</table>\n")
            sb.append("</div>\n") // end .logbook-page

            // 7) the page total will be forwarded to the next page as amt forwarded
            fwdTotals.copyFrom(toDateTotals)
        }
    }

    private fun appendEasaTable(
        sb: StringBuilder,
        logs: List<FlightLog>,
        dateFormat: SimpleDateFormat,
        totals: LogbookPrintTotals
    ) {
        sb.append("<table>\n<thead>\n<tr>\n")
        sb.append("  <th rowspan=\"2\">Date</th>\n")
        sb.append("  <th colspan=\"2\">Departure</th>\n")
        sb.append("  <th colspan=\"2\">Arrival</th>\n")
        sb.append("  <th colspan=\"2\">Aircraft</th>\n")
        sb.append("  <th rowspan=\"2\">Total<br>Time</th>\n")
        sb.append("  <th rowspan=\"2\">Name(s)<br>PIC</th>\n")
        sb.append("  <th colspan=\"2\">Landings</th>\n")
        sb.append("  <th colspan=\"2\">Conditions</th>\n")
        sb.append("  <th colspan=\"4\">Pilot Function Time</th>\n")
        sb.append("  <th rowspan=\"2\">Trainee</th>\n")
        sb.append("  <th rowspan=\"2\" style=\"width:14%;\">Remarks & Endorsements</th>\n")
        sb.append("</tr>\n<tr>\n")
        sb.append("  <th>Place</th><th>Time</th>\n")
        sb.append("  <th>Place</th><th>Time</th>\n")
        sb.append("  <th>Type</th><th>Reg</th>\n")
        sb.append("  <th>Day</th><th>Ngt</th>\n")
        sb.append("  <th>Night</th><th>IFR</th>\n")
        sb.append("  <th>PIC</th><th>Co-Pilot</th><th>Dual</th><th>FI</th>\n")
        sb.append("</tr>\n</thead>\n<tbody>\n")

        logs.forEach { log ->
            val crewCount = getCrewCount(log.crew)
            val rawBlockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
            val (crewPart, traineePart) = parseCrewAndTrainee(log.crew)
            val role = log.pilotRole.trim()
            val isPic = isPicRole(role, includeFI = true)
            val isFi = isFlightInstructorRole(role)

            val rawNightMin = parseMinutesFromTime(log.nightTime)
            val nightMin = calculateProratedMinutes(rawNightMin, crewCount)
            val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
            val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)
            // Actual instrument time must display the prorated flight time
            val ifrMin = if (log.flightRules.equals("IFR", ignoreCase = true)) flightMin else 0

            val picTime = if (isPic && !isFi) formatMinutesToHoursClean(blockMin) else "-"
            val copilotTime = if (!isPic && !isFi) formatMinutesToHoursClean(blockMin) else "-"
            val fiTime = if (isFi) formatMinutesToHoursClean(blockMin) else "-"

            sb.append("<tr>\n")
            sb.append("  <td>${formatLogDateDisplay(log.date)}</td>\n")
            sb.append("  <td><strong>${log.fromCode}</strong></td><td>${log.outTime.ifBlank { log.offTime }}</td>\n")
            sb.append("  <td><strong>${log.toCode}</strong></td><td>${log.inTime.ifBlank { log.onTime }}</td>\n")
            sb.append("  <td>${log.aircraftType}</td><td><strong>${log.tailNumber}</strong></td>\n")
            sb.append("  <td><strong>${formatMinutesToHoursClean(blockMin)}</strong></td>\n")
            sb.append("  <td class=\"align-left\">${crewPart.ifBlank { "Self" }}</td>\n")
            sb.append("  <td>${log.landingDay}</td><td>${log.landingNight}</td>\n")
            sb.append("  <td>${if (nightMin > 0) formatMinutesToHoursClean(nightMin) else "-"}</td>\n")
            sb.append("  <td>${if (ifrMin > 0) formatMinutesToHoursClean(ifrMin) else "-"}</td>\n")
            sb.append("  <td>$picTime</td><td>$copilotTime</td><td>-</td><td>$fiTime</td>\n")
            sb.append("  <td>${if (traineePart.isNotBlank()) "<span class=\"badge-trainee\">$traineePart</span>" else "-"}</td>\n")
            val cleanApp = log.approachType.ifBlank { "" }
            val rem = listOfNotNull(cleanApp.takeIf { it.isNotBlank() }, log.remarks.takeIf { it.isNotBlank() }).joinToString(" &bull; ")
            sb.append("  <td class=\"align-left\">${rem.ifBlank { "-" }}</td>\n")
            sb.append("</tr>\n")
        }

        // Totals
        sb.append("<tr class=\"totals-row\">\n")
        sb.append("  <td colspan=\"7\" class=\"align-left\">TOTALS (${totals.totalFlights} FLIGHTS)</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalBlockMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>${totals.totalDayLandings}</td><td>${totals.totalNightLandings}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalNightMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalPicMinutes)}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalSicMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalFiMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("</tr>\n</tbody>\n</table>\n")
    }

    private fun appendFaaTable(
        sb: StringBuilder,
        logs: List<FlightLog>,
        dateFormat: SimpleDateFormat,
        totals: LogbookPrintTotals
    ) {
        sb.append("<table>\n<thead>\n<tr>\n")
        sb.append("  <th rowspan=\"2\">Date</th>\n")
        sb.append("  <th colspan=\"2\">Aircraft</th>\n")
        sb.append("  <th colspan=\"2\">Route of Flight</th>\n")
        sb.append("  <th colspan=\"2\">Landings</th>\n")
        sb.append("  <th colspan=\"2\">Conditions of Flight</th>\n")
        sb.append("  <th colspan=\"4\">Type of Piloting Time</th>\n")
        sb.append("  <th rowspan=\"2\">Trainee</th>\n")
        sb.append("  <th rowspan=\"2\">Total<br>Duration</th>\n")
        sb.append("  <th rowspan=\"2\" style=\"width:16%;\">Remarks, Procedures & Approaches</th>\n")
        sb.append("</tr>\n<tr>\n")
        sb.append("  <th>Model</th><th>Ident</th>\n")
        sb.append("  <th>From</th><th>To</th>\n")
        sb.append("  <th>Day</th><th>Ngt</th>\n")
        sb.append("  <th>Night</th><th>Actual Inst.</th>\n")
        sb.append("  <th>PIC</th><th>SIC</th><th>Dual Rec.</th><th>As FI</th>\n")
        sb.append("</tr>\n</thead>\n<tbody>\n")

        logs.forEach { log ->
            val crewCount = getCrewCount(log.crew)
            val rawBlockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
            val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
            val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)
            val (_, traineePart) = parseCrewAndTrainee(log.crew)
            val role = log.pilotRole.trim()
            val isPic = isPicRole(role, includeFI = true)
            val isFi = isFlightInstructorRole(role)

            val rawNightMin = parseMinutesFromTime(log.nightTime)
            val nightMin = calculateProratedMinutes(rawNightMin, crewCount)
            // Actual instrument time must display the prorated flight time
            val instMin = if (log.flightRules.equals("IFR", ignoreCase = true)) flightMin else 0

            sb.append("<tr>\n")
            sb.append("  <td>${formatLogDateDisplay(log.date)}</td>\n")
            sb.append("  <td>${log.aircraftType}</td><td><strong>${log.tailNumber}</strong></td>\n")
            sb.append("  <td><strong>${log.fromCode}</strong></td><td><strong>${log.toCode}</strong></td>\n")
            sb.append("  <td>${log.landingDay}</td><td>${log.landingNight}</td>\n")
            sb.append("  <td>${if (nightMin > 0) formatMinutesToHoursClean(nightMin) else "-"}</td>\n")
            sb.append("  <td>${if (instMin > 0) formatMinutesToHoursClean(instMin) else "-"}</td>\n")
            sb.append("  <td>${if (isPic && !isFi) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
            sb.append("  <td>${if (!isPic && !isFi) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
            sb.append("  <td>-</td>\n")
            sb.append("  <td>${if (isFi) formatMinutesToHoursClean(blockMin) else "-"}</td>\n")
            sb.append("  <td>${if (traineePart.isNotBlank()) "<span class=\"badge-trainee\">$traineePart</span>" else "-"}</td>\n")
            sb.append("  <td><strong>${formatMinutesToHoursClean(blockMin)}</strong></td>\n")
            val rem = listOfNotNull(
                log.flightNum.takeIf { it.isNotBlank() },
                log.approachType.takeIf { it.isNotBlank() },
                log.remarks.takeIf { it.isNotBlank() }
            ).joinToString(" &bull; ")
            sb.append("  <td class=\"align-left\">${rem.ifBlank { "-" }}</td>\n")
            sb.append("</tr>\n")
        }

        // Totals
        sb.append("<tr class=\"totals-row\">\n")
        sb.append("  <td colspan=\"5\" class=\"align-left\">TOTALS (${totals.totalFlights} FLIGHTS)</td>\n")
        sb.append("  <td>${totals.totalDayLandings}</td><td>${totals.totalNightLandings}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalNightMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalPicMinutes)}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalSicMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalFiMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalBlockMinutes)}</td>\n")
        sb.append("  <td>-</td>\n")
        sb.append("</tr>\n</tbody>\n</table>\n")
    }

    private fun appendJeppesenTable(
        sb: StringBuilder,
        logs: List<FlightLog>,
        dateFormat: SimpleDateFormat,
        totals: LogbookPrintTotals
    ) {
        sb.append("<table>\n<thead>\n<tr>\n")
        sb.append("  <th>Date</th>\n")
        sb.append("  <th>Flt #</th>\n")
        sb.append("  <th>Dep (Out)</th>\n")
        sb.append("  <th>Arr (In)</th>\n")
        sb.append("  <th>Aircraft</th>\n")
        sb.append("  <th>Block (HH:MM)</th>\n")
        sb.append("  <th>Flight (HH:MM)</th>\n")
        sb.append("  <th>Night</th>\n")
        sb.append("  <th>Ldg (D/N)</th>\n")
        sb.append("  <th>Role</th>\n")
        sb.append("  <th>Crew</th>\n")
        sb.append("  <th>Trainee</th>\n")
        sb.append("  <th>Approach</th>\n")
        sb.append("  <th style=\"width:15%;\">Remarks</th>\n")
        sb.append("</tr>\n</thead>\n<tbody>\n")

        logs.forEach { log ->
            val crewCount = getCrewCount(log.crew)
            val rawBlockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
            val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
            val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)
            val rawNightMin = parseMinutesFromTime(log.nightTime)
            val nightMin = calculateProratedMinutes(rawNightMin, crewCount)
            val (crewPart, traineePart) = parseCrewAndTrainee(log.crew)

            sb.append("<tr>\n")
            sb.append("  <td>${formatLogDateDisplay(log.date)}</td>\n")
            sb.append("  <td><strong>${log.flightNum.ifBlank { "-" }}</strong></td>\n")
            sb.append("  <td><strong>${log.fromCode}</strong> ${log.outTime.ifBlank { log.offTime }}</td>\n")
            sb.append("  <td><strong>${log.toCode}</strong> ${log.inTime.ifBlank { log.onTime }}</td>\n")
            sb.append("  <td>${log.aircraftType} (${log.tailNumber})</td>\n")
            sb.append("  <td><strong>${formatMinutesToHoursClean(blockMin)}</strong></td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(flightMin)}</td>\n")
            sb.append("  <td>${if (nightMin > 0) formatMinutesToHoursClean(nightMin) else "-"}</td>\n")
            sb.append("  <td>${log.landingDay} / ${log.landingNight}</td>\n")
            sb.append("  <td><strong>${log.pilotRole.ifBlank { "PIC" }}</strong></td>\n")
            sb.append("  <td class=\"align-left\">${crewPart.ifBlank { "Self" }}</td>\n")
            sb.append("  <td>${if (traineePart.isNotBlank()) "<span class=\"badge-trainee\">$traineePart</span>" else "-"}</td>\n")
            sb.append("  <td>${log.approachType.ifBlank { "-" }}</td>\n")
            sb.append("  <td class=\"align-left\">${log.remarks.ifBlank { "-" }}</td>\n")
            sb.append("</tr>\n")
        }

        // Totals
        sb.append("<tr class=\"totals-row\">\n")
        sb.append("  <td colspan=\"5\" class=\"align-left\">TOTALS (${totals.totalFlights} FLIGHTS)</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalBlockMinutes)}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalFlightMinutes)}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalNightMinutes)}</td>\n")
        sb.append("  <td>${totals.totalDayLandings} / ${totals.totalNightLandings}</td>\n")
        sb.append("  <td colspan=\"5\">PIC: ${formatMinutesToHoursClean(totals.totalPicMinutes)} &bull; SIC: ${formatMinutesToHoursClean(totals.totalSicMinutes)}${if (totals.totalFiMinutes > 0) " &bull; FI: ${formatMinutesToHoursClean(totals.totalFiMinutes)}" else ""}</td>\n")
        sb.append("</tr>\n</tbody>\n</table>\n")
    }

    private fun appendComprehensiveTable(
        sb: StringBuilder,
        logs: List<FlightLog>,
        dateFormat: SimpleDateFormat,
        totals: LogbookPrintTotals
    ) {
        sb.append("<table>\n<thead>\n<tr>\n")
        sb.append("  <th>Date</th>\n")
        sb.append("  <th>Flight #</th>\n")
        sb.append("  <th>Type</th>\n")
        sb.append("  <th>Tail #</th>\n")
        sb.append("  <th>From</th>\n")
        sb.append("  <th>To</th>\n")
        sb.append("  <th>Out</th><th>Off</th><th>On</th><th>In</th>\n")
        sb.append("  <th>Block</th>\n")
        sb.append("  <th>Flight</th>\n")
        sb.append("  <th>Night</th>\n")
        sb.append("  <th>Ldg (D/N)</th>\n")
        sb.append("  <th>Role</th>\n")
        sb.append("  <th>Crew</th>\n")
        sb.append("  <th>Trainee</th>\n")
        sb.append("  <th style=\"width:14%;\">Remarks & Approach</th>\n")
        sb.append("</tr>\n</thead>\n<tbody>\n")

        logs.forEach { log ->
            val crewCount = getCrewCount(log.crew)
            val rawBlockMin = log.splitMinutes
                ?: calculateTimeDiffInMinutes(log.outTime, log.inTime)
                ?: parseBlockMinutesFromLog(log.blockHours)
                ?: 0
            val blockMin = calculateProratedMinutes(rawBlockMin, crewCount)
            val rawFlightMin = calculateTimeDiffInMinutes(log.offTime, log.onTime) ?: rawBlockMin
            val flightMin = calculateProratedMinutes(rawFlightMin, crewCount)
            val rawNightMin = parseMinutesFromTime(log.nightTime)
            val nightMin = calculateProratedMinutes(rawNightMin, crewCount)
            val (crewPart, traineePart) = parseCrewAndTrainee(log.crew)

            sb.append("<tr>\n")
            sb.append("  <td>${formatLogDateDisplay(log.date)}</td>\n")
            sb.append("  <td><strong>${log.flightNum.ifBlank { "-" }}</strong></td>\n")
            sb.append("  <td>${log.aircraftType}</td>\n")
            sb.append("  <td><strong>${log.tailNumber}</strong></td>\n")
            sb.append("  <td><strong>${log.fromCode}</strong></td>\n")
            sb.append("  <td><strong>${log.toCode}</strong></td>\n")
            sb.append("  <td>${log.outTime.ifBlank { "-" }}</td>\n")
            sb.append("  <td>${log.offTime.ifBlank { "-" }}</td>\n")
            sb.append("  <td>${log.onTime.ifBlank { "-" }}</td>\n")
            sb.append("  <td>${log.inTime.ifBlank { "-" }}</td>\n")
            sb.append("  <td><strong>${formatMinutesToHoursClean(blockMin)}</strong></td>\n")
            sb.append("  <td>${formatMinutesToHoursClean(flightMin)}</td>\n")
            sb.append("  <td>${if (nightMin > 0) formatMinutesToHoursClean(nightMin) else "-"}</td>\n")
            sb.append("  <td>${log.landingDay} / ${log.landingNight}</td>\n")
            sb.append("  <td>${log.pilotRole.ifBlank { "PIC" }}</td>\n")
            sb.append("  <td class=\"align-left\">${crewPart.ifBlank { "Self" }}</td>\n")
            sb.append("  <td>${if (traineePart.isNotBlank()) "<span class=\"badge-trainee\">$traineePart</span>" else "-"}</td>\n")
            val cleanApp = log.approachType.ifBlank { "" }
            val rem = listOfNotNull(cleanApp.takeIf { it.isNotBlank() }, log.remarks.takeIf { it.isNotBlank() }).joinToString(" &bull; ")
            sb.append("  <td class=\"align-left\">${rem.ifBlank { "-" }}</td>\n")
            sb.append("</tr>\n")
        }

        // Totals
        sb.append("<tr class=\"totals-row\">\n")
        sb.append("  <td colspan=\"10\" class=\"align-left\">TOTALS (${totals.totalFlights} FLIGHTS)</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalBlockMinutes)}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalFlightMinutes)}</td>\n")
        sb.append("  <td>${formatMinutesToHoursClean(totals.totalNightMinutes)}</td>\n")
        sb.append("  <td>${totals.totalDayLandings} / ${totals.totalNightLandings}</td>\n")
        sb.append("  <td colspan=\"4\">PIC: ${formatMinutesToHoursClean(totals.totalPicMinutes)} &bull; SIC: ${formatMinutesToHoursClean(totals.totalSicMinutes)}${if (totals.totalFiMinutes > 0) " &bull; FI: ${formatMinutesToHoursClean(totals.totalFiMinutes)}" else ""}</td>\n")
        sb.append("</tr>\n</tbody>\n</table>\n")
    }

    private fun parseMinutesFromTime(timeStr: String): Int {
        if (timeStr.isBlank()) return 0
        val clean = timeStr.trim()
        return if (clean.contains(":")) {
            val parts = clean.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            h * 60 + m
        } else {
            (clean.toDoubleOrNull()?.times(60))?.toInt() ?: 0
        }
    }

    private fun formatLogDateDisplay(dateStr: String): String {
        val d = com.example.parseLogDate(dateStr)
        return if (d != null) {
            SimpleDateFormat("dd/MM/yy", Locale.US).format(d)
        } else {
            dateStr
        }
    }

    /**
     * Prints the generated HTML using Android's native PrintManager service.
     * This provides system printer integration and direct "Save as PDF" capability.
     */
    fun print(context: Context, htmlContent: String, jobName: String) {
        try {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager != null) {
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        val printAttributes = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        printManager.print(jobName, printAdapter, printAttributes)
                    } else {
                        Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start print: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the HTML logbook content via Android Share Sheet.
     */
    fun share(context: Context, htmlContent: String, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, htmlContent)
            }
            context.startActivity(Intent.createChooser(intent, "Share Pilot Logbook"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
