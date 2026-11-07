package com.metcode

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * App language ("en" or "fa") kept in SharedPreferences. All screen text and all dropdown items come from
 * the two plain text files in app/src/main/assets: lang_en.txt and lang_fa.txt.
 * Persian also switches the layout to right-to-left.
 */
object Lang {
    private const val PREFS = "metcal_prefs"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isChosen(c: Context) = prefs(c).contains("lang")
    fun get(c: Context): String = prefs(c).getString("lang", "en") ?: "en"
    fun isFa(c: Context) = get(c) == "fa"
    fun set(c: Context, code: String) { prefs(c).edit().putString("lang", code).commit() }

    fun wrap(base: Context): Context {
        val loc = Locale.forLanguageTag(get(base))
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return base.createConfigurationContext(cfg)
    }

    // ------------------------------------------------------------ text files

    private class Pack(val ui: Map<String, String>, val tables: Map<String, List<Pair<String, String>>>)

    private val cache = HashMap<String, Pack>()

    private fun pack(c: Context, code: String): Pack = cache.getOrPut(code) {
        val ui = LinkedHashMap<String, String>()
        val tables = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        try {
            c.assets.open("lang_$code.txt").bufferedReader(Charsets.UTF_8).use { r ->
                var section = ""
                var lastKey: String? = null
                var blanks = 0
                val keyLine = Regex("^[A-Za-z0-9_]+\\s*=")
                for (raw in r.lineSequence()) {
                    val l = raw.trimStart('\uFEFF').trim()
                    if (l.isEmpty()) { blanks++; continue }
                    if (l.startsWith("#")) continue
                    if (l.startsWith("[") && l.endsWith("]")) { section = l.substring(1, l.length - 1).trim(); lastKey = null; blanks = 0; continue }
                    if (section == "ui") {
                        if (keyLine.containsMatchIn(l)) {
                            val i = l.indexOf('=')
                            val k = l.substring(0, i).trim()
                            lastKey = k
                            ui[k] = l.substring(i + 1).trim().replace("\\n", "\n")
                        } else if (lastKey != null) {
                            // the text continues on the next line(s) of the file (new paragraphs, e.g. in About)
                            ui[lastKey] = ui[lastKey] + "\n".repeat(blanks + 1) + l.replace("\\n", "\n")
                        }
                    } else {
                        val i = l.indexOf('|')
                        if (i > 0) tables.getOrPut(section) { mutableListOf() }.add(l.substring(0, i).trim() to l.substring(i + 1).trim())
                    }
                    blanks = 0
                }
            }
        } catch (_: Exception) {}
        Pack(ui, tables)
    }

    /** Screen text for a key, in the app language (English if the key is missing in the language file). */
    fun t(c: Context, key: String): String = pack(c, get(c)).ui[key] ?: pack(c, "en").ui[key] ?: key

    fun hasKey(c: Context, key: String): Boolean = pack(c, get(c)).ui.containsKey(key) || pack(c, "en").ui.containsKey(key)

    /** Dropdown items (code, description) of one section. */
    fun table(c: Context, name: String): List<Pair<String, String>> =
        pack(c, get(c)).tables[name] ?: pack(c, "en").tables[name] ?: emptyList()
}

/** Every screen extends this so it opens in the chosen language and direction. */
open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }
}
