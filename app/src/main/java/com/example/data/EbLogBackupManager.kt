package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupPackageSummary(
    val exportDate: String,
    val exportTimestamp: Long,
    val appName: String,
    val version: Int,
    val flightLogsCount: Int,
    val notesCount: Int,
    val airportsCount: Int,
    val aircraftsCount: Int,
    val aircraftTypesCount: Int,
    val hasUserProfile: Boolean,
    val hasAppSettings: Boolean,
    val userProfileName: String?,
    val rawJson: JSONObject
)

data class RestoreSelection(
    var restoreFlightLogs: Boolean = true,
    var restoreAirports: Boolean = true,
    var restoreAircrafts: Boolean = true,
    var restoreUserProfile: Boolean = true,
    var restoreAppSettings: Boolean = true,
    var replaceExisting: Boolean = false // false = merge, true = replace
) {
    val totalSelectedCount: Int
        get() = listOf(
            restoreFlightLogs,
            restoreAirports,
            restoreAircrafts,
            restoreUserProfile,
            restoreAppSettings
        ).count { it }
}

data class RestoreResult(
    val success: Boolean,
    val flightsRestored: Int = 0,
    val airportsRestored: Int = 0,
    val aircraftsRestored: Int = 0,
    val typesRestored: Int = 0,
    val userProfileRestored: Boolean = false,
    val appSettingsRestored: Boolean = false,
    val errorMessage: String? = null
)

object EbLogBackupManager {

    suspend fun createFullBackupJson(
        repository: EbLogRepository,
        sharedPreferences: SharedPreferences
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject()

        // 1. Metadata
        val meta = JSONObject().apply {
            put("appName", "ebLog")
            put("formatVersion", 1)
            put("timestamp", System.currentTimeMillis())
            put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        }
        root.put("metadata", meta)

        // 2. User Profile (from Room and SharedPreferences)
        val profileSettings = repository.getUserProfileSettingsValue() ?: UserProfileSettings()
        val profileObj = JSONObject().apply {
            put("fullName", profileSettings.fullName)
            put("role", profileSettings.role)
            put("airline", profileSettings.airline)
            put("experience", profileSettings.experience)
            put("avatarStyle", profileSettings.avatarStyle)
            put("prefAirlinePrefix", profileSettings.prefAirlinePrefix)
            put("prefTailPrefix", profileSettings.prefTailPrefix)
            put("prefCrewSize", profileSettings.prefCrewSize)
            put("prefPilotRole", profileSettings.prefPilotRole)
            put("prefFlightRules", profileSettings.prefFlightRules)
            put("pilotLimitsJson", profileSettings.pilotLimitsJson)
            put("previousExperienceJson", profileSettings.previousExperienceJson)
            // Complement with SharedPreferences profile values if present
            put("sharedPrefUserName", sharedPreferences.getString("user_name", profileSettings.fullName))
            put("sharedPrefProfileRole", sharedPreferences.getString("profile_role", profileSettings.role))
            put("sharedPrefProfileAirline", sharedPreferences.getString("profile_airline", profileSettings.airline))
            put("sharedPrefProfilePicUri", sharedPreferences.getString("profile_pic_uri", ""))
        }
        root.put("userProfile", profileObj)

        // 3. App Settings (SharedPreferences app_prefs)
        val settingsObj = JSONObject()
        val allPrefs = sharedPreferences.all
        for ((key, value) in allPrefs) {
            when (value) {
                is String -> settingsObj.put(key, value)
                is Int -> settingsObj.put(key, value)
                is Boolean -> settingsObj.put(key, value)
                is Float -> settingsObj.put(key, value.toDouble())
                is Long -> settingsObj.put(key, value)
            }
        }
        root.put("appSettings", settingsObj)

        // 4. Notes and Flight Logs
        val allNotes = repository.getAllNotesList()
        val notesArray = JSONArray()
        val flightLogsArray = JSONArray()
        for (note in allNotes) {
            val noteObj = JSONObject().apply {
                put("id", note.id)
                put("content", note.content)
                put("timestamp", note.timestamp)
            }
            notesArray.put(noteObj)
            if (note.content.startsWith("FLIGHTLOG::")) {
                flightLogsArray.put(noteObj)
            }
        }
        root.put("allNotes", notesArray)
        root.put("flightLogs", flightLogsArray)

        // 5. Airports
        val airports = repository.getAllAirportsList()
        val airportsArray = JSONArray()
        for (arpt in airports) {
            val arptObj = JSONObject().apply {
                put("icao", arpt.icao)
                put("iata", arpt.iata)
                put("name", arpt.name)
                put("country", arpt.country)
                put("city", arpt.city)
                put("approaches", arpt.approaches)
                put("longestRunwayDesignator", arpt.longestRunwayDesignator)
                put("longestRunwayLength", arpt.longestRunwayLength)
                put("threats", arpt.threats)
                put("timezone", arpt.timezone)
                put("dstAssociated", arpt.dstAssociated)
                put("category", arpt.category)
            }
            airportsArray.put(arptObj)
        }
        root.put("airports", airportsArray)

        // 6. Aircraft Fleet
        val aircrafts = repository.getAllAircraftsList()
        val aircraftsArray = JSONArray()
        for (ac in aircrafts) {
            val acObj = JSONObject().apply {
                put("reg", ac.reg)
                put("type", ac.type)
            }
            aircraftsArray.put(acObj)
        }
        root.put("aircrafts", aircraftsArray)

        // 7. Aircraft Types
        val aircraftTypes = repository.getAllAircraftTypesList()
        val typesArray = JSONArray()
        for (type in aircraftTypes) {
            val typeObj = JSONObject().apply {
                put("code", type.code)
                put("name", type.name)
                put("manufacturer", type.manufacturer)
                put("category", type.category)
                put("engineType", type.engineType)
            }
            typesArray.put(typeObj)
        }
        root.put("aircraftTypes", typesArray)

        root.toString(2)
    }

