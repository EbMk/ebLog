package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EbLogConfig
import com.example.data.EbLogRepository
import com.example.data.Airport
import com.example.data.Aircraft
import com.example.data.AircraftType
import com.example.data.UserProfileSettings
import com.example.data.PreviousExperience
import com.example.data.EbLogNote
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import com.example.parseCsvLine
import com.example.correctDateAnomaly
import com.example.calculateTimeDiffInMinutes
import com.example.normalizeTimeString
import com.example.normalizeBlockHours
import com.example.parseBlockMinutesFromLog
import com.example.formatMinutesToHoursClean
import com.example.extractDateFromDateTime

class EbLogViewModel(val repository: EbLogRepository) : ViewModel() {

    val uiScale = MutableStateFlow(0.85f)

    fun initUiScale(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        uiScale.value = prefs.getFloat("app_ui_scale", 0.85f)
    }

    fun setUiScale(context: Context, scale: Float) {
        uiScale.value = scale
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("app_ui_scale", scale).apply()
    }

    val importProgress = MutableStateFlow<Float?>(null)
    val importProgressRowText = MutableStateFlow("")
    val importStatusMsg = MutableStateFlow("")
    val isSuccessStatus = MutableStateFlow(true)
    val showImportCompletedDialog = MutableStateFlow<String?>(null)
    val isImporting = MutableStateFlow(false)
    val missingAircraftTypesToPrompt = MutableStateFlow<List<String>>(emptyList())

    var isCurrentlyViewingImportCsv = false

    fun dismissImportCompletedDialog() {
        showImportCompletedDialog.value = null
    }

    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                isImporting.value = true
                importProgress.value = 0.0f
                importProgressRowText.value = "Reading CSV file..."
                importStatusMsg.value = ""
                val inputStream = context.contentResolver.openInputStream(uri)
                val csvText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (csvText.isBlank()) {
                    importStatusMsg.value = "Selected CSV file is empty."
                    isSuccessStatus.value = false
                    return@launch
                }

