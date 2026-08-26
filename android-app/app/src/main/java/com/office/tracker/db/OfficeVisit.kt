package com.office.tracker.db

import androidx.room.*

@Entity(tableName = "office_visits")
data class OfficeVisit(
    @PrimaryKey val date: String,          // "2026-08-26"
    val arrivalTime: String? = null,       // "10:18"
    val departureTime: String? = null,     // "18:51"
    val isCurrentlyAtOffice: Boolean = false,
    val arrivalTimestamp: Long = 0,        // epoch millis for precise time
    val departureTimestamp: Long = 0
)

@Dao
interface OfficeVisitDao {

    @Query("SELECT * FROM office_visits ORDER BY date DESC")
    fun getAllVisits(): kotlinx.coroutines.flow.Flow<List<OfficeVisit>>

    @Query("SELECT * FROM office_visits WHERE date = :date LIMIT 1")
    suspend fun getVisitForDate(date: String): OfficeVisit?

    @Query("SELECT * FROM office_visits WHERE date = :date LIMIT 1")
    fun getVisitForDateFlow(date: String): kotlinx.coroutines.flow.Flow<OfficeVisit?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: OfficeVisit)

    @Query("UPDATE office_visits SET departureTime = :time, departureTimestamp = :ts, isCurrentlyAtOffice = 0 WHERE date = :date")
    suspend fun setDeparture(date: String, time: String, ts: Long)

    @Query("UPDATE office_visits SET arrivalTime = :time, arrivalTimestamp = :ts, isCurrentlyAtOffice = 1 WHERE date = :date")
    suspend fun setArrival(date: String, time: String, ts: Long)
}
