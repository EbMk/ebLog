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

class EbLogViewModel(private val repository: EbLogRepository) : ViewModel() {

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
                val headers = parseCsvLine(firstLine)

                val hasHeader = headers.any { h ->
                    val cleanH = h.lowercase()
                    cleanH.contains("flight") || cleanH.contains("date") || cleanH.contains("tail") || cleanH.contains("from") || cleanH.contains("to") || cleanH.contains("reg")
                }

                val startIndex = if (hasHeader) 1 else 0
                val headerMap = mutableMapOf<Int, String>()

                if (hasHeader) {
                    headers.forEachIndexed { index, header ->
                        val hClean = header.lowercase().trim()
                        val mappedKey = when {
                            hClean.contains("flight number") || hClean.contains("flightnum") || hClean == "flight" || hClean.contains("flight_num") || hClean.contains("flightnumber") -> "flightNum"
                            hClean == "date" -> "date"
                            hClean.contains("registration") || hClean.contains("tailnumber") || hClean == "tail" || hClean.contains("tail_number") || hClean == "reg" || hClean.contains("reg") -> "tailNumber"
                            hClean.contains("aircrafttype") || hClean.contains("aircraft type") || hClean.contains("aircraft_type") || hClean.contains("aircraft") || hClean == "type" -> "aircraftType"
                            hClean.contains("crew size") || hClean.contains("crew count") || hClean.contains("crew number") || hClean.contains("number of crew") || hClean.contains("crew_size") || hClean.contains("crew_count") || hClean.contains("crew_number") || hClean == "crewcount" || hClean == "crewsize" || hClean == "crewnumber" -> "crewCount"
                            hClean == "crew" || hClean.contains("copilot") || hClean == "crews" -> "crew"
                            hClean == "employer" || hClean == "company" || hClean == "operator" || hClean == "airlines" -> "employer"
                            hClean.contains("fromcode") || hClean == "from" || hClean.contains("origin") || hClean == "dep" || hClean == "dept" -> "fromCode"
                            hClean.contains("tocode") || hClean == "to" || hClean.contains("dest") || hClean.contains("destination") || hClean == "arr" -> "toCode"
                            hClean.contains("outtime") || hClean.contains("out_time") || hClean.contains("block out time") || hClean.contains("blockout") || hClean.contains("block_out") || hClean == "out" -> "outTime"
                            hClean.contains("offtime") || hClean == "off" || hClean.contains("takeoff") -> "offTime"
                            hClean.contains("ontime") || hClean == "on" || hClean.contains("landing") -> "onTime"
                            hClean.contains("intime") || hClean.contains("in_time") || hClean.contains("block in time") || hClean.contains("blockin") || hClean.contains("block_in") || hClean == "in" -> "inTime"
                            hClean.contains("pffrom") || hClean == "pf_from" -> "pfFrom"
                            hClean.contains("pfto") || hClean == "pf_to" -> "pfTo"
                            hClean.contains("day takeoff") || hClean.contains("takeoffday") || hClean.contains("takeoff_day") || hClean.contains("day_takeoff") -> "takeoffDay"
                            hClean.contains("night takeoff") || hClean.contains("takeoffnight") || hClean.contains("takeoff_night") || hClean.contains("night_takeoff") -> "takeoffNight"
                            hClean.contains("day landing") || hClean.contains("landingday") || hClean.contains("landing_day") || hClean.contains("day_landing") -> "landingDay"
                            hClean.contains("night landing") || hClean.contains("landingnight") || hClean.contains("landing_night") || hClean.contains("night_landing") -> "landingNight"
                            hClean.contains("approachtype") || hClean == "approach" || hClean.contains("approach_type") -> "approachType"
                            hClean.contains("crew role") || hClean.contains("pilot role") || hClean.contains("pilotrole") || hClean.contains("pilot_role") || hClean == "role" || hClean.contains("role") -> "pilotRole"
                            hClean.contains("flightrules") || hClean == "rules" || hClean.contains("flight_rules") -> "flightRules"
                            hClean == "remarks" || hClean == "remark" || hClean.contains("comment") || hClean == "comments" -> "remarks"
                            hClean.contains("night hours") || hClean.contains("night_hours") || hClean.contains("night time") || hClean.contains("nighttime") || hClean == "night" -> "nightTime"
                            hClean.contains("block hours") || hClean.contains("block_hours") || hClean.contains("pay hours") || hClean.contains("pay_hours") || hClean.contains("total block") || hClean.contains("blocktime") || hClean.contains("block time") -> "blockHours"
                            else -> null
                        }
                        if (mappedKey != null) {
                            headerMap[index] = mappedKey
                        }
                    }
                } else {
                    val defaultOrder = listOf(
                        "flightNum", "date", "tailNumber", "aircraftType", "crew", "employer",
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
                        val rowValues = parseCsvLine(lineText)
                        if (rowValues.isEmpty()) continue

                        val json = org.json.JSONObject()
                        json.put("flightNum", "")
                        json.put("date", "")
                        json.put("tailNumber", "")
                        json.put("aircraftType", "")
                        json.put("crew", "")
                        json.put("employer", "EM")
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
                            if (key != null) {
                                when (key) {
                                    "pfFrom", "pfTo" -> {
                                        val boolVal = valStr.lowercase() == "true" || valStr == "1" || valStr.lowercase() == "yes"
                                        json.put(key, boolVal)
                                    }
                                    "takeoffDay", "takeoffNight", "landingDay", "landingNight" -> {
                                        val intVal = valStr.toIntOrNull() ?: 0
                                        json.put(key, intVal)
                                    }
                                    "date" -> {
                                        val corrected = correctDateAnomaly(valStr)
                                        json.put(key, corrected)
                                    }
                                    "crew" -> {
                                        val cleanVal = valStr.trim()
                                        val intVal = cleanVal.toIntOrNull()
                                        if (intVal != null && intVal > 0) {
                                            importedCrewCount = intVal
                                        } else {
                                            importedCrew = cleanVal
                                        }
                                    }
                                    "crewCount" -> {
                                        val intVal = valStr.trim().toIntOrNull()
                                        if (intVal != null && intVal > 0) {
                                            importedCrewCount = intVal
                                        }
                                    }
                                    else -> {
                                        json.put(key, valStr)
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
                        val inT = json.optString("inTime", "")
                        val calcMin = calculateTimeDiffInMinutes(outT, inT)
                        if (calcMin != null) {
                            val calcHours = calcMin / 60.0
                            val calcHoursRounded = Math.round(calcHours * 100.0) / 100.0
                            val csvBlockHoursStr = json.optString("blockHours", "").trim()
                            val csvBlockHours = csvBlockHoursStr.toDoubleOrNull()
                            if (csvBlockHours == null || Math.abs(csvBlockHours - calcHoursRounded) > 0.02) {
                                json.put("blockHours", String.format(Locale.US, "%.2f", calcHoursRounded))
                            }
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
