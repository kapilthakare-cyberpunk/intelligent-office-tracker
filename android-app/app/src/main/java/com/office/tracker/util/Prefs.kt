package com.office.tracker.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
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

    // Auto-backup
    val GH_TOKEN = stringPreferencesKey("gh_token")
    val GH_OWNER = stringPreferencesKey("gh_owner")
    val GH_REPO = stringPreferencesKey("gh_repo")
    val GH_BRANCH = stringPreferencesKey("gh_branch")
    val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
    val BACKUP_LAST = longPreferencesKey("backup_last")

    // Next planned window (self-heal telemetry)
    val NEXT_PLAN_START = longPreferencesKey("next_plan_start")
    val NEXT_PLAN_TYPE = stringPreferencesKey("next_plan_type")

    // Work days: a boolean per day-of-week. Default: Mon-Sat work, Sunday off.
    private val WORK_DAY_KEYS = mapOf(
        DayOfWeek.SUNDAY to booleanPreferencesKey("work_sun"),
        DayOfWeek.MONDAY to booleanPreferencesKey("work_mon"),
        DayOfWeek.TUESDAY to booleanPreferencesKey("work_tue"),
        DayOfWeek.WEDNESDAY to booleanPreferencesKey("work_wed"),
        DayOfWeek.THURSDAY to booleanPreferencesKey("work_thu"),
        DayOfWeek.FRIDAY to booleanPreferencesKey("work_fri"),
        DayOfWeek.SATURDAY to booleanPreferencesKey("work_sat")
    )

    private fun defaultWorkDay(dayOfWeek: DayOfWeek): Boolean = dayOfWeek != DayOfWeek.SUNDAY

    /** Calendar.DAY_OF_WEEK (1=Sunday .. 7=Saturday) convenience overload. */
    suspend fun isWorkDay(ctx: Context, calendarDayOfWeek: Int): Boolean =
        isWorkDay(ctx, calendarDayOfWeek.toDayOfWeek())

    suspend fun setWorkDay(ctx: Context, calendarDayOfWeek: Int, work: Boolean) {
        setWorkDay(ctx, calendarDayOfWeek.toDayOfWeek(), work)
    }

    private fun Int.toDayOfWeek(): DayOfWeek = when (this) {
        1 -> DayOfWeek.SUNDAY
        2 -> DayOfWeek.MONDAY
        3 -> DayOfWeek.TUESDAY
        4 -> DayOfWeek.WEDNESDAY
        5 -> DayOfWeek.THURSDAY
        6 -> DayOfWeek.FRIDAY
        7 -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    suspend fun isWorkDay(ctx: Context, dayOfWeek: DayOfWeek): Boolean {
        val key = WORK_DAY_KEYS[dayOfWeek] ?: return false
        return ctx.dataStore.data.map { it[key] ?: defaultWorkDay(dayOfWeek) }.first()
    }

    suspend fun setWorkDay(ctx: Context, dayOfWeek: DayOfWeek, work: Boolean) {
        val key = WORK_DAY_KEYS[dayOfWeek] ?: return
        ctx.dataStore.edit { it[key] = work }
    }

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

    suspend fun getNextPlanStart(ctx: Context): Long =
        ctx.dataStore.data.map { it[NEXT_PLAN_START] ?: 0L }.first()

    suspend fun getNextPlanType(ctx: Context): String =
        ctx.dataStore.data.map { it[NEXT_PLAN_TYPE] ?: "" }.first()

    suspend fun setNextPlan(ctx: Context, startMillis: Long, typeWire: String) {
        ctx.dataStore.edit {
            it[NEXT_PLAN_START] = startMillis
            it[NEXT_PLAN_TYPE] = typeWire
        }
    }

    suspend fun getBackupEnabled(ctx: Context): Boolean =
        ctx.dataStore.data.map { it[BACKUP_ENABLED] ?: false }.first()

    suspend fun setBackupEnabled(ctx: Context, enabled: Boolean) {
        ctx.dataStore.edit { it[BACKUP_ENABLED] = enabled }
    }

    suspend fun getBackupLast(ctx: Context): Long =
        ctx.dataStore.data.map { it[BACKUP_LAST] ?: 0L }.first()

    suspend fun setBackupLast(ctx: Context, millis: Long) {
        ctx.dataStore.edit { it[BACKUP_LAST] = millis }
    }

    suspend fun getGitHubToken(ctx: Context): String =
        ctx.dataStore.data.map { it[GH_TOKEN] ?: "" }.first()

    suspend fun setGitHubToken(ctx: Context, token: String) {
        ctx.dataStore.edit { it[GH_TOKEN] = token }
    }

    suspend fun getGitHubOwner(ctx: Context): String =
        ctx.dataStore.data.map { it[GH_OWNER] ?: "" }.first()

    suspend fun setGitHubOwner(ctx: Context, owner: String) {
        ctx.dataStore.edit { it[GH_OWNER] = owner }
    }

    suspend fun getGitHubRepo(ctx: Context): String =
        ctx.dataStore.data.map { it[GH_REPO] ?: "" }.first()

    suspend fun setGitHubRepo(ctx: Context, repo: String) {
        ctx.dataStore.edit { it[GH_REPO] = repo }
    }

    suspend fun getGitHubBranch(ctx: Context): String =
        ctx.dataStore.data.map { it[GH_BRANCH] ?: "master" }.first()

    suspend fun setGitHubBranch(ctx: Context, branch: String) {
        ctx.dataStore.edit { it[GH_BRANCH] = branch }
    }

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
