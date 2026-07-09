package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class WorkspaceRepository(private val db: WorkspaceDatabase) {
    // Unused config, tasks, and posts now return static empty flow values since they are not used by the app.
    val config: Flow<WorkspaceConfig?> = flowOf(null)
    val tasks: Flow<List<WorkspaceTask>> = flowOf(emptyList())
    val posts: Flow<List<TeamPost>> = flowOf(emptyList())

    val notes: Flow<List<WorkspaceNote>> = db.noteDao().getAllNotes()
    val airports: Flow<List<Airport>> = db.airportDao().getAllAirports()
    val aircrafts: Flow<List<Aircraft>> = db.aircraftDao().getAllAircrafts()
    val userProfileSettings: Flow<UserProfileSettings?> = db.userProfileSettingsDao().getUserProfileSettings()
    val previousExperiences: Flow<List<PreviousExperience>> = db.userProfileSettingsDao().getUserProfileSettings()
        .map { settings -> settings?.getPreviousExperiences() ?: emptyList() }

    suspend fun saveConfig(config: WorkspaceConfig) {
        // No-op, config is unused
    }

    suspend fun saveUserProfileSettings(settings: UserProfileSettings) {
        db.userProfileSettingsDao().saveUserProfileSettings(settings)
    }

    suspend fun insertPreviousExperience(experience: PreviousExperience) {
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

    suspend fun deletePreviousExperience(id: Int) {
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

    suspend fun savePilotLimits(limits: List<com.example.HourLimit>) {
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

    suspend fun insertNote(content: String) {
        db.noteDao().insertNote(WorkspaceNote(content = content))
    }

    suspend fun updateNote(id: Int, content: String) {
        db.noteDao().insertNote(WorkspaceNote(id = id, content = content))
    }

    suspend fun deleteNote(id: Int) {
        db.noteDao().deleteNote(id)
    }

    suspend fun insertPost(author: String, content: String) {
        // No-op, posts are unused
    }

    suspend fun deletePost(id: Int) {
        // No-op, posts are unused
    }

    suspend fun insertAirport(airport: Airport) {
        db.airportDao().insertAirport(airport)
    }

    suspend fun deleteAirport(icao: String) {
        db.airportDao().deleteAirport(icao)
    }

    suspend fun insertAircraft(aircraft: Aircraft) {
        db.aircraftDao().insertAircraft(aircraft)
    }

    suspend fun deleteAircraft(reg: String) {
        db.aircraftDao().deleteAircraft(reg)
    }

    suspend fun clearAllData() {
        db.noteDao().deleteAllNotes()
        db.airportDao().deleteAllAirports()
        db.aircraftDao().deleteAllAircrafts()
        
        val currentSettings = db.userProfileSettingsDao().getUserProfileSettingsValue() ?: UserProfileSettings()
        val clearedSettings = currentSettings.copy(
            previousExperienceJson = "[]",
            pilotLimitsJson = "[]"
        )
        db.userProfileSettingsDao().saveUserProfileSettings(clearedSettings)
    }
}
