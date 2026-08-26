package com.office.tracker.util

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "office_settings")

object Prefs {
    // Office location
    val OFFICE_LAT = doublePreferencesKey("office_lat")
    val OFFICE_LNG = doublePreferencesKey("office_lng")
    val OFFICE_RADIUS = intPreferencesKey("office_radius") // meters

    // Time windows (hour of day, 0-23)
    val ARRIVAL_WINDOW_START = intPreferencesKey("arrival_window_start")
    val ARRIVAL_WINDOW_END = intPreferencesKey("arrival_window_end")
    val DEPARTURE_WINDOW_START = intPreferencesKey("departure_window_start")
    val DEPARTURE_WINDOW_END = intPreferencesKey("departure_window_end")

    // Check interval in seconds
    val CHECK_INTERVAL = intPreferencesKey("check_interval")

    suspend fun getOfficeLat(ctx: Context): Double =
        ctx.dataStore.data.map { it[OFFICE_LAT] ?: 18.531555 }.first()

    suspend fun getOfficeLng(ctx: Context): Double =
        ctx.dataStore.data.map { it[OFFICE_LNG] ?: 73.842193 }.first()

    suspend fun getOfficeRadius(ctx: Context): Int =
        ctx.dataStore.data.map { it[OFFICE_RADIUS] ?: 100 }.first()

    suspend fun getArrivalWindowStart(ctx: Context): Int =
        ctx.dataStore.data.map { it[ARRIVAL_WINDOW_START] ?: 9 }.first()

    suspend fun getArrivalWindowEnd(ctx: Context): Int =
        ctx.dataStore.data.map { it[ARRIVAL_WINDOW_END] ?: 12 }.first()

    suspend fun getDepartureWindowStart(ctx: Context): Int =
        ctx.dataStore.data.map { it[DEPARTURE_WINDOW_START] ?: 18 }.first()

    suspend fun getDepartureWindowEnd(ctx: Context): Int =
        ctx.dataStore.data.map { it[DEPARTURE_WINDOW_END] ?: 21 }.first()

    suspend fun getCheckInterval(ctx: Context): Int =
        ctx.dataStore.data.map { it[CHECK_INTERVAL] ?: 30 }.first()

    suspend fun setOfficeLocation(ctx: Context, lat: Double, lng: Double) {
        ctx.dataStore.edit {
            it[OFFICE_LAT] = lat
            it[OFFICE_LNG] = lng
        }
    }

    suspend fun setArrivalWindow(ctx: Context, start: Int, end: Int) {
        ctx.dataStore.edit {
            it[ARRIVAL_WINDOW_START] = start
            it[ARRIVAL_WINDOW_END] = end
        }
    }

    suspend fun setDepartureWindow(ctx: Context, start: Int, end: Int) {
        ctx.dataStore.edit {
            it[DEPARTURE_WINDOW_START] = start
            it[DEPARTURE_WINDOW_END] = end
        }
    }
}
