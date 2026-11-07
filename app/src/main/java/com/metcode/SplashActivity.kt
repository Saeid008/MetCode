package com.metcode

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Launcher screen. Shows a photo from app/src/main/assets/splash (the next one each launch) for 3 seconds:
 * icon and app name at the top, "Made by Observer 40827" near the bottom, both in the app language only. Tap to skip.
 * On the very first launch the language page comes first.
 */
class SplashActivity : BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var went = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Lang.isChosen(this)) {
            startActivity(Intent(this, LanguageActivity::class.java))
            finish()
            return
        }
        if (intent.getBooleanExtra("restart", false)) { proceed(); return }   // after a language change: straight to the calculator

        val d = resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        val root = FrameLayout(this)
        val bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bg.setImageDrawable(SplashPhoto.background(this))
        root.addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundColor(Color.parseColor("#66000000")) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        top.addView(ImageView(this).apply { setImageResource(R.drawable.ic_launcher_foreground) }, LinearLayout.LayoutParams(dp(150), dp(150)))
        top.addView(TextView(this).apply {
            text = Lang.t(this@SplashActivity, "app_name")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this@SplashActivity, "size_splash_title", 30f))
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            Style.font(this@SplashActivity, this)
            gravity = Gravity.CENTER
        })
        root.addView(top, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply { topMargin = dp(36) })

        val made = TextView(this).apply {
            text = Lang.t(this@SplashActivity, "splash_text")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this@SplashActivity, "size_splash_text", 20f))
            setTextColor(Color.WHITE)
            Style.font(this@SplashActivity, this)
            gravity = Gravity.CENTER
        }
        val version = TextView(this).apply {
            val v = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
            val f = if (Lang.hasKey(this@SplashActivity, "splash_version")) Lang.t(this@SplashActivity, "splash_version")
                    else if (Lang.isFa(this@SplashActivity)) "\u0646\u0633\u062E\u0647 %s" else "Version %s"
            text = String.format(Locale.US, f, v)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this@SplashActivity, "size_splash_version", 15f))
            setTextColor(Color.WHITE)
            Style.font(this@SplashActivity, this)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(made)
        bottom.addView(version)
        root.addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(48) })

        root.setOnClickListener { proceed() }
        setContentView(root)
        handler.postDelayed({ proceed() }, 3000)
    }

    private fun proceed() {
        if (went) return
        went = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
