package com.metcode

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
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
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * "Update App": finds the newest GitHub Release (assets/app_config.txt: update_repo), shows its notes, and after the
 * user confirms downloads the .apk, checks it, and opens the installer.
 *
 * Checks before installing:
 *  - if the release has a file ending in .sha256 (for example app-release.apk.sha256), the download must match it;
 *  - the file must be this app (same package name) and have a higher versionCode than the installed one.
 *
 * When the GitHub API refuses (rate limit), the newest tag is read from the releases/latest web link and the file
 * is expected to be named app-release.apk.
 */
object Updater {

    private class Release(val tag: String, val apkUrl: String?, val shaUrl: String?, val notes: String)

    fun check(a: Activity, td: (String, String, String) -> String) {
        val repo = TextConfig.read(a, "app_config.txt")["update_repo"].orEmpty().trim()
        if (repo.isEmpty()) { toast(a, td("upd_no_repo", "The update link is not set yet.", "پیوند به‌روزرسانی هنوز تنظیم نشده است.")); return }
        toast(a, td("upd_checking", "Checking for updates...", "در حال بررسی به‌روزرسانی..."))
        Thread {
            try {
                val rel = latestRelease(repo)
                val current = a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "0"
                a.runOnUiThread {
                    if (rel.apkUrl == null || !newer(rel.tag, current)) {
                        toast(a, td("upd_latest", "You already have the latest version.", "نسخهٔ شما به‌روز است."))
                    } else {
                        var msg = String.format(td("upd_available", "Version %s is available. Download and install it?", "نسخهٔ %s موجود است. دانلود و نصب شود؟"), rel.tag)
                        if (rel.notes.isNotBlank()) msg += "\n\n" + rel.notes.trim().take(600)
                        AlertDialog.Builder(a)
                            .setMessage(msg)
                            .setPositiveButton(td("upd_yes", "Update", "به‌روزرسانی")) { _, _ -> download(a, rel, td) }
                            .setNegativeButton(Lang.t(a, "close"), null)
                            .show()
                    }
                }
            } catch (_: Exception) {
                a.runOnUiThread { toast(a, td("upd_failed", "Update failed. Check the internet connection.", "به‌روزرسانی ناموفق بود. اتصال اینترنت را بررسی کنید.")) }
            }
        }.start()
    }

    // ---------------------------------------------------------------- finding the newest release

    private fun latestRelease(repo: String): Release {
        try { return fromApi(repo) } catch (_: Exception) { /* rate limit or no network: try the web link */ }
        return fromRedirect(repo)
    }

    private fun fromApi(repo: String): Release {
        val json = JSONObject(httpGet("https://api.github.com/repos/$repo/releases/latest"))
        val assets = json.getJSONArray("assets")
        var apk: String? = null
        var sha: String? = null
        for (i in 0 until assets.length()) {
            val o = assets.getJSONObject(i)
            val name = o.getString("name")
            if (apk == null && name.endsWith(".apk", true)) apk = o.getString("browser_download_url")
            if (sha == null && name.endsWith(".sha256", true)) sha = o.getString("browser_download_url")
        }
        return Release(json.getString("tag_name"), apk, sha, json.optString("body", ""))
    }

    private fun fromRedirect(repo: String): Release {
        val c = URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15000; c.readTimeout = 15000
        val loc = c.getHeaderField("Location") ?: throw Exception("no release")
        c.disconnect()
        val tag = loc.substringAfter("/releases/tag/", "").substringBefore('?').trim('/')
        if (tag.isEmpty()) throw Exception("no release")
        val apk = "https://github.com/$repo/releases/download/$tag/app-release.apk"
        return Release(tag, apk, "$apk.sha256", "")
    }

    // ---------------------------------------------------------------- download and install

    private fun download(a: Activity, rel: Release, td: (String, String, String) -> String) {
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
                val md = MessageDigest.getInstance("SHA-256")
                val conn = URL(rel.apkUrl!!).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                DigestInputStream(conn.inputStream, md).use { input -> file.outputStream().use { out -> input.copyTo(out) } }
                val actual = md.digest().joinToString("") { "%02x".format(it) }

                val expected = rel.shaUrl?.let { fetchSha(it) }
                if (expected != null && !expected.equals(actual, true)) {
                    file.delete()
                    a.runOnUiThread { toast(a, td("upd_bad_checksum", "The downloaded file does not match its checksum. Update cancelled.", "فایل دانلودشده با مقدار کنترلی (SHA-256) مطابقت ندارد. به‌روزرسانی لغو شد.")) }
                    return@Thread
                }
                val info = a.packageManager.getPackageArchiveInfo(file.path, 0)
                if (info == null || info.packageName != a.packageName) {
                    file.delete()
                    a.runOnUiThread { toast(a, td("upd_bad_file", "The downloaded file is not a valid update for this app.", "فایل دانلودشده به‌روزرسانی معتبری برای این برنامه نیست.")) }
                    return@Thread
                }
                if (codeOf(info) <= codeOf(a.packageManager.getPackageInfo(a.packageName, 0))) {
                    file.delete()
                    a.runOnUiThread { toast(a, td("upd_same_code", "This release has the same or an older version code, so Android cannot install it.", "این نسخه شمارهٔ ساخت برابر یا قدیمی‌تر دارد و اندروید نمی‌تواند آن را نصب کند.")) }
                    return@Thread
                }
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

    /** The 64-character SHA-256 from a .sha256 file, or null when the release has none. */
    private fun fetchSha(u: String): String? = try {
        Regex("[0-9a-fA-F]{64}").find(httpGet(u))?.value
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun codeOf(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

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