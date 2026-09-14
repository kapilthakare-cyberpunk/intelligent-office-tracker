package com.office.tracker.backup

import android.content.Context
import android.util.Log
import com.office.tracker.db.OfficeVisit
import com.office.tracker.util.Prefs
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Base64

/**
 * Exports the visit history to a human-readable JSON (same shape Seeder
 * imports: a future-proof, private backup) and optionally pushes it to a
 * GitHub repo via the contents API using a user-supplied token.
 *
 * Local copy always lands in Download/OfficeTracker/backups/.
 */
object BackupManager {

    private const val TAG = "BackupManager"

    fun backupDir(context: Context): File =
        File(context.getExternalFilesDir(null)?.parentFile, "backups").also { it.mkdirs() }

    suspend fun exportJson(): JSONObject {
        val dao = OfficeApp.instance.database.officeVisitDao()
        val visits = dao.getAllVisits().first()
        val arr = JSONArray()
        for (v in visits) {
            arr.put(
                JSONObject()
                    .put("date", v.date)
                    .put("arrivalTime", v.arrivalTime ?: JSONObject.NULL)
                    .put("departureTime", v.departureTime ?: JSONObject.NULL)
                    .put("arrivalTimestamp", v.arrivalTimestamp)
                    .put("departureTimestamp", v.departureTimestamp)
                    .put("isCurrentlyAtOffice", v.isCurrentlyAtOffice)
            )
        }
        return JSONObject().put("visits", arr)
    }

    fun saveLocal(context: Context, json: JSONObject): File {
        val today = LocalDate.now().toString()
        val file = File(backupDir(context), "office-backup-$today.json")
        file.writeText(json.toString(2))
        return file
    }

    /** Returns a user-facing status message. */
    suspend fun backupNow(context: Context): String {
        val json = exportJson()
        val saved = saveLocal(context, json)
        Log.d(TAG, "Saved local backup: ${saved.absolutePath}")

        val token = Prefs.getGitHubToken(context)
        val owner = Prefs.getGitHubOwner(context)
        val repo = Prefs.getGitHubRepo(context)
        if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
            return "Saved locally. Add GitHub credentials in Settings for remote backup."
        }
        val branch = Prefs.getGitHubBranch(context)
        return try {
            val message = uploadToGitHub(owner, repo, token, json, branch)
            Prefs.setBackupLast(context, System.currentTimeMillis())
            "Saved locally + GitHub: $message"
        } catch (e: Exception) {
            Log.e(TAG, "GitHub upload failed", e)
            "Saved locally; GitHub upload failed: ${e.message}"
        }
    }

    private suspend fun uploadToGitHub(owner: String, repo: String, token: String, json: JSONObject, branch: String): String {
        val date = LocalDate.now().toString()
        val path = "backups/office-backup-$date.json"
        val content = Base64.getEncoder().encodeToString(json.toString(2).toByteArray())

        // Try create; if it already exists (retry), fetch its sha and update it.
        var sha: String? = null
        val create = putContents(
            owner, repo, token, path,
            body = JSONObject()
                .put("message", "Office Tracker auto-backup $date")
                .put("content", content)
                .put("branch", branch)
        )
        if (create.first == 422) {
            sha = getSha(owner, repo, token, path)
        }
        if (sha != null) {
            val update = putContents(
                owner, repo, token, path,
                body = JSONObject()
                    .put("message", "Office Tracker auto-backup $date (update)")
                    .put("content", content)
                    .put("sha", sha)
                    .put("branch", branch)
            )
            if (update.first !in 200..299) throw RuntimeException("GitHub update failed: ${update.first}")
        }
        return "backups/$path"
    }

    private data class Resp(val code: Int, val body: String)

    private fun putContents(owner: String, repo: String, token: String, path: String, body: JSONObject): Resp {
        val url = URL("https://api.github.com/repos/$owner/$repo/contents/$path")
        return http("PUT", url, token, body, null)
    }

    private fun getSha(owner: String, repo: String, token: String, path: String): String? {
        val url = URL("https://api.github.com/repos/$owner/$repo/contents/$path")
        val resp = http("GET", url, token, null, null)
        if (resp.code != 200) return null
        return JSONObject(resp.body).optString("sha", null)
    }

    private fun http(method: String, url: URL, token: String, body: JSONObject?, shaFile: File?): Resp {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "office-tracker")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        }
        val code = conn.responseCode
        val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
        val ok = conn.inputStream?.bufferedReader()?.use { it.readText() }
        conn.disconnect()
        return Resp(code, ok ?: err ?: "")
    }
}
