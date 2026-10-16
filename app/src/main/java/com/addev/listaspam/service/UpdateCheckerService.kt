package com.addev.listaspam.service

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.addev.listaspam.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.core.net.toUri

/**
 * Class responsible for checking if a newer version of the app is available on a GitHub repository.
 *
 * @property context The application context, used for accessing package information and displaying dialogs.
 * @property githubUser The GitHub username or organization name.
 * @property githubRepo The name of the GitHub repository.
 */
class UpdateChecker(
    private val context: Context,
    private val githubUser: String,
    private val githubRepo: String,
) {

    private val client = OkHttpClient()

    private fun getCurrentVersion(): String {
        val manager = context.packageManager
        val info = manager.getPackageInfo(context.packageName, 0)
        return info.versionName ?: "unknown"
    }

    fun checkForUpdateSync() {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$githubUser/$githubRepo/releases/latest").build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return
            }

            val responseBody = response.body?.string()
            val json = JSONObject(responseBody ?: "")
            val latestTag = json.getString("tag_name")
            val assetsArray = json.getJSONArray("assets")
            val firstAsset = assetsArray.getJSONObject(0)
            val downloadUrl = firstAsset.getString("browser_download_url")

            val currentVersion = getCurrentVersion()

            if (isUpdateAvailable(currentVersion, latestTag)) {
                showAlert(context.getString(R.string.update_available_title),
                    context.getString(R.string.update_available_message, latestTag),
                    positiveAction = {
                        redirectToGitHubReleasePage(context, downloadUrl)
                    })

            }

        } catch (_: Exception) {
        }
    }

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        val currentParts = versionParts(current)
        val latestParts = versionParts(latest)
        if (currentParts.isEmpty() || latestParts.isEmpty()) {
            return current.trimStart('v', 'V') != latest.trimStart('v', 'V')
        }
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trimStart('v', 'V').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: return emptyList() }

    private fun showAlert(title: String, message: String, positiveAction: (() -> Unit)? = null) {
        Handler(Looper.getMainLooper()).post {
            // The check runs in the background: the activity may be gone by now
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return@post
            AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.aceptar) { _, _ -> positiveAction?.invoke() }
                .setNegativeButton(R.string.cancelar, null).show()
        }
    }

    companion object {
        fun redirectToGitHubReleasePage(context: Context, url: String) {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
