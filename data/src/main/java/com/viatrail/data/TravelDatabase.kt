package com.viatrail.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: String, val destination: String,
    val stopsJson: String, val createdAt: Long,
    val routeJson: String = "", val settingsJson: String = "", val visitedJson: String = "[]"
)
@Dao interface TripsDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC") fun all(): Flow<List<TripEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(trip: TripEntity): Long
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
}
@Database(entities = [TripEntity::class], version = 3, exportSchema = false)
abstract class TravelDatabase : RoomDatabase() { abstract fun tripsDao(): TripsDao }
