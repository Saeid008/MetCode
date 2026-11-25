package com.metcode

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * First launch only: choose the app language. The phone language is preselected (like Telegram), tapping the other
 * language changes the page text and direction at once, and Continue saves the choice and opens the opening screen.
 */
class LanguageActivity : BaseActivity() {

    private var selected = "en"
    private var renderer: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Lang.isChosen(this)) { openNext(); return }
        selected = if (phoneLanguage() == "fa") "fa" else "en"

        val d = resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        val blue = Color.parseColor("#1565C0")

        val root = FrameLayout(this)
        val bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bg.setImageDrawable(SplashPhoto.background(this))
        root.addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // darker at the top and bottom so the text stays readable over any photo
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xB3000000.toInt(), 0x66000000, 0xB3000000.toInt()))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(32)) }

        val title = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
        }
        col.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        val rule = View(this).apply { background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(Color.parseColor("#FFC107")) } }
        col.addView(rule, LinearLayout.LayoutParams(dp(48), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(36) })

        // one language card: ring or filled check on the start side, the language name next to it
        val cards = HashMap<String, Triple<LinearLayout, TextView, TextView>>()
        fun option(label: String, code: String) {
            val tick = TextView(this).apply { text = "\u2713"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
            val name = TextView(this).apply { text = label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f); setTextColor(Color.WHITE) }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(18))
                setOnClickListener { selected = code; renderer() }
            }
            row.addView(tick, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(16) })
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            cards[code] = Triple(row, tick, name)
            col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })
        }
        option("English", "en")
        option("\u0641\u0627\u0631\u0633\u06CC", "fa")

        col.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // Continue: blue gradient pill with a ripple
        val go = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
            val pill = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(blue, Color.parseColor("#42A5F5"))).apply { cornerRadius = dp(30).toFloat() }
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), pill, null)
            elevation = dp(6).toFloat()
            isClickable = true
            setOnClickListener { Lang.set(this@LanguageActivity, selected); openNext() }
        }
        col.addView(go, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        val ver = TextView(this).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(Color.parseColor("#CCFFFFFF")); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) }
        col.addView(ver)
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        // everything that depends on the selection
        renderer = {
            val fa = selected == "fa"
            title.text = if (fa) "\u0632\u0628\u0627\u0646 \u062E\u0648\u062F \u0631\u0627 \u0627\u0646\u062A\u062E\u0627\u0628 \u06A9\u0646\u06CC\u062F" else "Choose your language"
            go.text = if (fa) "\u0627\u062F\u0627\u0645\u0647" else "Continue"
            ver.text = String.format(java.util.Locale.US, if (fa) "\u0646\u0633\u062E\u0647 %s" else "Version %s", version)
            root.layoutDirection = if (fa) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            for ((code, c) in cards) {
                val on = code == selected
                c.first.background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(Color.parseColor(if (on) "#40FFFFFF" else "#1FFFFFFF"))
                    setStroke(dp(2), if (on) Color.WHITE else Color.parseColor("#33FFFFFF"))
                }
                c.second.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (on) setColor(Color.WHITE) else { setColor(Color.TRANSPARENT); setStroke(dp(2), Color.parseColor("#99FFFFFF")) }
                }
                c.second.setTextColor(if (on) blue else Color.TRANSPARENT)
            }
        }
        renderer()
    }

    /** The phone's own language (not the app language, which is not chosen yet). */
    private fun phoneLanguage(): String {
        val cfg = Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= 24) cfg.locales[0].language else @Suppress("DEPRECATION") cfg.locale.language
    }

    private fun openNext() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }
}
