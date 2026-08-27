package com.office.tracker.db

import android.content.Context
import android.util.Log
import com.office.tracker.OfficeApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Imports historical office visit data from a JSON seed file.
 *
 * The seed file is read from /sdcard/OfficeTracker/seed.json
 * (pushed via adb). Format:
 *
 * {
 *   "visits": [
 *     {
 *       "date": "2026-08-26",
 *       "arrivalTime": "10:18 am",
 *       "arrivalTimestamp": 1787719680000,
 *       "departureTime": "7:48 pm",
 *       "departureTimestamp": 1787753880000,
 *       "isCurrentlyAtOffice": false
 *     }
 *   ]
 * }
 *
 * Only fields present in the JSON are written; a missing value keeps the
 * existing DB row intact (i.e. it won't clear data the app logged itself).
 */
object Seeder {

    private const val TAG = "OfficeSeeder"

    fun seedFile(context: Context): File =
        File(context.getExternalFilesDir(null), "seed.json")

    /**
     * Semantically merges the json into the DB.
     * Returns a count of rows touched.
     */
    suspend fun importFromJson(context: Context, text: String): Int {
        val json = JSONObject(text)
        val visits = json.optJSONArray("visits") ?: JSONArray()
        val dao = OfficeApp.instance.database.officeVisitDao()

        var count = 0
        for (i in 0 until visits.length()) {
            val v = visits.getJSONObject(i)
            val date = v.getString("date")
            val existing = dao.getVisitForDate(date)

            val arrivalTime = v.optString("arrivalTime", existing?.arrivalTime ?: "").ifEmpty { null }
            val departureTime = v.optString("departureTime", existing?.departureTime ?: "").ifEmpty { null }
            val arrivalTs = if (v.has("arrivalTimestamp")) v.getLong("arrivalTimestamp") else existing?.arrivalTimestamp ?: 0L
            val departureTs = if (v.has("departureTimestamp")) v.getLong("departureTimestamp") else existing?.departureTimestamp ?: 0L
            val current = v.optBoolean("isCurrentlyAtOffice", existing?.isCurrentlyAtOffice ?: false)

            dao.upsert(
                OfficeVisit(
                    date = date,
                    arrivalTime = arrivalTime,
                    departureTime = departureTime,
                    isCurrentlyAtOffice = current,
                    arrivalTimestamp = arrivalTs,
                    departureTimestamp = departureTs
                )
            )
            count++
        }
        return count
    }

    suspend fun importFromSeedFile(context: Context): Int {
        val file = seedFile(context)
        if (!file.exists()) {
            Log.w(TAG, "No seed file at ${file.absolutePath}")
            return 0
        }
        val text = file.readText()
        val count = importFromJson(context, text)
        Log.d(TAG, "Imported $count visits")
        return count
    }
}
