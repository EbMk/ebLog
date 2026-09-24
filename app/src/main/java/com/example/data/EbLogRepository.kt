package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class EbLogRepository(private val db: EbLogDatabase) {
    // Unused config, tasks, and posts now return static empty flow values since they are not used by the app.
    val config: Flow<EbLogConfig?> = flowOf(null)
    val tasks: Flow<List<EbLogTask>> = flowOf(emptyList())
    val posts: Flow<List<TeamPost>> = flowOf(emptyList())

    val notes: Flow<List<EbLogNote>> = db.noteDao().getAllNotes()
    val airports: Flow<List<Airport>> = db.airportDao().getAllAirports()
    val aircrafts: Flow<List<Aircraft>> = db.aircraftDao().getAllAircrafts()
    val aircraftTypes: Flow<List<AircraftType>> = db.aircraftTypeDao().getAllAircraftTypes()
    val userProfileSettings: Flow<UserProfileSettings?> = db.userProfileSettingsDao().getUserProfileSettings()
    val previousExperiences: Flow<List<PreviousExperience>> = db.userProfileSettingsDao().getUserProfileSettings()
        .map { settings -> settings?.getPreviousExperiences() ?: emptyList() }

    suspend fun saveConfig(config: EbLogConfig) {
        // No-op, config is unused
    }

    suspend fun saveUserProfileSettings(settings: UserProfileSettings) = withContext(Dispatchers.IO) {
        db.userProfileSettingsDao().saveUserProfileSettings(settings)
    }

    suspend fun insertPreviousExperience(experience: PreviousExperience) = withContext(Dispatchers.IO) {
        val currentSettings = db.userProfileSettingsDao().getUserProfileSettingsValue() ?: UserProfileSettings()
        val currentList = currentSettings.getPreviousExperiences()
        val newId = (currentList.maxOfOrNull { it.id } ?: 0) + 1
        val updatedList = currentList + experience.copy(id = newId)
        
        val array = org.json.JSONArray()
        updatedList.forEach { exp ->
            val obj = org.json.JSONObject()
            obj.put("id", exp.id)
            obj.put("aircraftType", exp.aircraftType)
            obj.put("pilotRole", exp.pilotRole)
            obj.put("totalHours", exp.totalHours)
            array.put(obj)
        }
        
        val newSettings = currentSettings.copy(previousExperienceJson = array.toString())
        db.userProfileSettingsDao().saveUserProfileSettings(newSettings)
    }

    suspend fun deletePreviousExperience(id: Int) = withContext(Dispatchers.IO) {
        val currentSettings = db.userProfileSettingsDao().getUserProfileSettingsValue() ?: UserProfileSettings()
        val currentList = currentSettings.getPreviousExperiences()
        val updatedList = currentList.filter { it.id != id }
        
        val array = org.json.JSONArray()
        updatedList.forEach { exp ->
            val obj = org.json.JSONObject()
            obj.put("id", exp.id)
            obj.put("aircraftType", exp.aircraftType)
            obj.put("pilotRole", exp.pilotRole)
            obj.put("totalHours", exp.totalHours)
            array.put(obj)
        }
        
        val newSettings = currentSettings.copy(previousExperienceJson = array.toString())
        db.userProfileSettingsDao().saveUserProfileSettings(newSettings)
    }

    suspend fun savePilotLimits(limits: List<com.example.HourLimit>) = withContext(Dispatchers.IO) {
        val currentSettings = db.userProfileSettingsDao().getUserProfileSettingsValue() ?: UserProfileSettings()
        val array = org.json.JSONArray()
        limits.forEach { limit ->
            val obj = org.json.JSONObject()
            obj.put("id", limit.id)
            obj.put("days", limit.days)
            obj.put("hours", limit.hours)
            array.put(obj)
        }
        val newSettings = currentSettings.copy(pilotLimitsJson = array.toString())
        db.userProfileSettingsDao().saveUserProfileSettings(newSettings)
    }

    suspend fun insertTask(title: String) {
        // No-op, tasks are unused
    }

    suspend fun updateTaskStatus(id: Int, isCompleted: Boolean) {
        // No-op, tasks are unused
    }

    suspend fun deleteTask(id: Int) {
        // No-op, tasks are unused
    }

    suspend fun insertNote(content: String) = withContext(Dispatchers.IO) {
        db.noteDao().insertNote(EbLogNote(content = content))
    }

    suspend fun updateNote(id: Int, content: String) = withContext(Dispatchers.IO) {
        db.noteDao().insertNote(EbLogNote(id = id, content = content))
    }

    suspend fun deleteNote(id: Int) = withContext(Dispatchers.IO) {
        db.noteDao().deleteNote(id)
    }

    suspend fun insertPost(author: String, content: String) {
        // No-op, posts are unused
    }

    suspend fun deletePost(id: Int) {
        // No-op, posts are unused
    }

    suspend fun insertAirport(airport: Airport) = withContext(Dispatchers.IO) {
        db.airportDao().insertAirport(airport)
    }

    suspend fun deleteAirport(icao: String) = withContext(Dispatchers.IO) {
        db.airportDao().deleteAirport(icao)
    }

    suspend fun insertAircraft(aircraft: Aircraft) = withContext(Dispatchers.IO) {
        db.aircraftDao().insertAircraft(aircraft)
    }

    suspend fun deleteAircraft(reg: String) = withContext(Dispatchers.IO) {
        db.aircraftDao().deleteAircraft(reg)
    }

    suspend fun insertAircraftType(aircraftType: AircraftType) = withContext(Dispatchers.IO) {
        db.aircraftTypeDao().insertAircraftType(aircraftType)
    }

    suspend fun deleteAircraftType(code: String) = withContext(Dispatchers.IO) {
        db.aircraftTypeDao().deleteAircraftType(code)
    }

    suspend fun clearFlightLogs() = withContext(Dispatchers.IO) {
        db.noteDao().deleteAllNotes()
    }

    suspend fun clearAirports() = withContext(Dispatchers.IO) {
        db.airportDao().deleteAllAirports()
    }

    suspend fun clearAircrafts() = withContext(Dispatchers.IO) {
        db.aircraftDao().deleteAllAircrafts()
    }

    suspend fun clearAircraftTypes() = withContext(Dispatchers.IO) {
        db.aircraftTypeDao().deleteAllAircraftTypes()
    }

    suspend fun clearSettings() = withContext(Dispatchers.IO) {
        db.userProfileSettingsDao().saveUserProfileSettings(UserProfileSettings())
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        db.noteDao().deleteAllNotes()
        db.airportDao().deleteAllAirports()
        db.aircraftDao().deleteAllAircrafts()
        db.aircraftTypeDao().deleteAllAircraftTypes()
        
        val currentSettings = db.userProfileSettingsDao().getUserProfileSettingsValue() ?: UserProfileSettings()
        val clearedSettings = currentSettings.copy(
            previousExperienceJson = "[]",
            pilotLimitsJson = "[]"
        )
        db.userProfileSettingsDao().saveUserProfileSettings(clearedSettings)
    }
}
