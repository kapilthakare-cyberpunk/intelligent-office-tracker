package com.office.tracker.db

import kotlinx.coroutines.flow.Flow

/**
 * Thin data layer used by the service, receivers and UI.
 * Centralizes "record an arrival / departure / re-arrival" so the exact DAO
 * update semantics live in one place instead of being re-implemented in the
 * service and the manual-edit dialog.
 */
class VisitRepository(private val dao: OfficeVisitDao) {

    fun allVisits(): Flow<List<OfficeVisit>> = dao.getAllVisits()

    fun visitForDate(date: String): Flow<OfficeVisit?> = dao.getVisitForDateFlow(date)

    suspend fun visitForDateOnce(date: String): OfficeVisit? = dao.getVisitForDate(date)

    suspend fun recordArrival(date: String, timeHHmm: String, millis: Long) {
        val existing = dao.getVisitForDate(date)
        if (existing == null) {
            dao.upsert(
                OfficeVisit(
                    date = date,
                    arrivalTime = timeHHmm,
                    arrivalTimestamp = millis,
                    isCurrentlyAtOffice = true
                )
            )
        } else if (existing.arrivalTime == null) {
            dao.setArrival(date, timeHHmm, millis)
        }
    }

    suspend fun recordDeparture(date: String, timeHHmm: String, millis: Long) {
        dao.setDeparture(date, timeHHmm, millis)
    }

    suspend fun recordReArrival(date: String, timeHHmm: String, millis: Long) {
        dao.setArrival(date, timeHHmm, millis)
    }

    suspend fun upsert(visit: OfficeVisit) = dao.upsert(visit)
}
