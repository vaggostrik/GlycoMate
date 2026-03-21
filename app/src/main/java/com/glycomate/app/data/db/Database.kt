package com.glycomate.app.data.db

import androidx.room.*
import com.glycomate.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Query("SELECT * FROM glucose_readings ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<GlucoseReading>>

    @Query("SELECT * FROM glucose_readings ORDER BY timestampMs DESC LIMIT 1")
    fun observeLatest(): Flow<GlucoseReading?>

    @Query("SELECT * FROM glucose_readings WHERE timestampMs >= :fromMs ORDER BY timestampMs ASC")
    suspend fun getFrom(fromMs: Long): List<GlucoseReading>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: GlucoseReading): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(readings: List<GlucoseReading>)

    @Delete
    suspend fun delete(r: GlucoseReading)

    /** Remove any readings whose timestamp is strictly after [maxMs].
     *  Used to purge bogus System.currentTimeMillis() fallback entries. */
    @Query("DELETE FROM glucose_readings WHERE timestampMs > :maxMs")
    suspend fun deleteNewerThan(maxMs: Long)
}

@Dao
interface InsulinDao {
    @Query("SELECT * FROM insulin_entries ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<InsulinEntry>>

    @Query("SELECT * FROM insulin_entries WHERE timestampMs >= :fromMs ORDER BY timestampMs DESC")
    suspend fun getFrom(fromMs: Long): List<InsulinEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: InsulinEntry): Long

    @Delete
    suspend fun delete(e: InsulinEntry)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_entries ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<MealEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: MealEntry): Long

    @Delete
    suspend fun delete(e: MealEntry)
}

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entries ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries WHERE timestampMs >= :fromMs ORDER BY timestampMs DESC")
    suspend fun getFrom(fromMs: Long): List<MoodEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: MoodEntry): Long

    @Delete
    suspend fun delete(e: MoodEntry)
}

@Database(
    entities  = [GlucoseReading::class, InsulinEntry::class, MealEntry::class,
                 MoodEntry::class],
    version   = 2,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class GlycoDatabase : RoomDatabase() {
    abstract fun glucoseDao(): GlucoseDao
    abstract fun insulinDao(): InsulinDao
    abstract fun mealDao():    MealDao
    abstract fun moodDao():    MoodDao

    companion object {
        @Volatile private var INSTANCE: GlycoDatabase? = null
        fun getInstance(context: android.content.Context): GlycoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    GlycoDatabase::class.java,
                    "glycomate.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

class DbConverters {
    @TypeConverter fun trendToString(v: GlucoseTrend): String  = v.name
    @TypeConverter fun stringToTrend(v: String): GlucoseTrend  = GlucoseTrend.valueOf(v)
    @TypeConverter fun sourceToString(v: DataSource): String   = v.name
    @TypeConverter fun stringToSource(v: String): DataSource   = DataSource.valueOf(v)
    @TypeConverter fun insulinToString(v: InsulinType): String = v.name
    @TypeConverter fun stringToInsulin(v: String): InsulinType = InsulinType.valueOf(v)
    @TypeConverter fun moodToString(v: MoodLevel): String      = v.name
    @TypeConverter fun stringToMood(v: String): MoodLevel      = MoodLevel.valueOf(v)
    @TypeConverter fun energyToString(v: EnergyLevel): String  = v.name
    @TypeConverter fun stringToEnergy(v: String): EnergyLevel  = EnergyLevel.valueOf(v)
}