                val lines = csvText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    importStatusMsg.value = "The CSV file has no parseable rows."
                    isSuccessStatus.value = false
                    return@launch
                }

                val firstLine = lines.first()
                val csvDelimiter = when {
                    firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
                    firstLine.count { it == '\t' } > firstLine.count { it == ',' } -> '\t'
                    else -> ','
                }
                val headers = parseCsvLine(firstLine, csvDelimiter)

                val hasHeader = headers.any { h ->
                    val cleanH = h.lowercase()
                    cleanH.contains("flight") || cleanH.contains("date") || cleanH.contains("tail") || cleanH.contains("from") || cleanH.contains("to") || cleanH.contains("reg")
                }

                val startIndex = if (hasHeader) 1 else 0
                val headerMap = mutableMapOf<Int, String>()

                if (hasHeader) {
                    headers.forEachIndexed { index, header ->
                        val rawClean = header.trim().replace("\"", "").replace("'", "")
                        val hClean = rawClean.lowercase()
                        // Strip parenthetical modifiers and common suffixes like (utc), (z), (gmt), (time), (act), etc.
                        val hNorm = hClean
                            .replace("\\s*\\((?:utc|z|local|gmt|time|act|est|sched|actual|scheduled)\\)".toRegex(), "")
                            .replace("\\b(?:utc|z|local|gmt|act|est|sched|actual|scheduled)\\b".toRegex(), "")
                            .replace("[-_./]".toRegex(), " ")
                            .replace("\\s+".toRegex(), " ")
                            .trim()

                        val mappedKey = when {
                            // 1. Takeoff & Landing COUNTS (Must explicitly match count qualifiers or plurals)
                            // Night Takeoff count
                            hNorm.contains("night") && (hNorm.contains("takeoff") || hNorm.contains("take off") || hNorm.contains("t o") || hNorm.contains("to")) -> "takeoffNight"

                            // Day Takeoff count
                            hNorm.contains("day") && (hNorm.contains("takeoff") || hNorm.contains("take off") || hNorm.contains("t o") || hNorm.contains("to")) -> "takeoffDay"

                            // Night Landing count
                            hNorm.contains("night") && (hNorm.contains("landing") || hNorm.contains("ldg") || hNorm.contains("land")) -> "landingNight"

                            // Day Landing count
                            hNorm.contains("day") && (hNorm.contains("landing") || hNorm.contains("ldg") || hNorm.contains("land")) -> "landingDay"

                            // Generic Takeoff count (plural or specific count keywords, no 'time')
                            (hNorm == "takeoffs" || hNorm.contains("takeoff count") || hNorm.contains("takeoffs count") ||
                             hNorm.contains("num takeoff") || hNorm.contains("number of takeoff") || hNorm.contains("t o count")) && !hNorm.contains("time") -> "takeoffDay"

                            // Generic Landing count (plural or specific count keywords, no 'time')
                            (hNorm == "landings" || hNorm.contains("landing count") || hNorm.contains("landings count") ||
                             hNorm.contains("num landing") || hNorm.contains("number of landing") || hNorm.contains("ldg count")) && !hNorm.contains("time") -> "landingDay"

                            // 2. Flight Times: Off Time, On Time, Out Time, In Time
                            // OFF Time (Flight Off / Wheels Off / Wheels Up / Takeoff Time / Airborne Time)
                            hNorm.contains("flight off") || hNorm.contains("flightoff") ||
                            hNorm.contains("off time") || hNorm.contains("offtime") || hNorm.contains("time off") ||
                            hNorm.contains("wheels off") || hNorm.contains("wheelsoff") ||
                            hNorm.contains("wheels up") || hNorm.contains("wheelsup") ||
                            hNorm.contains("takeoff time") || hNorm.contains("take off time") || hNorm.contains("time takeoff") ||
                            hNorm.contains("t o time") || hNorm.contains("t/o time") || hNorm.contains("to time") ||
                            hNorm.contains("airborne time") || hNorm.contains("dep airborne") || hNorm.contains("departure airborne") ||
                            hNorm.contains("atot") || hNorm.contains("etot") || hNorm.contains("dep off") || hNorm.contains("dep takeoff") ||
                            hNorm == "off" || hNorm == "takeoff" || hNorm == "take off" || hNorm == "take-off" ||
                            hNorm == "t o" || hNorm == "t/o" || hNorm == "airborne" || hNorm == "tkoff" -> "offTime"

                            // ON Time (Flight On / Wheels On / Wheels Down / Landing Time / Touchdown Time)
                            hNorm.contains("landing time") || hNorm.contains("landingtime") || hNorm.contains("time landing") ||
                            hNorm.contains("flight on") || hNorm.contains("flighton") ||
                            hNorm.contains("flight landing") || hNorm.contains("flight land") ||
                            hNorm.contains("on time") || hNorm.contains("ontime") || hNorm.contains("time on") ||
                            hNorm.contains("wheels on") || hNorm.contains("wheelson") ||
                            hNorm.contains("wheels down") || hNorm.contains("wheelsdown") ||
                            hNorm.contains("touchdown time") || hNorm.contains("touch down time") || hNorm.contains("time touchdown") ||
                            hNorm.contains("touchdown") || hNorm.contains("touch down") ||
                            hNorm.contains("ldg time") || hNorm.contains("land time") ||
                            hNorm.contains("arr airborne") || hNorm.contains("arr on") || hNorm.contains("arr landing") || hNorm.contains("arr touchdown") ||
                            hNorm.contains("aldt") || hNorm.contains("eldt") ||
                            hNorm == "on" || hNorm == "landing" || hNorm == "ldg" || hNorm == "land" -> "onTime"

                            // OUT Time (Block Out / Chocks Off / Off Block / Gate Out)
                            hNorm.contains("out time") || hNorm.contains("outtime") || hNorm.contains("time out") ||
                            hNorm.contains("block out") || hNorm.contains("blockout") ||
                            hNorm.contains("chocks off") || hNorm.contains("chock off") ||
                            hNorm.contains("off block") || hNorm.contains("offblock") ||
                            hNorm.contains("gate out") ||
                            hNorm.contains("aobt") || hNorm.contains("eobt") || hNorm.contains("sobt") ||
                            hNorm == "out" || (hNorm.contains("dep") && hNorm.contains("time") && !hNorm.contains("airborne")) -> "outTime"

                            // IN Time (Block In / Chocks On / On Block / Gate In)
                            hNorm.contains("in time") || hNorm.contains("intime") || hNorm.contains("time in") ||
                            hNorm.contains("block in") || hNorm.contains("blockin") ||
                            hNorm.contains("chocks on") || hNorm.contains("chock on") ||
                            hNorm.contains("on block") || hNorm.contains("onblock") ||
                            hNorm.contains("gate in") ||
                            hNorm.contains("aibt") || hNorm.contains("sibt") || hNorm.contains("eibt") ||
                            hNorm == "in" || (hNorm.contains("arr") && hNorm.contains("time") && !hNorm.contains("airborne") && !hNorm.contains("touchdown")) -> "inTime"

                            // Flight number
                            hNorm.contains("flight number") || hNorm.contains("flightnum") || hNorm == "flight" ||
                            hNorm.contains("flight num") || hNorm.contains("flightnumber") || hNorm == "flt" ||
                            hNorm.contains("flt num") || hNorm.contains("flt#") ||
                            hNorm.contains("flight#") || hNorm == "trip" -> "flightNum"

                            // Date
                            hNorm == "date" || hNorm.contains("flight date") ||
                            hNorm.contains("flt date") || hNorm.contains("utc date") -> "date"

                            // Tail / Registration
                            hNorm.contains("registration") || hNorm.contains("tailnumber") || hNorm == "tail" ||
                            hNorm.contains("tail number") || hNorm == "reg" || hNorm.contains("reg num") ||
                            hNorm.contains("aircraft reg") || hNorm.contains("ac reg") || hNorm.contains("tail#") -> "tailNumber"

                            // Aircraft Type
                            hNorm.contains("aircrafttype") || hNorm.contains("aircraft type") ||
                            hNorm.contains("ac type") || hNorm.contains("actype") || hNorm == "type" || hNorm == "model" ||
                            hNorm.contains("aircraft model") -> "aircraftType"

                            // Crew count & crew
                            hNorm.contains("crew size") || hNorm.contains("crew count") || hNorm.contains("crew number") ||
                            hNorm.contains("number of crew") || hNorm == "crewcount" || hNorm == "crewsize" || hNorm == "crewnumber" -> "crewCount"

                            hNorm == "crew" || hNorm.contains("copilot") || hNorm.contains("co pilot") || hNorm == "crews" ||
                            hNorm.contains("crew name") || hNorm.contains("crew members") ||
                            hNorm.contains("pic sic") || hNorm == "sic" -> "crew"

                            // Origin airport
                            hNorm.contains("fromcode") || hNorm.contains("from code") || hNorm == "from" || hNorm.contains("origin") ||
                            hNorm == "dep" || hNorm == "dept" || hNorm.contains("departure airport") || hNorm.contains("dep airport") ||
                            hNorm.contains("origin airport") || hNorm.contains("orig") -> "fromCode"

                            // Destination airport
                            hNorm.contains("tocode") || hNorm.contains("to code") || hNorm == "to" || hNorm.contains("dest") ||
                            hNorm.contains("destination") || hNorm == "arr" || hNorm.contains("arrival airport") ||
                            hNorm.contains("arr airport") || hNorm.contains("destination airport") -> "toCode"

                            // PF Dep / PF Arr
                            hNorm.contains("pffrom") || hNorm.contains("pf from") || hNorm.contains("pf dep") || hNorm.contains("pilot flying dep") -> "pfFrom"
                            hNorm.contains("pfto") || hNorm.contains("pf to") || hNorm.contains("pf arr") || hNorm.contains("pilot flying arr") -> "pfTo"

                            // Approach type
                            hNorm.contains("approachtype") || hNorm.contains("approach type") || hNorm == "approach" -> "approachType"

                            // Pilot role
                            hNorm.contains("crew role") || hNorm.contains("pilot role") || hNorm.contains("pilotrole") ||
                            hNorm == "role" || hNorm.contains("function") || hNorm.contains("capacity") -> "pilotRole"

                            // Flight rules
                            hNorm.contains("flightrules") || hNorm.contains("flight rules") || hNorm == "rules" ||
                            hNorm.contains("ifr") || hNorm.contains("vfr") -> "flightRules"

                            // Remarks
                            hNorm == "remarks" || hNorm == "remark" || hNorm.contains("comment") ||
                            hNorm.contains("notes") || hNorm == "note" -> "remarks"

                            // Night hours
                            hNorm.contains("night hours") || hNorm.contains("nighthours") ||
                            hNorm.contains("night time") || hNorm.contains("nighttime") || hNorm == "night" -> "nightTime"

                            // Block hours / Total time
                            hNorm.contains("block hours") || hNorm.contains("blockhours") ||
                            hNorm.contains("block time") || hNorm.contains("blocktime") ||
                            hNorm.contains("total block") || hNorm.contains("total time") ||
                            hNorm.contains("total hours") || hNorm.contains("pay hours") ||
                            hNorm == "total" || hNorm == "hours" -> "blockHours"

                            else -> null
                        }
                        if (mappedKey != null) {
                            headerMap[index] = mappedKey
                        }
                    }
                } else {
                    val defaultOrder = listOf(
                        "flightNum", "date", "tailNumber", "aircraftType", "crew",
                        "fromCode", "toCode", "outTime", "offTime", "onTime", "inTime",
                        "pfFrom", "pfTo", "takeoffDay", "takeoffNight", "landingDay", "landingNight",
                        "approachType", "pilotRole", "flightRules", "remarks", "blockHours", "nightTime"
                    )
                    defaultOrder.forEachIndexed { index, key ->
                        headerMap[index] = key
                    }
                }

                val totalRows = lines.size - startIndex
                var successCount = 0
                var failCount = 0

                val notesList = repository.notes.first()
                val existingAirports = repository.airports.first()
                val existingAircrafts = repository.aircrafts.first()
                val existingAircraftTypes = repository.aircraftTypes.first()

                val addedAirports = mutableListOf<String>()
                val addedAircrafts = mutableListOf<String>()

                for (lineIndex in startIndex until lines.size) {
                    val lineText = lines[lineIndex].trim()
                    if (lineText.isEmpty()) continue

                    val currentRowIndex = lineIndex - startIndex
                    importProgress.value = currentRowIndex.toFloat() / totalRows.toFloat()
                    importProgressRowText.value = "Importing record ${currentRowIndex + 1} of $totalRows..."
                    kotlinx.coroutines.delay(6)

                    try {
                        val rowValues = parseCsvLine(lineText, csvDelimiter)
                        if (rowValues.isEmpty()) continue

                        val json = org.json.JSONObject()
                        json.put("flightNum", "")
                        json.put("date", "")
                        json.put("tailNumber", "")
                        json.put("aircraftType", "")
                        json.put("crew", "")
                        json.put("fromCode", "")
                        json.put("toCode", "")
                        json.put("outTime", "")
                        json.put("offTime", "")
                        json.put("onTime", "")
                        json.put("inTime", "")
                        json.put("pfFrom", true)
                        json.put("pfTo", true)
                        json.put("takeoffDay", 0)
                        json.put("takeoffNight", 0)
                        json.put("landingDay", 0)
                        json.put("landingNight", 0)
                        json.put("approachType", "")
                        json.put("pilotRole", "PIC")
                        json.put("flightRules", "IFR")
                        json.put("remarks", "")
                        json.put("blockHours", "")
                        json.put("nightTime", "")

                        var importedCrew: String? = null
                        var importedCrewCount: Int? = null

                        rowValues.forEachIndexed { colIndex, valStr ->
                            val key = headerMap[colIndex]
                            val cleanVal = valStr.trim().replace("\"", "").replace("'", "")
                            if (cleanVal.isEmpty()) return@forEachIndexed

                            if (key != null) {
                                when (key) {
                                    "outTime", "inTime" -> {
                                        val normalized = normalizeTimeString(cleanVal)
                                        if (normalized.isNotBlank()) {
                                            json.put(key, normalized)
                                        }
                                        val extractedDate = extractDateFromDateTime(cleanVal)
                                        if (extractedDate != null && (!json.has("date") || json.optString("date", "").isBlank())) {
                                            json.put("date", extractedDate)
                                        }
                                    }
                                    "offTime", "onTime" -> {
                                        // Disambiguate: Is this value a takeoff/landing count (e.g. "1", "0", "2")?
                                        val asInt = cleanVal.toIntOrNull()
                                        if (asInt != null && asInt in 0..10 && !cleanVal.contains(":") && !cleanVal.contains(".")) {
                                            val countKey = if (key == "offTime") "takeoffDay" else "landingDay"
                                            json.put(countKey, asInt)
                                        } else {
                                            val normalized = normalizeTimeString(cleanVal)
                                            if (normalized.isNotBlank()) {
                                                json.put(key, normalized)
                                            }
                                            val extractedDate = extractDateFromDateTime(cleanVal)
                                            if (extractedDate != null && (!json.has("date") || json.optString("date", "").isBlank())) {
                                                json.put("date", extractedDate)
                                            }
                                        }
                                    }
                                    "takeoffDay", "takeoffNight", "landingDay", "landingNight" -> {
                                        // Disambiguate: If value contains ":" or "." or is a 4-digit time, it's actually a time string!
                                        if (cleanVal.contains(":") || (cleanVal.contains(".") && !cleanVal.endsWith(".0")) ||
                                            (cleanVal.length == 4 && cleanVal.all { it.isDigit() } && (cleanVal.substring(0, 2).toIntOrNull() ?: 99) in 0..23)) {
                                            val normalized = normalizeTimeString(cleanVal)
                                            if (normalized.isNotBlank()) {
                                                val targetKey = if (key.startsWith("takeoff")) "offTime" else "onTime"
                                                if (json.optString(targetKey, "").isBlank()) {
                                                    json.put(targetKey, normalized)
                                                }
                                            }
                                        } else {
                                            val intVal = cleanVal.toDoubleOrNull()?.toInt() ?: cleanVal.toIntOrNull() ?: 0
                                            json.put(key, intVal)
                                        }
                                    }
                                    "nightTime" -> {
                                        val normalized = normalizeTimeString(cleanVal)
                                        json.put(key, normalized)
                                    }
                                    "blockHours" -> {
                                        val normalized = normalizeBlockHours(cleanVal)
                                        json.put(key, normalized)
                                    }
                                    "pfFrom", "pfTo" -> {
                                        val cLower = cleanVal.lowercase()
                                        val boolVal = cLower == "true" || cLower == "1" || cLower == "yes" || cLower == "y" || cLower == "pf"
                                        json.put(key, boolVal)
                                    }
                                    "date" -> {
                                        val corrected = correctDateAnomaly(cleanVal)
                                        json.put(key, corrected)
                                    }
                                    "crew" -> {
                                        val intVal = cleanVal.toIntOrNull()
                                        if (intVal != null && intVal > 0) {
                                            importedCrewCount = intVal
                                        } else {
                                            importedCrew = cleanVal
                                        }
                                    }
                                    "crewCount" -> {
                                        val intVal = cleanVal.toIntOrNull()
                                        if (intVal != null && intVal > 0) {
                                            importedCrewCount = intVal
                                        }
                                    }
                                    else -> {
                                        json.put(key, cleanVal)
                                    }
                                }
                            }
                        }

                        val finalCrew: String = when {
                            importedCrewCount != null && importedCrewCount!! > 0 -> {
                                if (!importedCrew.isNullOrBlank()) {
                                    val namesList = importedCrew!!.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    if (namesList.size >= importedCrewCount!!) {
                                        namesList.take(importedCrewCount!!).joinToString(", ")
                                    } else {
                                        val paddedList = namesList.toMutableList()
                                        while (paddedList.size < importedCrewCount!!) {
                                            paddedList.add("Crew ${paddedList.size + 1}")
                                        }
                                        paddedList.joinToString(", ")
                                    }
                                } else {
                                    (1..importedCrewCount!!).map { "Crew $it" }.joinToString(", ")
                                }
                            }
                            !importedCrew.isNullOrBlank() -> {
                                importedCrew!!
                            }
                            else -> {
                                ""
                            }
                        }
                        json.put("crew", finalCrew)

                        if (!json.has("date") || json.optString("date", "").isBlank()) {
                            json.put("date", correctDateAnomaly(""))
                        }

                        val outT = json.optString("outTime", "")
                        val offT = json.optString("offTime", "")
                        val onT = json.optString("onTime", "")
                        val inT = json.optString("inTime", "")

                        // Ensure default Takeoff & Landing counts if not specified in CSV
                        val toDay = json.optInt("takeoffDay", 0)
                        val toNight = json.optInt("takeoffNight", 0)
                        val ldgDay = json.optInt("landingDay", 0)
                        val ldgNight = json.optInt("landingNight", 0)
                        val pfDep = json.optBoolean("pfFrom", true)
                        val pfArr = json.optBoolean("pfTo", true)

                        if (toDay == 0 && toNight == 0 && pfDep) {
                            json.put("takeoffDay", 1)
                        }
                        if (ldgDay == 0 && ldgNight == 0 && pfArr) {
                            json.put("landingDay", 1)
                        }

                        val calcBlockMin = calculateTimeDiffInMinutes(outT, inT)
                        val csvBlockHoursStr = json.optString("blockHours", "").trim()
                        val csvBlockMin = parseBlockMinutesFromLog(csvBlockHoursStr)

                        if (calcBlockMin != null && calcBlockMin > 0) {
                            if (csvBlockMin == null || Math.abs(csvBlockMin - calcBlockMin) > 1) {
                                json.put("blockHours", formatMinutesToHoursClean(calcBlockMin))
                            } else {
                                json.put("blockHours", formatMinutesToHoursClean(csvBlockMin))
                            }
                        } else if (csvBlockMin == null || csvBlockMin == 0) {
                            // If block times not provided, calculate from flight time (offTime to onTime)
                            val calcFlightMin = calculateTimeDiffInMinutes(offT, onT)
                            if (calcFlightMin != null && calcFlightMin > 0) {
                                json.put("blockHours", formatMinutesToHoursClean(calcFlightMin))
                            }
                        } else {
                            json.put("blockHours", formatMinutesToHoursClean(csvBlockMin))
                        }

                        val importedFlightNum = json.optString("flightNum", "").trim().uppercase()
                        val importedDate = json.optString("date", "").trim()
                        val importedFrom = json.optString("fromCode", "").trim().uppercase()
                        val importedTo = json.optString("toCode", "").trim().uppercase()
                        val importedTail = json.optString("tailNumber", "").trim().uppercase()
                        val importedType = json.optString("aircraftType", "").trim()

                        // Auto-add unknown airports with ICAO format
                        listOf(importedFrom, importedTo).forEach { code ->
                            if (code.length == 4 && code.all { it.isLetter() }) {
                                val alreadyExists = existingAirports.any { it.icao.equals(code, ignoreCase = true) || it.iata.equals(code, ignoreCase = true) } || addedAirports.contains(code)
                                if (!alreadyExists) {
                                    try {
                                        val newAirport = Airport(
                                            icao = code,
                                            iata = "",
                                            name = "$code Airport",
                                            country = "Unknown",
                                            city = "Unknown",
                                            approaches = "Visual",
                                            longestRunwayDesignator = "Unknown",
                                            longestRunwayLength = "Unknown",
                                            threats = "None",
                                            timezone = "Unknown",
                                            dstAssociated = "Unknown",
                                            category = "Cat A"
                                        )
                                        repository.insertAirport(newAirport)
                                        addedAirports.add(code)
                                    } catch (e: Exception) {
                                        // Ignore database insertion errors gracefully
                                    }
                                }
                            }
                        }

                        // Auto-add unknown aircraft
                        if (importedTail.isNotBlank()) {
                            val alreadyExists = existingAircrafts.any { it.reg.equals(importedTail, ignoreCase = true) } || addedAircrafts.contains(importedTail)
                            if (!alreadyExists) {
                                try {
                                    val targetType = if (importedType.isNotBlank()) importedType else "Unknown"
                                    val guessedEngineType = when {
                                        importedType.contains("Q400", ignoreCase = true) || 
                                        importedType.contains("ATR", ignoreCase = true) || 
                                        importedType.contains("prop", ignoreCase = true) || 
                                        importedType.contains("C172", ignoreCase = true) || 
                                        importedType.contains("Cessna", ignoreCase = true) -> "Turboprop"
                                        else -> "Jet"
                                    }
                                    val newAircraft = Aircraft(
                                        reg = importedTail,
                                        type = targetType
                                    )
                                    repository.insertAircraft(newAircraft)
                                    addedAircrafts.add(importedTail)

                                    // Check if this type exists in the database
                                    val hasType = existingAircraftTypes.any { it.code.equals(targetType, ignoreCase = true) }
                                    if (!hasType && !targetType.equals("Unknown", ignoreCase = true)) {
                                        val currentMissing = missingAircraftTypesToPrompt.value
                                        if (!currentMissing.contains(targetType)) {
                                            missingAircraftTypesToPrompt.value = currentMissing + targetType
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore database insertion errors gracefully
                                }
                            }
                        }

                        val existingDuplicateNote = notesList.find { note ->
                            if (note.content.startsWith("FLIGHTLOG::")) {
                                try {
                                    val existingJsonStr = note.content.substring("FLIGHTLOG::".length)
                                    val existingJson = org.json.JSONObject(existingJsonStr)
                                    val existingFlightNum = existingJson.optString("flightNum", "").trim().uppercase()
                                    val existingDate = existingJson.optString("date", "").trim()
                                    val existingFrom = existingJson.optString("fromCode", "").trim().uppercase()
                                    val existingTo = existingJson.optString("toCode", "").trim().uppercase()
                                    
                                    if (importedFlightNum.isNotBlank() && existingFlightNum.isNotBlank()) {
                                        importedFlightNum == existingFlightNum && importedDate == existingDate
                                    } else {
                                        importedDate == existingDate && importedFrom == existingFrom && importedTo == existingTo
                                    }
                                } catch (e: Exception) {
                                    false
                                }
                            } else {
                                false
                            }
                        }

                        if (existingDuplicateNote != null) {
                            repository.updateNote(existingDuplicateNote.id, "FLIGHTLOG::$json")
                        } else {
                            repository.insertNote("FLIGHTLOG::$json")
                        }
                        successCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                }

                if (successCount > 0) {
                    var msg = "Successfully imported $successCount flight logs!"
                    if (failCount > 0) {
                        msg += " (Failed to parse $failCount rows)"
                    }

                    val details = mutableListOf<String>()
                    if (addedAirports.isNotEmpty()) {
                        val examples = addedAirports.distinct().take(3).joinToString(", ")
                        details.add("• Added ${addedAirports.distinct().size} new airports (e.g. $examples)")
                    }
                    if (addedAircrafts.isNotEmpty()) {
                        val examples = addedAircrafts.distinct().take(3).joinToString(", ")
                        details.add("• Added ${addedAircrafts.distinct().size} new aircraft (e.g. $examples)")
                    }

                    if (details.isNotEmpty()) {
                        msg += "\n\nNew database entries automatic import:\n" + details.joinToString("\n")
                    }

                    importStatusMsg.value = msg
                    isSuccessStatus.value = true
                    if (!isCurrentlyViewingImportCsv) {
                        showImportCompletedDialog.value = msg
                    } else {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    importStatusMsg.value = "Failed to parse CSV file content."
                    isSuccessStatus.value = false
                }
            } catch (e: Exception) {
                importStatusMsg.value = "Failed to load CSV: ${e.localizedMessage}"
                isSuccessStatus.value = false
            } finally {
                importProgress.value = null
                importProgressRowText.value = ""
                isImporting.value = false
            }
        }
    }

    val config: StateFlow<EbLogConfig?> = repository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val tasks = repository.tasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var cachedNotes: List<EbLogNote> = emptyList()
    val notes = repository.notes
        .combine(isImporting) { list, importing ->
            if (!importing) {
                cachedNotes = list
                list
            } else {
                cachedNotes
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val posts = repository.posts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var cachedAirports: List<Airport> = emptyList()
    val airports = repository.airports
        .combine(isImporting) { list, importing ->
            if (!importing) {
                cachedAirports = list
                list
            } else {
                cachedAirports
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var cachedAircrafts: List<Aircraft> = emptyList()
    val aircrafts = repository.aircrafts
        .combine(isImporting) { list, importing ->
            if (!importing) {
                cachedAircrafts = list
                list
            } else {
                cachedAircrafts
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val aircraftTypes = repository.aircraftTypes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userProfileSettings = repository.userProfileSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val previousExperiences = repository.previousExperiences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveUserProfileSettings(settings: UserProfileSettings) {
        viewModelScope.launch {
            repository.saveUserProfileSettings(settings)
        }
    }

    fun insertPreviousExperience(experience: PreviousExperience) {
        viewModelScope.launch {
            repository.insertPreviousExperience(experience)
        }
    }

    fun deletePreviousExperience(id: Int) {
        viewModelScope.launch {
            repository.deletePreviousExperience(id)
        }
    }

    fun savePilotLimits(limits: List<com.example.HourLimit>) {
        viewModelScope.launch {
            repository.savePilotLimits(limits)
        }
    }

    fun saveConfig(name: String, industry: String, teamSize: String, enabledTools: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.saveConfig(
                EbLogConfig(
                    name = name,
                    industry = industry,
                    teamSize = teamSize,
                    enabledTools = enabledTools,
                    isOnboardingCompleted = isCompleted
                )
            )
        }
    }

    fun insertTask(title: String) {
        viewModelScope.launch {
            repository.insertTask(title)
        }
    }

    fun updateTaskStatus(id: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateTaskStatus(id, isCompleted)
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    suspend fun insertNote(content: String) {
        repository.insertNote(content)
    }

    suspend fun updateNote(id: Int, content: String) {
        repository.updateNote(id, content)
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun insertPost(author: String, content: String) {
        viewModelScope.launch {
            repository.insertPost(author, content)
        }
    }

    fun deletePost(id: Int) {
        viewModelScope.launch {
            repository.deletePost(id)
        }
    }

    fun insertAirport(airport: Airport) {
        viewModelScope.launch {
            repository.insertAirport(airport)
        }
    }

    fun deleteAirport(icao: String) {
        viewModelScope.launch {
            repository.deleteAirport(icao)
        }
    }

    fun insertAircraft(aircraft: Aircraft) {
        viewModelScope.launch {
            repository.insertAircraft(aircraft)
        }
    }

    fun deleteAircraft(reg: String) {
        viewModelScope.launch {
            repository.deleteAircraft(reg)
        }
    }

    fun insertAircraftType(aircraftType: AircraftType) {
        viewModelScope.launch {
            repository.insertAircraftType(aircraftType)
        }
    }

    fun deleteAircraftType(code: String) {
        viewModelScope.launch {
            repository.deleteAircraftType(code)
        }
    }

    fun remapAircraftType(oldTypeCode: String, newTypeCode: String) {
        viewModelScope.launch {
            try {
                val allNotes = repository.notes.first()
                allNotes.forEach { note ->
                    if (note.content.startsWith("FLIGHTLOG::")) {
                        val jsonStr = note.content.substring("FLIGHTLOG::".length)
                        val json = org.json.JSONObject(jsonStr)
                        val typeCode = json.optString("aircraftType", "")
                        if (typeCode.equals(oldTypeCode, ignoreCase = true)) {
                            json.put("aircraftType", newTypeCode)
                            repository.updateNote(note.id, "FLIGHTLOG::$json")
                        }
                    }
                }
                
                // Also update any Aircraft in the database that has this type code!
                val aircrafts = repository.aircrafts.first()
                aircrafts.forEach { ac ->
                    if (ac.type.equals(oldTypeCode, ignoreCase = true)) {
                        repository.insertAircraft(ac.copy(type = newTypeCode))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearFlightLogs() {
        viewModelScope.launch {
            repository.clearFlightLogs()
        }
    }

    fun clearAirports() {
        viewModelScope.launch {
            repository.clearAirports()
        }
    }

    fun clearAircrafts() {
        viewModelScope.launch {
            repository.clearAircrafts()
        }
    }

    fun clearAircraftTypes() {
        viewModelScope.launch {
            repository.clearAircraftTypes()
        }
    }

    fun clearSettings() {
        viewModelScope.launch {
            repository.clearSettings()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }
}
