package com.metcode

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** "Update App": finds the newest GitHub Release (assets/app_config.txt: update_repo), downloads its .apk and opens the installer. */
object Updater {

    fun check(a: Activity, td: (String, String, String) -> String) {
        val repo = TextConfig.read(a, "app_config.txt")["update_repo"].orEmpty().trim()
        if (repo.isEmpty()) { toast(a, td("upd_no_repo", "The update link is not set yet.", "پیوند به‌روزرسانی هنوز تنظیم نشده است.")); return }
        toast(a, td("upd_checking", "Checking for updates...", "در حال بررسی به‌روزرسانی..."))
        Thread {
            try {
                val json = JSONObject(httpGet("https://api.github.com/repos/$repo/releases/latest"))
                val tag = json.getString("tag_name")
                val assets = json.getJSONArray("assets")
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val o = assets.getJSONObject(i)
                    if (o.getString("name").endsWith(".apk", true)) { url = o.getString("browser_download_url"); break }
                }
                val current = a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "0"
                a.runOnUiThread {
                    if (url == null || !newer(tag, current)) {
                        toast(a, td("upd_latest", "You already have the latest version.", "نسخهٔ شما به‌روز است."))
                    } else {
                        AlertDialog.Builder(a)
                            .setMessage(String.format(td("upd_available", "Version %s is available. Download and install it?", "نسخهٔ %s موجود است. دانلود و نصب شود؟"), tag))
                            .setPositiveButton(td("upd_yes", "Update", "به‌روزرسانی")) { _, _ -> download(a, url, td) }
                            .setNegativeButton(Lang.t(a, "close"), null)
                            .show()
                    }
                }
            } catch (_: Exception) {
                a.runOnUiThread { toast(a, td("upd_failed", "Update failed. Check the internet connection.", "به‌روزرسانی ناموفق بود. اتصال اینترنت را بررسی کنید.")) }
            }
        }.start()
    }

    private fun download(a: Activity, url: String, td: (String, String, String) -> String) {
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            toast(a, td("upd_allow", "Allow this app to install updates, then tap Update again.", "اجازهٔ نصب برنامه‌ها را به این برنامه بدهید و دوباره به‌روزرسانی را بزنید."))
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            return
        }
        toast(a, td("upd_downloading", "Downloading the update...", "در حال دانلود به‌روزرسانی..."))
        Thread {
            try {
                val dir = a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                val file = File(dir, "update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                conn.inputStream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
                a.runOnUiThread {
                    val uri = FileProvider.getUriForFile(a, a.packageName + ".fileprovider", file)
                    a.startActivity(Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (_: Exception) {
                a.runOnUiThread { toast(a, td("upd_failed", "Update failed. Check the internet connection.", "به‌روزرسانی ناموفق بود. اتصال اینترنت را بررسی کنید.")) }
            }
        }.start()
    }

    private fun httpGet(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** "v2.1" is newer than "2.0": compares the numbers in order. */
    private fun newer(tag: String, current: String): Boolean {
        fun nums(s: String) = Regex("\\d+").findAll(s).map { it.value.toInt() }.toList()
        val t = nums(tag); val c = nums(current)
        for (i in 0 until maxOf(t.size, c.size)) {
            val x = t.getOrElse(i) { 0 }; val y = c.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun toast(a: Activity, t: String) = Toast.makeText(a, t, Toast.LENGTH_LONG).show()
}
