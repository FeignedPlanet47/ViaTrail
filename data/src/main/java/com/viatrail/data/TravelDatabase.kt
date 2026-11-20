package com.viatrail.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class TripEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val start: String, val destination: String, val stopsJson: String, val createdAt: Long)
@Dao interface TripsDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC") fun all(): Flow<List<TripEntity>>
    @Insert suspend fun insert(trip: TripEntity)
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
}
@Database(entities = [TripEntity::class], version = 1, exportSchema = false)
abstract class TravelDatabase : RoomDatabase() { abstract fun tripsDao(): TripsDao }