    suspend fun exportDatabaseToFile(
        context: Context,
        uri: Uri,
        repository: EbLogRepository,
        sharedPreferences: SharedPreferences
    ): Result<BackupPackageSummary> = withContext(Dispatchers.IO) {
        try {
            val jsonString = createFullBackupJson(repository, sharedPreferences)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: return@withContext Result.failure(Exception("Could not open output stream for selected file"))

            val summary = parseBackupJsonString(jsonString)
                ?: return@withContext Result.failure(Exception("Failed to verify created backup package"))

            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseBackupFile(context: Context, uri: Uri): Result<BackupPackageSummary> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            } ?: return Result.failure(Exception("Could not read backup file"))

            val summary = parseBackupJsonString(jsonString)
                ?: return Result.failure(Exception("The selected file is not a valid ebLog backup JSON format."))

            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseBackupJsonString(jsonString: String): BackupPackageSummary? {
        return try {
            val root = JSONObject(jsonString)
            val meta = root.optJSONObject("metadata")
            val appName = meta?.optString("appName") ?: root.optString("appName", "ebLog")
            val version = meta?.optInt("formatVersion", 1) ?: 1
            val timestamp = meta?.optLong("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()
            val exportDate = meta?.optString("exportDate") ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))

            val allNotesArray = root.optJSONArray("allNotes")
            val flightLogsArray = root.optJSONArray("flightLogs")
            val notesCount = allNotesArray?.length() ?: 0
            val flightCount = flightLogsArray?.length() ?: notesCount

            val airportsArray = root.optJSONArray("airports")
            val airportsCount = airportsArray?.length() ?: 0

            val aircraftsArray = root.optJSONArray("aircrafts")
            val aircraftsCount = aircraftsArray?.length() ?: 0

            val aircraftTypesArray = root.optJSONArray("aircraftTypes")
            val aircraftTypesCount = aircraftTypesArray?.length() ?: 0

            val userProfileObj = root.optJSONObject("userProfile")
            val hasUserProfile = userProfileObj != null
            val userProfileName = userProfileObj?.optString("fullName", null)

            val appSettingsObj = root.optJSONObject("appSettings")
            val hasAppSettings = appSettingsObj != null && appSettingsObj.length() > 0

            BackupPackageSummary(
                exportDate = exportDate,
                exportTimestamp = timestamp,
                appName = appName,
                version = version,
                flightLogsCount = flightCount,
                notesCount = notesCount,
                airportsCount = airportsCount,
                aircraftsCount = aircraftsCount,
                aircraftTypesCount = aircraftTypesCount,
                hasUserProfile = hasUserProfile,
                hasAppSettings = hasAppSettings,
                userProfileName = userProfileName,
                rawJson = root
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restoreDatabaseFromFile(
        context: Context,
        summary: BackupPackageSummary,
        selection: RestoreSelection,
        repository: EbLogRepository,
        sharedPreferences: SharedPreferences
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = summary.rawJson
            var flightsCount = 0
            var airportsCount = 0
            var aircraftsCount = 0
            var typesCount = 0
            var profileRestored = false
            var settingsRestored = false

            // 1. Restore Flight Logs and Notes
            if (selection.restoreFlightLogs) {
                val notesArray = root.optJSONArray("allNotes") ?: root.optJSONArray("flightLogs")
                if (notesArray != null && notesArray.length() > 0) {
                    val notesToInsert = mutableListOf<EbLogNote>()
                    for (i in 0 until notesArray.length()) {
                        val obj = notesArray.getJSONObject(i)
                        val content = obj.optString("content", "")
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        if (content.isNotBlank()) {
                            notesToInsert.add(EbLogNote(content = content, timestamp = timestamp))
                        }
                    }

                    if (selection.replaceExisting) {
                        repository.clearFlightLogs()
                        repository.insertNotes(notesToInsert)
                        flightsCount = notesToInsert.size
                    } else {
                        // Merge mode: Only insert notes whose content is not already present
                        val existingContents = repository.getAllNotesList().map { it.content }.toSet()
                        val newNotes = notesToInsert.filter { !existingContents.contains(it.content) }
                        if (newNotes.isNotEmpty()) {
                            repository.insertNotes(newNotes)
                        }
                        flightsCount = if (newNotes.isNotEmpty()) newNotes.size else notesToInsert.size
                    }
                }
            }

            // 2. Restore Airports
            if (selection.restoreAirports) {
                val airportsArray = root.optJSONArray("airports")
                if (airportsArray != null && airportsArray.length() > 0) {
                    val airportsList = mutableListOf<Airport>()
                    for (i in 0 until airportsArray.length()) {
                        val obj = airportsArray.getJSONObject(i)
                        val icao = obj.optString("icao", "").trim().uppercase()
                        if (icao.isNotBlank()) {
                            airportsList.add(
                                Airport(
                                    icao = icao,
                                    iata = obj.optString("iata", "---").trim().uppercase(),
                                    name = obj.optString("name", "Unknown Airport"),
                                    country = obj.optString("country", "Unknown"),
                                    city = obj.optString("city", "Unknown"),
                                    approaches = obj.optString("approaches", ""),
                                    longestRunwayDesignator = obj.optString("longestRunwayDesignator", ""),
                                    longestRunwayLength = obj.optString("longestRunwayLength", ""),
                                    threats = obj.optString("threats", ""),
                                    timezone = obj.optString("timezone", "UTC"),
                                    dstAssociated = obj.optString("dstAssociated", "No"),
                                    category = obj.optString("category", "Cat A")
                                )
                            )
                        }
                    }

                    if (selection.replaceExisting) {
                        repository.clearAirports()
                    }
                    if (airportsList.isNotEmpty()) {
                        repository.insertAirports(airportsList)
                        airportsCount = airportsList.size
                    }
                }
            }

            // 3. Restore Aircraft Fleet and Aircraft Types
            if (selection.restoreAircrafts) {
                val aircraftsArray = root.optJSONArray("aircrafts")
                if (aircraftsArray != null && aircraftsArray.length() > 0) {
                    val acList = mutableListOf<Aircraft>()
                    for (i in 0 until aircraftsArray.length()) {
                        val obj = aircraftsArray.getJSONObject(i)
                        val reg = obj.optString("reg", "").trim().uppercase()
                        val type = obj.optString("type", "").trim().uppercase()
                        if (reg.isNotBlank()) {
                            acList.add(Aircraft(reg = reg, type = type))
                        }
                    }
                    if (selection.replaceExisting) {
                        repository.clearAircrafts()
                    }
                    if (acList.isNotEmpty()) {
                        repository.insertAircrafts(acList)
                        aircraftsCount = acList.size
                    }
                }

                val typesArray = root.optJSONArray("aircraftTypes")
                if (typesArray != null && typesArray.length() > 0) {
                    val typeList = mutableListOf<AircraftType>()
                    for (i in 0 until typesArray.length()) {
                        val obj = typesArray.getJSONObject(i)
                        val code = obj.optString("code", "").trim().uppercase()
                        if (code.isNotBlank()) {
                            typeList.add(
                                AircraftType(
                                    code = code,
                                    name = obj.optString("name", code),
                                    manufacturer = obj.optString("manufacturer", "Unknown"),
                                    category = obj.optString("category", "MEL"),
                                    engineType = obj.optString("engineType", "Turbo Jet")
                                )
                            )
                        }
                    }
                    if (selection.replaceExisting) {
                        repository.clearAircraftTypes()
                    }
                    if (typeList.isNotEmpty()) {
                        repository.insertAircraftTypes(typeList)
                        typesCount = typeList.size
                    }
                }
            }

            // 4. Restore User Profile & Information
            if (selection.restoreUserProfile) {
                val profileObj = root.optJSONObject("userProfile")
                if (profileObj != null) {
                    val currentSettings = repository.getUserProfileSettingsValue() ?: UserProfileSettings()
                    val newSettings = currentSettings.copy(
                        fullName = profileObj.optString("fullName", currentSettings.fullName),
                        role = profileObj.optString("role", currentSettings.role),
                        airline = profileObj.optString("airline", currentSettings.airline),
                        experience = profileObj.optString("experience", currentSettings.experience),
                        avatarStyle = profileObj.optString("avatarStyle", currentSettings.avatarStyle),
                        prefAirlinePrefix = profileObj.optString("prefAirlinePrefix", currentSettings.prefAirlinePrefix),
                        prefTailPrefix = profileObj.optString("prefTailPrefix", currentSettings.prefTailPrefix),
                        prefCrewSize = profileObj.optInt("prefCrewSize", currentSettings.prefCrewSize),
                        prefPilotRole = profileObj.optString("prefPilotRole", currentSettings.prefPilotRole),
                        prefFlightRules = profileObj.optString("prefFlightRules", currentSettings.prefFlightRules),
                        pilotLimitsJson = profileObj.optString("pilotLimitsJson", currentSettings.pilotLimitsJson),
                        previousExperienceJson = profileObj.optString("previousExperienceJson", currentSettings.previousExperienceJson)
                    )
                    repository.saveUserProfileSettings(newSettings)

                    // Also sync SharedPreferences profile values
                    sharedPreferences.edit().apply {
                        if (profileObj.has("fullName")) putString("user_name", profileObj.getString("fullName"))
                        if (profileObj.has("role")) putString("profile_role", profileObj.getString("role"))
                        if (profileObj.has("airline")) putString("profile_airline", profileObj.getString("airline"))
                        if (profileObj.has("avatarStyle")) putString("profile_pic_uri", profileObj.getString("avatarStyle"))
                        if (profileObj.has("prefAirlinePrefix")) putString("pref_airline_prefix", profileObj.getString("prefAirlinePrefix"))
                        if (profileObj.has("prefTailPrefix")) putString("pref_tail_prefix", profileObj.getString("prefTailPrefix"))
                        if (profileObj.has("prefCrewSize")) putInt("pref_crew_size", profileObj.getInt("prefCrewSize"))
                        if (profileObj.has("prefPilotRole")) putString("pref_pilot_role", profileObj.getString("prefPilotRole"))
                        if (profileObj.has("prefFlightRules")) putString("pref_flight_rules", profileObj.getString("prefFlightRules"))
                        apply()
                    }
                    profileRestored = true
                }
            }

            // 5. Restore App Settings & Preferences
            if (selection.restoreAppSettings) {
                val settingsObj = root.optJSONObject("appSettings")
                if (settingsObj != null && settingsObj.length() > 0) {
                    sharedPreferences.edit().apply {
                        val keys = settingsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = settingsObj.get(key)
                            when (value) {
                                is String -> putString(key, value)
                                is Boolean -> putBoolean(key, value)
                                is Int -> putInt(key, value)
                                is Long -> putLong(key, value)
                                is Double -> putFloat(key, value.toFloat())
                            }
                        }
                        apply()
                    }
                    settingsRestored = true
                }
            }

            RestoreResult(
                success = true,
                flightsRestored = flightsCount,
                airportsRestored = airportsCount,
                aircraftsRestored = aircraftsCount,
                typesRestored = typesCount,
                userProfileRestored = profileRestored,
                appSettingsRestored = settingsRestored
            )
        } catch (e: Exception) {
            RestoreResult(
                success = false,
                errorMessage = e.message ?: "Unknown restore error"
            )
        }
    }
}
