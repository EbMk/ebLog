package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(
    tableName = "eblog_notes",
    indices = [Index(value = ["timestamp"])]
)
data class EbLogNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Simple stub models for compatibility (not database entities)
data class EbLogConfig(
    val id: Int = 0,
    val name: String = "",
    val industry: String = "",
    val teamSize: String = "",
    val enabledTools: String = "",
    val isOnboardingCompleted: Boolean = false
)

data class EbLogTask(
    val id: Int = 0,
    val title: String = "",
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class TeamPost(
    val id: Int = 0,
    val author: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "airports",
    indices = [Index(value = ["icao"]), Index(value = ["country"]), Index(value = ["city"])]
)
data class Airport(
    @PrimaryKey val icao: String,
    val iata: String,
    val name: String,
    val country: String,
    val city: String,
    val approaches: String,
    val longestRunwayDesignator: String,
    val longestRunwayLength: String,
    val threats: String,
    val timezone: String,
    val dstAssociated: String,
    val category: String = "Cat A"
)

@Entity(
    tableName = "aircrafts",
    indices = [Index(value = ["reg"]), Index(value = ["type"])]
)
data class Aircraft(
    @PrimaryKey val reg: String,
    val type: String
)

@Entity(
    tableName = "aircraft_types",
    indices = [Index(value = ["code"])]
)
data class AircraftType(
    @PrimaryKey val code: String,
    val name: String,
    val manufacturer: String,
    val category: String = "MEL",
    val engineType: String = "Turbo Jet"
)

@Entity(tableName = "user_profile_settings")
data class UserProfileSettings(
    @PrimaryKey val id: Int = 0,
    val fullName: String = "Pilot Pilot",
    val role: String = "Captain",
    val airline: String = "Ethiopian Airlines",
    val experience: String = "5,200 hrs Total Time, B787-8/9 & A350-900 Rated",
    val avatarStyle: String = "Gold Captain",
    val prefAirlinePrefix: String = "",
    val prefTailPrefix: String = "",
    val prefCrewSize: Int = 1,
    val prefPilotRole: String = "PIC",
    val prefFlightRules: String = "IFR",
    val pilotLimitsJson: String = "[]",
    val previousExperienceJson: String = "[]"
) {
    fun getPilotLimits(): List<com.example.HourLimit> {
        if (pilotLimitsJson.isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(pilotLimitsJson)
            val list = mutableListOf<com.example.HourLimit>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.HourLimit(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        days = obj.optInt("days", 0),
                        hours = obj.optDouble("hours", 0.0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPreviousExperiences(): List<PreviousExperience> {
        if (previousExperienceJson.isBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(previousExperienceJson)
            val list = mutableListOf<PreviousExperience>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PreviousExperience(
                        id = obj.optInt("id", 0),
                        aircraftType = obj.optString("aircraftType", ""),
                        pilotRole = obj.optString("pilotRole", ""),
                        totalHours = obj.optDouble("totalHours", 0.0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// Simple non-entity data class for previous experience
data class PreviousExperience(
    val id: Int = 0,
    val aircraftType: String,
    val pilotRole: String,
    val totalHours: Double
)

// --- DAOs ---

@Dao
interface UserProfileSettingsDao {
    @Query("SELECT * FROM user_profile_settings WHERE id = 0 LIMIT 1")
    fun getUserProfileSettings(): Flow<UserProfileSettings?>

    @Query("SELECT * FROM user_profile_settings WHERE id = 0 LIMIT 1")
    suspend fun getUserProfileSettingsValue(): UserProfileSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfileSettings(settings: UserProfileSettings)
}

@Dao
interface AircraftDao {
    @Query("SELECT * FROM aircrafts ORDER BY reg ASC")
    fun getAllAircrafts(): Flow<List<Aircraft>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAircraft(aircraft: Aircraft)

    @Query("DELETE FROM aircrafts WHERE reg = :reg")
    suspend fun deleteAircraft(reg: String)

    @Query("DELETE FROM aircrafts")
    suspend fun deleteAllAircrafts()
}

@Dao
interface AirportDao {
    @Query("SELECT * FROM airports ORDER BY icao ASC")
    fun getAllAirports(): Flow<List<Airport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAirport(airport: Airport)

    @Query("DELETE FROM airports WHERE icao = :icao")
    suspend fun deleteAirport(icao: String)

    @Query("DELETE FROM airports")
    suspend fun deleteAllAirports()
}

@Dao
interface AircraftTypeDao {
    @Query("SELECT * FROM aircraft_types ORDER BY code ASC")
    fun getAllAircraftTypes(): Flow<List<AircraftType>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAircraftType(aircraftType: AircraftType)

    @Query("DELETE FROM aircraft_types WHERE code = :code")
    suspend fun deleteAircraftType(code: String)

    @Query("DELETE FROM aircraft_types")
    suspend fun deleteAllAircraftTypes()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM eblog_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<EbLogNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: EbLogNote)

    @Query("DELETE FROM eblog_notes WHERE id = :id")
    suspend fun deleteNote(id: Int)

    @Query("DELETE FROM eblog_notes")
    suspend fun deleteAllNotes()
}

// --- Database ---

@Database(
    entities = [EbLogNote::class, Airport::class, Aircraft::class, AircraftType::class, UserProfileSettings::class],
    version = 12,
    exportSchema = false
)
abstract class EbLogDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun airportDao(): AirportDao
    abstract fun aircraftDao(): AircraftDao
    abstract fun aircraftTypeDao(): AircraftTypeDao
    abstract fun userProfileSettingsDao(): UserProfileSettingsDao
}
