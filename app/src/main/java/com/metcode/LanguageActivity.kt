package com.metcode

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Asks for the app language once (first launch only): the two choices sit at the bottom, over the splash photo. */
class LanguageActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Lang.isChosen(this)) { openNext(); return }

        val d = resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        val root = FrameLayout(this)
        val bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bg.setImageDrawable(SplashPhoto.background(this))
        root.addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(View(this).apply { setBackgroundColor(Color.parseColor("#66000000")) },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        fun choice(label: String, code: String) = Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this@LanguageActivity, "size_language_button", 22f))
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) }
            setOnClickListener { Lang.set(this@LanguageActivity, code); openNext() }
        }
        col.addView(choice("English", "en"))
        col.addView(choice("\u0641\u0627\u0631\u0633\u06CC", "fa"))
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
            .apply { setMargins(dp(40), 0, dp(40), dp(64)) })
        setContentView(root)
    }

    private fun openNext() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }
}
