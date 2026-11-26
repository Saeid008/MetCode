@file:Suppress("SetTextI18n")

package com.metcode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.util.TypedValue
import kotlin.system.exitProcess
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.graphics.Rect
import android.text.InputType
import org.json.JSONObject
import androidx.core.content.ContextCompat
import android.widget.ScrollView
import android.widget.LinearLayout
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.text.util.Linkify
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.metcode.databinding.ActivityMainBinding
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.text.iterator

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var codeStore: StationCodeStore
    private lateinit var history: HistoryStore
    private val autoFilled = HashMap<String, String>()

    private var selectedHeight = 0.0
    private var selectedLatDeg = 0.0
    private var selectedLatMin = 0.0
    private var selectedStationName = ""

    /** True while the app itself writes into a field, so that write does not trigger a recalculation. */
    private var isUpdating = false

    private enum class Trigger { BAR, PCOR, QFE, T12, T, RH, WT }

    /** RH typed by the observer = RH is the source and WT is calculated; otherwise WT is the source. */
    private var primaryIsRH = false
    private var visInMeters = false

    private var currentSynop = ""
    private var currentMetar = ""
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshCodes() }

    private val layerValueSpinners get() = listOf(binding.spCloud1Value, binding.spCloud2Value, binding.spCloud3Value, binding.spCloud4Value)
    private val layerTypeSpinners get() = listOf(binding.spCloud1Type, binding.spCloud2Type, binding.spCloud3Type, binding.spCloud4Type)
    private val layerHeightFields get() = listOf(binding.etCloud1Height, binding.etCloud2Height, binding.etCloud3Height, binding.etCloud4Height)

    override fun onCreate(savedInstanceState: Bundle?) {
        Locale.setDefault(Locale.US) // ASCII digits and "." in every number the app writes
        Appearance.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyUiText(binding.root)
        Choices.load(this)
        setupChoices()
        setupMenu()
        setupUi()
        codeStore = StationCodeStore(this)
        history = HistoryStore(this)

        setupStation()
        setupReactiveFields()
        setupTimeAndReportType()
        setupCloudAndN()
        setupVisibilityUnit()
        setupTendency()
        setupSimpleSpinners()
        binding.tvSynopOut.setOnClickListener { copyText("SYNOP", currentSynop) }
        binding.tvMetarOut.setOnClickListener { copyText("METAR", currentMetar) }
        val appPrefs = getSharedPreferences("metcode_prefs", MODE_PRIVATE)
        binding.cbIncludeQnhInch.isChecked = appPrefs.getBoolean("qnh_inch", false)
        binding.cbIncludeQnhInch.setOnCheckedChangeListener { _, on -> appPrefs.edit().putBoolean("qnh_inch", on).apply(); scheduleRefresh() }

        binding.spVisUnit.setSelection(0)   // visibility unit: km
        setSpinnerValue(binding.spIX, "2")   // IX default 2
        setSpinnerValue(binding.spIW, "1")   // IW default 1
        setSpinnerValue(binding.spE, "/")
        setSpinnerValue(binding.spEPrime, "/")
        setSpinnerValue(binding.spA3, "/")
        applyIxLock()
        applyHourSections(null)

        // every field change rebuilds the codes at once
        attachRefreshWatchers(binding.root)
        restoreStation()
        fillPhoneTime()
        refreshCodes()
    }

    // ------------------------------------------------------------------ helpers

    private fun watcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { if (!isUpdating) onChanged() }
    }

    /** Accepts "-3.5", "−3,5", Persian or Arabic digits and stray spaces. */
    private fun parseNumber(raw: String): Double? {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when (c) {
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                '\u066B', '\u066C', '\u060C', ',' -> sb.append('.')
                '\u2212', '\u2013', '\u2014', '\u2011' -> sb.append('-')
                ' ' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString().toDoubleOrNull()
    }

    private fun EditText.num(): Double? = parseNumber(text.toString())
    private fun EditText.int(): Int? = num()?.roundToInt()
    private fun EditText.precip(): Double? { val t = text.toString().trim().uppercase(); return if (t == "TR") 0.01 else parseNumber(t) }
    private fun Double.f1() = if (isNaN() || isInfinite()) "" else "%.1f".format(this).let { if (it == "-0.0") "0.0" else it }
    private fun Double.f2() = if (isNaN() || isInfinite()) "" else "%.2f".format(this).let { if (it == "-0.00") "0.00" else it }
    private fun r1(x: Double) = Math.round(x * 10.0) / 10.0

    /** Writes into a field without triggering the calculation watchers (the code panel still refreshes). */
    private fun setField(field: EditText, value: String) {
        isUpdating = true
        try { field.setText(value) } finally { isUpdating = false }
    }

    private fun setSpinnerValue(spinner: Spinner, value: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) if (adapter.getItem(i).toString() == value) { spinner.setSelection(i); return }
    }

    private fun spinnerText(spinner: Spinner) = spinner.selectedItem?.toString() ?: ""

    private fun boldText(text: String): CharSequence {
        val s = SpannableString(text)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, 0)
        return s
    }

    /** Text from the language file; when the file has no such line, the built-in English or Persian default. */
    private fun Td(key: String, en: String, fa: String) = if (Lang.hasKey(this, key)) T(key) else if (Lang.isFa(this)) fa else en

    /** Closes the app and opens it again, so the new language and its direction apply everywhere. */
    private fun restartApp() {
        val i = Intent(this, SplashActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("restart", true)
        startActivity(i)
        finishAffinity()
        exitProcess(0)
    }

    /** Text from the language file (assets/lang_en.txt or lang_fa.txt). */
    private fun T(key: String) = Lang.t(this, key)

    /** Language-file text with a format, always with ASCII digits. */
    private fun fmt(key: String, vararg args: Any) = String.format(Locale.US, T(key), *args)

    /** Keeps each "5aPPP (label)" together so a group code and its label never split across two lines. */
    private fun keepTogether(t: String) =
        Regex("(\\S+) \\(([^)]*)\\)").replace(t) { r -> (r.groupValues[1] + " (" + r.groupValues[2] + ")").replace(' ', '\u00A0') }

    /** Fills every label, title and hint that carries a key in android:tag, and applies the sizes and font of assets/style.txt. */
    private fun applyUiText(v: View) {
        val tag = v.tag as? String
        if (tag != null && tag.isNotEmpty() && Lang.hasKey(this, tag)) {
            var text = T(tag)
            if (tag.startsWith("g_")) text = keepTogether(text)
            if (v is EditText) v.hint = text else if (v is TextView) v.text = text
        }
        if (v is TextView) {
            when {
                v is EditText -> Style.styleInput(this, v)
                tag != null && tag.startsWith("g_") -> { v.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this, "size_title", 14f)); Style.font(this, v) }
                tag != null && tag.startsWith("l_") -> { v.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this, "size_label", 12f)); Style.font(this, v) }
                tag == "menu" -> { v.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this, "size_menu", 16f)); Style.font(this, v) }
                tag == "app_name" -> Style.font(this, v)
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) applyUiText(v.getChildAt(i))
    }

    // ------------------------------------------------------------ menu, language, about

    private fun setupMenu() {
        val open = { v: View ->
            val popup = PopupMenu(this, v)
            popup.menu.add(0, 5, 0, Td("menu_home", "Home", "خانه"))
            popup.menu.add(0, 6, 1, Td("menu_reports", "Saved reports", "گزارش‌های ذخیره‌شده"))
            popup.menu.add(0, 8, 1, Td("menu_minmax", "MinMx Report", "گزارش MinMx"))
            popup.menu.add(0, 4, 2, Td("menu_station", "Station settings", "تنظیمات ایستگاه"))
            popup.menu.add(0, 7, 3, Td("menu_dark", "Dark mode", "حالت شب"))
            popup.menu.add(0, 1, 4, T("menu_language"))
            popup.menu.add(0, 3, 5, Td("menu_update", "Update App", "به‌روزرسانی برنامه"))
            popup.menu.add(0, 2, 6, T("menu_about"))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showLanguageDialog(); 2 -> showAbout(); 3 -> Updater.check(this) { k, en, fa -> Td(k, en, fa) }
                    4 -> showPageId(2); 5 -> showPageId(0); 6 -> showPageId(1); 8 -> showPageId(3); 7 -> showNightDialog()
                }
                true
            }
            popup.show()
        }
        binding.btnMenu.setOnClickListener { open(it) }
        binding.btnMenuStation.setOnClickListener { open(it) }
        binding.btnMenuReports.setOnClickListener { open(it) }
        binding.btnMenuMinMx.setOnClickListener { open(it) }
    }

    private fun showNightDialog() {
        val modes = arrayOf("off", "on", "system")
        val labels = arrayOf(Td("night_off", "Off", "خاموش"), Td("night_on", "On", "روشن"), Td("night_system", "Use device settings", "استفاده از تنظیمات دستگاه"))
        AlertDialog.Builder(this)
            .setTitle(Td("menu_dark", "Dark mode", "حالت شب"))
            .setSingleChoiceItems(labels, modes.indexOf(Appearance.get(this)).coerceAtLeast(0)) { d, which ->
                d.dismiss()
                Appearance.set(this, modes[which])
                Appearance.apply(this)
            }
            .setNegativeButton(T("close"), null)
            .show()
    }

    private fun showLanguageDialog() {
        val labels = arrayOf("English", "\u0641\u0627\u0631\u0633\u06CC")
        AlertDialog.Builder(this)
            .setTitle(T("menu_language"))
            .setSingleChoiceItems(labels, if (Lang.isFa(this)) 1 else 0) { d, which ->
                d.dismiss()
                val code = if (which == 1) "fa" else "en"
                if (code != Lang.get(this)) { Lang.set(this, code); restartApp() }
            }
            .setNegativeButton(T("close"), null)
            .show()
    }

    private fun showAbout() {
        val tv = TextView(this)
        tv.text = T("about_text")
        tv.textSize = 15f
        Style.font(this, tv)
        val pad = (20 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad / 2, pad, 0)
        Linkify.addLinks(tv, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
        tv.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        AlertDialog.Builder(this)
            .setTitle(T("menu_about"))
            .setView(tv)
            .setPositiveButton(T("close"), null)
            .show()
    }

    // ------------------------------------------- dropdowns with descriptions (app language)

    private fun setupChoices() {
        val w = (320 * resources.displayMetrics.density).toInt()
        fun fill(sp: Spinner, items: List<Choice>, icons: String? = null) { sp.adapter = ChoiceAdapter(this, items, icons); sp.dropDownWidth = w }
        fill(binding.spA3, Choices.a3, "a3")
        fill(binding.spE, Choices.e)
        fill(binding.spEPrime, Choices.ePrime)
        fill(binding.spH, Choices.h)
        fill(binding.spWW, Choices.ww, "ww")
        fill(binding.spIW, Choices.iw)
        fill(binding.spIR, Choices.ir)
        fill(binding.spIX, Choices.ix)
        fill(binding.spW1, Choices.past, "past")
        fill(binding.spW2, Choices.past, "past")
        fun plain(sp: Spinner, items: List<String>) {
            val keep = sp.selectedItemPosition
            sp.adapter = StyledArrayAdapter(this, items)
            if (keep in items.indices) sp.setSelection(keep)
        }
        plain(binding.spN, resources.getStringArray(R.array.n_values).toList())
        binding.spVisUnit.adapter = StyledArrayAdapter(this, resources.getStringArray(R.array.vis_units).toList(), R.layout.spinner_item_compact)
        binding.spVisUnit.setSelection(0)
        for (sp in layerTypeSpinners) plain(sp, resources.getStringArray(R.array.cloud_types).toList())
        fill(binding.spTotalSnow, Choices.totalSnow)
        fill(binding.spFresh, Choices.freshSnow)
    }

    // ------------------------------------------------------------ pages, info cards, required fields, cloud rule

    private fun setupUi() {
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            binding.root.getWindowVisibleDisplayFrame(r)
            val screen = binding.root.rootView.height
            val keyboardOpen = screen - r.bottom > screen * 0.15
            binding.bottomNav.visibility = if (keyboardOpen) View.GONE else View.VISIBLE
        }
        binding.spNh.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { updateCloudDerived(); scheduleRefresh() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        binding.btnRefresh.setOnClickListener { fillPhoneTime() }
        binding.chipStation.setOnClickListener { showPage(false); binding.actvStation.requestFocus() }
        binding.navHome.setOnClickListener { showPage(true) }
        binding.navStation.setOnClickListener { showPage(false) }
        binding.navUpdate.setOnClickListener { Updater.check(this) { k, en, fa -> Td(k, en, fa) } }
        binding.navAbout.setOnClickListener { showAbout() }
        // wind direction boxes: type it or pick 000 ... 360, 099 (variable) or "/" from the list; wind speed up to 0.4 means calm = 000
        val wd = (200 * resources.displayMetrics.density).toInt()
        for (f in listOf(binding.etDd, binding.etMaxWindDd, binding.etMaxGustDd, binding.etMmMaxDd)) {
            f.setAdapter(DirectionAdapter(this)); f.threshold = 100000; f.dropDownWidth = wd; enableArrowDropdown(f)
        }
        fun calm(ff: EditText, dd: AutoCompleteTextView) {
            var auto = false
            ff.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val v = ff.num() ?: return
                    if (v <= 0.4) { if (dd.text.toString() != "000") dd.setText("000"); auto = true }
                    else if (auto && dd.text.toString() == "000") { dd.setText(""); auto = false }
                }
            })
        }
        calm(binding.etFf, binding.etDd); calm(binding.etMaxWindFf, binding.etMaxWindDd)
        calm(binding.etMaxGustFf, binding.etMaxGustDd); calm(binding.etMmMaxFf, binding.etMmMaxDd)
        // T12 and the three earlier pressures have a long hint (for example "Temp 02:00 UTC"): a little smaller text so it fits
        for (f in listOf(binding.etT12, binding.etP1, binding.etP2, binding.etP3)) f.setTextSize(TypedValue.COMPLEX_UNIT_SP, Style.size(this, "size_input", 14f) - 2f)
        setupMinMx()
        binding.btnSaveStation.setOnClickListener { saveStation() }
        binding.navReports.setOnClickListener { showPageId(1) }
        binding.tvNavReports.text = Td("nav_reports", "Reports", "گزارش‌ها")
        binding.btnSaveReport.text = Td("btn_save_report", "Save report", "ذخیره گزارش")
        binding.btnSaveReport.setOnClickListener { saveReport(false) }
        binding.btnCompleteFromLast.text = Td("btn_complete", "Complete report based on last report", "تکمیل گزارش بر اساس آخرین گزارش")
        binding.btnCompleteFromLast.setOnClickListener { completeFromLast() }
        setupReports()
        binding.btnSaveStation.text = Td("btn_save", "Save changes", "ذخیره تغییرات")
        binding.btnCopySynop.setOnClickListener { saveReport(true); copyText("SYNOP", currentSynop) }
        binding.btnCopyMetar.setOnClickListener { saveReport(true); copyText("METAR", currentMetar) }
        binding.tvStationTitle.text = Td("g_station", "Station settings", "تنظیمات ایستگاه")
        binding.tvNavHome.text = Td("nav_home", "Home", "خانه")
        binding.tvNavStation.text = Td("nav_station", "Station", "ایستگاه")
        binding.tvNavUpdate.text = Td("nav_update", "Update", "به‌روزرسانی")
        binding.tvNavAbout.text = T("menu_about")
        binding.btnCopySynop.text = Td("copy_synop", "Copy SYNOP", "کپی SYNOP")
        binding.btnCopyMetar.text = Td("copy_metar", "Copy METAR", "کپی METAR")

        // cloud height: type the metres, or open the list (WMO table 1677) with the arrow; the code is shown under the box
        val w = (320 * resources.displayMetrics.density).toInt()
        val captions = listOf(binding.tvCloud1Code, binding.tvCloud2Code, binding.tvCloud3Code, binding.tvCloud4Code)
        for (i in 0 until 4) {
            val f = layerHeightFields[i]
            f.setAdapter(HeightAdapter(this))
            f.threshold = 100000
            f.dropDownWidth = w
            enableArrowDropdown(f)
            f.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val hm = f.int()
                    captions[i].text = if (hm == null) "" else "hshs " + "%02d".format(CloudTypes.hshsCode(hm))
                }
            })
        }
        showPage(true)
    }

    /** Tapping the arrow at the end of the box opens its list; tapping elsewhere lets you type. */
    private fun enableArrowDropdown(field: AutoCompleteTextView) {
        field.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                val arrow = (v as TextView).compoundDrawablesRelative[2]
                val reach = (arrow?.bounds?.width() ?: 0) + (16 * resources.displayMetrics.density).toInt()
                val rtl = v.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val hit = if (rtl) ev.x <= v.paddingLeft + reach else ev.x >= v.width - v.paddingRight - reach
                if (hit) {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                    field.showDropDown()
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun showPage(home: Boolean) = showPageId(if (home) 0 else 2)

    /** 0 = Home, 1 = Saved reports, 2 = Station settings, 3 = MinMx report. */
    private fun showPageId(id: Int) {
        show(binding.pageHome, id == 0)
        show(binding.pageReports, id == 1)
        show(binding.pageStation, id == 2)
        show(binding.pageMinMx, id == 3)
        show(binding.codePanel, id != 3)
        binding.navHome.isSelected = id == 0
        binding.navReports.isSelected = id == 1
        binding.navStation.isSelected = id == 2
        binding.navMinMx.isSelected = id == 3
        if (id == 1) refreshReportList()
        if (id == 3) updateMinMxPage()
    }

    /** Station values shown on the Home cards and in the header chip. */
    private fun updateInfoCards() {
        fun v(f: EditText) = f.text.toString().trim().ifEmpty { "\u2014" }
        binding.tvElev.text = v(binding.etHeight)
        binding.tvLat.text = v(binding.etLatN)
        binding.tvLon.text = v(binding.etLonE)
        binding.tvIcao.text = v(binding.etMetarCode)
        binding.tvWmo.text = v(binding.etSynopCode)
        binding.tvStationChip.text = if (selectedStationName.isEmpty()) Td("chip_hint", "Select a station", "انتخاب ایستگاه") else selectedStationName
    }

    /** A red border marks a box the codes cannot be built without; it goes away once the box (or its alternative) is filled. */
    private fun updateRequired() {
        fun req(v: View, missing: Boolean) = v.setBackgroundResource(if (missing) R.drawable.bg_field_required else R.drawable.bg_field)
        fun blank(f: EditText) = f.text.isNullOrBlank()
        // station: the name chip on the main page (and the search box on the station page) until a station is chosen
        val noStation = selectedStationName.isEmpty()
        binding.chipStation.setBackgroundResource(if (noStation) R.drawable.bg_chip_required else R.drawable.bg_chip)
        req(binding.actvStation, noStation)
        // pressure: QFE, or the barometer reading with its attached thermometer
        val noQfe = blank(binding.etQFE)
        req(binding.etQFE, noQfe && (blank(binding.etBarPress) || blank(binding.etBarTemp)))
        req(binding.etBarPress, noQfe && blank(binding.etBarPress))
        req(binding.etBarTemp, noQfe && blank(binding.etBarTemp))
        // wind, and the pressure tendency (calculated from the three earlier pressures)
        req(binding.etDd, blank(binding.etDd))
        req(binding.etFf, blank(binding.etFf))
        req(binding.spA3.parent as View, spinnerText(binding.spA3) == "/")
        req(binding.etPpp3, blank(binding.etPpp3))
        // the three earlier pressures (for the pressure tendency) and the rain amount of this hour
        req(binding.etP1, blank(binding.etP1))
        req(binding.etP2, blank(binding.etP2))
        req(binding.etP3, blank(binding.etP3))
        fun shown(f: EditText) = (f.parent as View).visibility == View.VISIBLE
        req(binding.etR1h, shown(binding.etR1h) && blank(binding.etR1h))
        req(binding.etR6h, shown(binding.etR6h) && blank(binding.etR6h))
        // temperature: T12, dry bulb, and wet bulb or humidity
        req(binding.etT12, blank(binding.etT12))
        req(binding.etDry, blank(binding.etDry))
        val noHumidity = blank(binding.etWet) && blank(binding.etRH)
        req(binding.etWet, noHumidity)
        req(binding.etRH, noHumidity)
    }

    // ------------------------------------------------------------ MinMx report (05 and 12 UTC)

    private fun mmHour(): Int? = binding.etHour.int()?.takeIf { it in 0..23 }

    private fun setupMinMx() {
        binding.navMinMx.setOnClickListener { showPageId(3) }
        binding.tvNavMinMx.text = "MinMx"
        binding.tvMinMxTitle.text = Td("mm_title", "MinMx Report", "گزارش MinMx")
        binding.tvMmT05.text = Td("mm_t05", "05 UTC: humidity, minimum temperature and maximum wind", "ساعت 05 UTC: رطوبت، کمینهٔ دما و بیشینهٔ باد")
        binding.tvMmT12.text = Td("mm_t12", "12 UTC: maximum temperature", "ساعت 12 UTC: بیشینهٔ دما")
        binding.tvMmNone.text = Td("mm_none", "The MinMx report is made at 05 UTC and 12 UTC. Change the hour on the Home page.", "گزارش MinMx در ساعت 05 و 12 UTC تهیه می‌شود. ساعت را در صفحه خانه تغییر دهید.")
        binding.tvMmL1.text = Td("mm_maxrh", "Max Humidity (%)", "بیشینهٔ رطوبت (%)")
        binding.tvMmL2.text = Td("mm_minrh", "Min Humidity (%)", "کمینهٔ رطوبت (%)")
        binding.tvMmL3.text = Td("mm_mint", "Min Temp (°C)", "کمینهٔ دما (°C)")
        binding.tvMmL4.text = Td("mm_maxff", "Max Wind Speed (m/s)", "بیشینهٔ سرعت باد (m/s)")
        binding.tvMmL5.text = Td("mm_maxdd", "Max Wind Direction (°)", "جهت بیشینهٔ باد (°)")
        binding.tvMmL6.text = Td("mm_maxt", "Max Temp (°C)", "بیشینهٔ دما (°C)")
        binding.btnMmSave.text = Td("btn_save_report", "Save report", "ذخیره گزارش")
        binding.btnMmCopy.text = Td("mm_copy", "Copy MinMx", "کپی MinMx")
        binding.btnMmSave.setOnClickListener { saveMinMx(false) }
        binding.btnMmCopy.setOnClickListener { saveMinMx(true); copyText("MinMx", minMxCode()) }
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { binding.tvMinMxOut.text = minMxCode() }
        }
        for (f in listOf(binding.etMmMaxRh, binding.etMmMinRh, binding.etMmMinT, binding.etMmMaxFf, binding.etMmMaxDd, binding.etMmMaxT)) f.addTextChangedListener(w)
    }

    /** 05 UTC shows five boxes, 12 UTC only Max Temp; the boxes are filled from the saved reports of the last 24 hours. */
    private fun updateMinMxPage() {
        val h = mmHour()
        show(binding.secMm05, h == 5)
        show(binding.secMm12, h == 12)
        show(binding.tvMmNone, h != 5 && h != 12)
        binding.tvMmHour.text = Td("mm_hour", "Report hour", "ساعت گزارش") + ": " + (if (h == null) "--" else "%02d:00 UTC".format(h))
        autofillMinMx(h)
        binding.tvMinMxOut.text = minMxCode()
    }

    private fun autoFillBox(key: String, f: EditText, value: String?) {
        val cur = f.text.toString()
        if (cur.isBlank() || cur == autoFilled[key]) {
            if (value != null) { f.setText(value); autoFilled[key] = value } else { if (cur.isNotBlank()) f.setText(""); autoFilled.remove(key) }
        }
    }

    private fun autofillMinMx(h: Int?) {
        val day = binding.etDay.int()
        if (selectedStationName.isEmpty() || day == null || (h != 5 && h != 12)) return
        fun key(c: Calendar) = (((c.get(Calendar.YEAR) * 100L + c.get(Calendar.MONTH) + 1) * 100L + c.get(Calendar.DAY_OF_MONTH)) * 100L + c.get(Calendar.HOUR_OF_DAY))
        val lo = key(history.timeKey(day, h!!, 24).second)
        val hi = key(history.timeKey(day, h!!, 0).second)
        val rs = ReportStore.load(this).filter { it.station == selectedStationName && it.kind == "synop" && it.sortKey > lo && it.sortKey <= hi }
        fun num(r: Report, k: String): Double? = parseNumber(r.fields.optString(k))
        val temps = rs.mapNotNull { it.dry }
        if (h == 5) {
            val rh = rs.mapNotNull { num(it, "rh") }
            val wind = rs.filter { num(it, "ff") != null }.maxByOrNull { num(it, "ff")!! }
            autoFillBox("mmMaxRh", binding.etMmMaxRh, rh.maxOrNull()?.f1())
            autoFillBox("mmMinRh", binding.etMmMinRh, rh.minOrNull()?.f1())
            autoFillBox("mmMinT", binding.etMmMinT, temps.minOrNull()?.f1())
            autoFillBox("mmMaxFf", binding.etMmMaxFf, wind?.let { num(it, "ff") }?.f1())
            autoFillBox("mmMaxDd", binding.etMmMaxDd, wind?.fields?.optString("dd")?.takeIf { it.isNotBlank() })
        } else {
            autoFillBox("mmMaxT", binding.etMmMaxT, temps.maxOrNull()?.f1())
        }
    }

    /** "40827 20094 30057 40018 51408=" at 05 UTC (Tmin, max RH, min RH, max wind); "40827 10237=" at 12 UTC (Tmax). */
    private fun minMxCode(): String {
        val wmo = binding.etSynopCode.text.toString().trim().ifEmpty { "/////" }
        fun rh(prefix: String, f: EditText) = f.num()?.let { prefix + "%03d".format(Math.round(it).toInt().coerceIn(0, 100)) } ?: (prefix.take(1) + "////")
        val groups = when (mmHour()) {
            5 -> listOf(
                binding.etMmMinT.num()?.let { SynopEncoder.groupTmin(it) } ?: "2////",
                rh("30", binding.etMmMaxRh),
                rh("40", binding.etMmMinRh),
                "5" + (binding.etMmMaxDd.int()?.let { if (it == 99) "99" else "%02d".format(Math.round(it / 10.0).toInt()) } ?: "//") +
                    (binding.etMmMaxFf.int()?.let { "%02d".format(it.coerceIn(0, 99)) } ?: "//")
            )
            12 -> listOf(binding.etMmMaxT.num()?.let { SynopEncoder.groupTmax(it) } ?: "1////")
            else -> return ""
        }
        return "$wmo ${groups.joinToString(" ")}=\nNNNN"
    }

    private fun saveMinMx(quiet: Boolean): Boolean {
        val day = binding.etDay.int()
        val hour = mmHour()
        val code = minMxCode()
        if (selectedStationName.isEmpty() || day == null || hour == null || code.isEmpty()) {
            if (!quiet) Toast.makeText(this, Td("report_no", "Select a station and fill in the report first", "ابتدا ایستگاه را انتخاب و گزارش را تکمیل کنید"), Toast.LENGTH_SHORT).show()
            return false
        }
        val c = history.timeKey(day, hour, 0).second
        ReportStore.upsert(this, Report(System.currentTimeMillis(), selectedStationName, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), hour, code, "", null, null, JSONObject(), "minmax"))
        if (!quiet) Toast.makeText(this, Td("report_saved", "Report saved", "گزارش ذخیره شد"), Toast.LENGTH_SHORT).show()
        return true
    }

    // ------------------------------------------------------------ saved reports

    /** The entries that "Complete report based on last report" copies: visibility, present weather, N, h and the cloud layers. */
    private fun snapshotFields(): JSONObject {
        val o = JSONObject()
        o.put("vis", binding.etVis.text.toString()).put("visUnit", spinnerText(binding.spVisUnit)).put("vertVis", binding.etVertVis.text.toString())
        o.put("rh", binding.etRH.text.toString()).put("dd", binding.etDd.text.toString()).put("ff", binding.etFf.text.toString())
        o.put("ix", spinnerText(binding.spIX)).put("ww", spinnerText(binding.spWW)).put("w1", spinnerText(binding.spW1)).put("w2", spinnerText(binding.spW2))
        o.put("n", spinnerText(binding.spN)).put("h", spinnerText(binding.spH))
        for (i in 0 until 4) {
            o.put("c${i + 1}v", spinnerText(layerValueSpinners[i])).put("c${i + 1}t", spinnerText(layerTypeSpinners[i])).put("c${i + 1}h", layerHeightFields[i].text.toString())
        }
        return o
    }

    /** Saves the report on screen (an older one of the same station and hour is replaced). */
    private fun saveReport(quiet: Boolean): Boolean {
        val day = binding.etDay.int()
        val hour = binding.etHour.int()?.takeIf { it in 0..23 }
        if (selectedStationName.isEmpty() || day == null || hour == null || !currentSynop.contains("NNNN")) {
            if (!quiet) Toast.makeText(this, Td("report_no", "Select a station and fill in the report first", "ابتدا ایستگاه را انتخاب و گزارش را تکمیل کنید"), Toast.LENGTH_SHORT).show()
            return false
        }
        val c = history.timeKey(day, hour, 0).second
        ReportStore.upsert(this, Report(System.currentTimeMillis(), selectedStationName, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH), hour, currentSynop, currentMetar, binding.etDry.num(), binding.etQFE.num(), snapshotFields()))
        if (!quiet) Toast.makeText(this, Td("report_saved", "Report saved", "گزارش ذخیره شد"), Toast.LENGTH_SHORT).show()
        return true
    }

    /** Fills only visibility, present weather, N, h and the cloud layers from the newest saved report of this station. */
    private fun completeFromLast() {
        val last = ReportStore.last(ReportStore.load(this), selectedStationName)
        if (last == null) {
            Toast.makeText(this, Td("report_none", "There is no saved report yet", "هنوز گزارشی ذخیره نشده است"), Toast.LENGTH_SHORT).show()
            return
        }
        val o = last.fields
        val unit = o.optString("visUnit", "km")
        visInMeters = unit == "m"                    // so the unit box does not convert the value a second time
        setSpinnerValue(binding.spVisUnit, unit)
        binding.etVis.setText(o.optString("vis"))
        setSpinnerValue(binding.spIX, o.optString("ix", "2"))
        applyIxLock()
        if (binding.spWW.isEnabled) {
            setSpinnerValue(binding.spWW, o.optString("ww")); setSpinnerValue(binding.spW1, o.optString("w1")); setSpinnerValue(binding.spW2, o.optString("w2"))
        }
        setSpinnerValue(binding.spN, o.optString("n", "0"))
        onNChanged()
        binding.etVertVis.setText(o.optString("vertVis"))
        if (binding.spH.isEnabled) setSpinnerValue(binding.spH, o.optString("h", "/"))
        for (i in 0 until 4) {
            setSpinnerValue(layerValueSpinners[i], o.optString("c${i + 1}v", "-"))
            applyLayerEnable(i)
            setSpinnerValue(layerTypeSpinners[i], o.optString("c${i + 1}t", "-"))
            layerHeightFields[i].setText(o.optString("c${i + 1}h"))
        }
        updateCloudDerived()
        scheduleRefresh()
        Toast.makeText(this, Td("report_completed", "Completed from the last report", "از آخرین گزارش تکمیل شد"), Toast.LENGTH_SHORT).show()
    }

    private fun setupReports() {
        binding.tvReportsTitle.text = Td("reports_title", "Saved reports", "گزارش‌های ذخیره‌شده")
        binding.tvReportsEmpty.text = Td("reports_empty", "No saved reports found", "گزارشی پیدا نشد")
        binding.etFYear.hint = Td("f_year", "Year", "سال")
        binding.etFMonth.hint = Td("f_month", "Month", "ماه")
        binding.etFDay.hint = Td("f_day", "Day", "روز")
        binding.etFHour.hint = Td("f_hour", "Hour", "ساعت")
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { refreshReportList() }
        }
        for (f in listOf(binding.etFYear, binding.etFMonth, binding.etFDay, binding.etFHour)) f.addTextChangedListener(w)
    }

    /** The list below the search boxes: newest first, filtered by the year, month, day and hour that are typed. */
    private fun refreshReportList() {
        val y = binding.etFYear.int(); val mo = binding.etFMonth.int(); val d = binding.etFDay.int(); val h = binding.etFHour.int()
        val list = ReportStore.load(this)
            .filter { (y == null || it.year == y) && (mo == null || it.month == mo) && (d == null || it.day == d) && (h == null || it.hour == h) }
            .sortedByDescending { it.sortKey }
        binding.llReports.removeAllViews()
        show(binding.tvReportsEmpty, list.isEmpty())
        val dp = resources.displayMetrics.density
        for (r in list.take(200)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                isClickable = true
                setOnClickListener { editReport(r) }
            }
            card.addView(TextView(this).apply {
                text = "%04d/%02d/%02d   %02d:00 UTC   \u00B7   %s%s".format(r.year, r.month, r.day, r.hour, r.station, if (r.kind == "minmax") "   [MinMx]" else "")
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.primary)); setTypeface(typeface, Typeface.BOLD); textSize = 14f
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            })
            for (code in listOf(r.synop, r.metar)) card.addView(TextView(this).apply {
                text = code.replace("\nNNNN", "").replace('\n', ' ')
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dark)); typeface = Typeface.MONOSPACE; textSize = 11f
                layoutDirection = View.LAYOUT_DIRECTION_LTR; textDirection = View.TEXT_DIRECTION_LTR
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            binding.llReports.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt()) })
        }
    }

    /** Edit, delete or copy a saved report. */
    private fun editReport(r: Report) {
        val dp = resources.displayMetrics.density
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0) }
        fun field(label: String, value: String, type: Int, multi: Boolean = false): EditText {
            col.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.label_gray)); setPadding(0, (10 * dp).toInt(), 0, 0) })
            val e = EditText(this).apply {
                setText(value); inputType = type or (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
                if (multi) { typeface = Typeface.MONOSPACE; textSize = 12f; layoutDirection = View.LAYOUT_DIRECTION_LTR; textDirection = View.TEXT_DIRECTION_LTR }
            }
            col.addView(e)
            return e
        }
        val num = InputType.TYPE_CLASS_NUMBER
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        val eSt = field(Td("l_station", "Station", "ایستگاه"), r.station, InputType.TYPE_CLASS_TEXT)
        val eY = field(Td("f_year", "Year", "سال"), r.year.toString(), num)
        val eMo = field(Td("f_month", "Month", "ماه"), r.month.toString(), num)
        val eD = field(Td("f_day", "Day", "روز"), r.day.toString(), num)
        val eH = field(Td("f_hour", "Hour", "ساعت"), r.hour.toString(), num)
        val eT = field(T("l_t"), r.dry?.f1() ?: "", dec)
        val eQ = field(T("l_qfe_hpa"), r.qfe?.f1() ?: "", dec)
        val eS = field("SYNOP", r.synop, InputType.TYPE_CLASS_TEXT, true)
        val eM = field("METAR", r.metar, InputType.TYPE_CLASS_TEXT, true)
        val sv = ScrollView(this).apply { addView(col) }
        AlertDialog.Builder(this)
            .setTitle(Td("report_edit", "Edit report", "ویرایش گزارش"))
            .setView(sv)
            .setPositiveButton(Td("btn_save", "Save changes", "ذخیره تغییرات")) { _, _ ->
                r.station = eSt.text.toString().trim().ifEmpty { r.station }
                r.year = eY.text.toString().toIntOrNull() ?: r.year
                r.month = (eMo.text.toString().toIntOrNull() ?: r.month).coerceIn(1, 12)
                r.day = (eD.text.toString().toIntOrNull() ?: r.day).coerceIn(1, 31)
                r.hour = (eH.text.toString().toIntOrNull() ?: r.hour).coerceIn(0, 23)
                r.dry = parseNumber(eT.text.toString()); r.qfe = parseNumber(eQ.text.toString())
                r.synop = eS.text.toString(); r.metar = eM.text.toString()
                ReportStore.upsert(this, r)
                refreshReportList()
            }
            .setNeutralButton(Td("delete", "Delete", "حذف")) { _, _ -> ReportStore.delete(this, r.id); refreshReportList() }
            .setNegativeButton(T("close"), null)
            .show()
    }

    // ------------------------------------------------------------ keeping the station settings

    private fun stationPrefs() = getSharedPreferences("metcode_station", MODE_PRIVATE)

    /** "Save changes" on the station page: the station and its values are kept for the next time the app opens. */
    private fun saveStation() {
        stationPrefs().edit()
            .putString("name", selectedStationName)
            .putString("height", binding.etHeight.text.toString())
            .putString("lat", binding.etLatN.text.toString())
            .putString("lon", binding.etLonE.text.toString())
            .putString("wmo", binding.etSynopCode.text.toString())
            .putString("icao", binding.etMetarCode.text.toString())
            .commit()
        Toast.makeText(this, Td("saved", "Saved", "ذخیره شد"), Toast.LENGTH_SHORT).show()
    }

    private fun restoreStation() {
        val p = stationPrefs()
        val name = p.getString("name", "") ?: ""
        if (name.isEmpty()) return
        selectedStationName = name
        binding.actvStation.setText(name, false)
        binding.etHeight.setText(p.getString("height", ""))
        binding.etLatN.setText(p.getString("lat", ""))
        binding.etLonE.setText(p.getString("lon", ""))
        binding.etSynopCode.setText(p.getString("wmo", ""))
        binding.etMetarCode.setText(p.getString("icao", ""))
    }

    /** Highest cloud amount (oktas) among the entered layers of one category (CL, CM or CH). */
    private fun categoryOktas(layers: List<CloudTypes.CloudLayer>, cat: CloudCategory): Int =
        layers.mapNotNull { l ->
            val k = CloudTypes.amountToOktas(l.amount)
            if (k != null && k > 0 && CloudTypes.legend[l.typeLabel]?.category == cat) k else null
        }.maxOrNull() ?: 0

    /** CM and CH as written in the code: "/" when low cloud covers 8/8 (CM and CH hidden), or medium cloud covers 8/8 (CH hidden). */
    private fun cloudDigits(layers: List<CloudTypes.CloudLayer>, d: CloudTypes.DerivedCloudGroup): Pair<String, String> {
        val low = categoryOktas(layers, CloudCategory.CL)
        val medium = categoryOktas(layers, CloudCategory.CM)
        val cm = if (low == 8) "/" else d.cm.toString()
        val ch = if (low == 8 || medium == 8) "/" else d.ch.toString()
        return cm to ch
    }

    private fun cloud8(d: CloudTypes.DerivedCloudGroup, layers: List<CloudTypes.CloudLayer>): String {
        val (cm, ch) = cloudDigits(layers, d)
        val nh = spinnerText(binding.spNh).ifEmpty { d.nh.toString() }
        return "8$nh${d.cl}$cm$ch"
    }

    // ----------------------------------------------------- live code refresh plumbing

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 40)
    }

    private fun attachRefreshWatchers(v: View) {
        if (v is EditText) {
            v.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { scheduleRefresh() }
            })
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) attachRefreshWatchers(v.getChildAt(i))
        }
    }

    private fun setupSimpleSpinners() {
        val l = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = scheduleRefresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        for (s in listOf(binding.spH, binding.spE, binding.spEPrime, binding.spA3, binding.spWW, binding.spW1, binding.spW2, binding.spIW, binding.spIR, binding.spTotalSnow, binding.spFresh)) s.onItemSelectedListener = l
        binding.spIX.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { applyIxLock(); scheduleRefresh() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun copyText(label: String, text: String) {
        if (!text.contains("NNNN")) {
            Toast.makeText(this, T("toast_no_code"), Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, fmt("toast_copied", label), Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ station

    private fun setupStation() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, StationData.names)
        binding.actvStation.setAdapter(adapter)
        binding.actvStation.setOnClickListener { binding.actvStation.showDropDown() }
        binding.actvStation.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.actvStation.showDropDown() }
        binding.actvStation.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position) as String
            val idx = StationData.names.indexOf(name)
            if (idx != -1) {
                selectedStationName = name
                selectedHeight = StationData.heightsM[idx]
                binding.etHeight.setText(selectedHeight.toString())
                binding.etLonE.setText(StationData.lonE[idx].toString())
                binding.etLatN.setText(StationData.latN[idx].toString())
                // codes typed earlier for this station win, otherwise the ones from the station list
                binding.etSynopCode.setText(codeStore.getSynopCode(name).ifEmpty { StationData.wmo[idx] })
                binding.etMetarCode.setText(codeStore.getMetarCode(name).ifEmpty { StationData.icao[idx] })
                autofillFromHistory()
                handle(Trigger.BAR)
                scheduleRefresh()
            }
        }
        binding.etSynopCode.addTextChangedListener(watcher {
            if (selectedStationName.isNotEmpty()) codeStore.setSynopCode(selectedStationName, binding.etSynopCode.text.toString())
        })
        binding.etMetarCode.addTextChangedListener(watcher {
            if (selectedStationName.isNotEmpty()) codeStore.setMetarCode(selectedStationName, binding.etMetarCode.text.toString())
        })
    }

    // ------------------------------------------------ live ("online") calculation

    private fun setupReactiveFields() {
        binding.etHeight.addTextChangedListener(watcher { binding.etHeight.num()?.let { selectedHeight = it }; handle(Trigger.BAR) })
        // latitude is typed as decimal degrees; the pressure formulas get degrees and decimal minutes
        binding.etLatN.addTextChangedListener(watcher {
            binding.etLatN.num()?.let { selectedLatDeg = Math.floor(it); selectedLatMin = (it - selectedLatDeg) * 60.0 }
            handle(Trigger.BAR)
        })
        binding.etBarPress.addTextChangedListener(watcher { handle(Trigger.BAR) })
        binding.etBarTemp.addTextChangedListener(watcher { handle(Trigger.BAR) })
        binding.etPCor.addTextChangedListener(watcher { handle(Trigger.PCOR) })
        binding.etQFE.addTextChangedListener(watcher { handle(Trigger.QFE) })
        binding.etT12.addTextChangedListener(watcher { handle(Trigger.T12) })
        binding.etDry.addTextChangedListener(watcher { handle(Trigger.T) })
        binding.etRH.addTextChangedListener(watcher {
            if (binding.etRH.text.isNullOrBlank()) { if (binding.etWet.num() != null) primaryIsRH = false } else primaryIsRH = true
            handle(Trigger.RH)
        })
        binding.etWet.addTextChangedListener(watcher {
            if (binding.etRH.num() == null) primaryIsRH = false
            handle(Trigger.WT)
        })
        // DP, QFF, 850, QNH, QNH Inches of: editable, but editing them changes nothing else (as in AWSPlus).
    }

    /**
     * Dependency rules (as in AWSPlus):
     *  - Bar Press / Bar Temp  -> P Cor and QFE (then the QFE rules below)
     *  - P Cor typed           -> QFE = Bar Press + P Cor
     *  - QFE, T, RH            -> WT, DP, QFF, 850   (and QNH / QNH Inches of for QFE)
     *  - T12                   -> QFF, 850
     *  - WT typed while RH is the source -> DP only
     * When WT is the source (no RH typed) it takes the role of RH: the other humidity value is calculated.
     */
    private fun handle(trigger: Trigger) {
        val qfe: Double? = when (trigger) {
            Trigger.BAR -> {
                val press = binding.etBarPress.num()
                val barTemp = binding.etBarTemp.num()
                if (press != null && barTemp != null) {
                    try {
                        val (corr, q) = MetCalc.barometerCorrection(press, selectedHeight, selectedLatDeg, selectedLatMin, barTemp)
                        setField(binding.etPCor, corr.f1())
                        setField(binding.etQFE, r1(q).f1())
                    } catch (_: Throwable) {}
                }
                binding.etQFE.num()
            }
            Trigger.PCOR -> {
                val press = binding.etBarPress.num()
                val pcor = binding.etPCor.num()
                if (press != null && pcor != null) setField(binding.etQFE, r1(press + pcor).f1())
                binding.etQFE.num()
            }
            else -> binding.etQFE.num()
        }
        if (qfe == null) return

        val qfeChanged = trigger == Trigger.BAR || trigger == Trigger.PCOR || trigger == Trigger.QFE
        if (qfeChanged) {
            try {
                val (q, qi) = MetCalc.qnhFromQfe(qfe, selectedHeight)
                setField(binding.etQNH, r1(q).f1())
                setField(binding.etQNHInch, qi.f2())
            } catch (_: Throwable) {}
            updateTendency()
        }

        val dry = binding.etDry.num() ?: return
        val wet = binding.etWet.num()
        val rh = binding.etRH.num()
        if (wet == null && rh == null) return
        val t12 = binding.etT12.num() ?: dry
        val useRH = (primaryIsRH && rh != null) || wet == null

        val res = try {
            if (useRH) MetCalc.computeFromRHGivenQFE(qfe, selectedHeight, selectedLatDeg, selectedLatMin, t12, dry, rh!!)
            else MetCalc.computeFromWetBulbGivenQFE(qfe, selectedHeight, selectedLatDeg, selectedLatMin, t12, dry, wet!!)
        } catch (_: Throwable) { return }

        fun setOther() { if (useRH) setField(binding.etWet, res.wetBulbC.f1()) else setField(binding.etRH, res.relativeHumidity.toString()) }
        fun setDP() = setField(binding.etDP, res.dewPointC.f1())
        fun setQffAnd850() { setField(binding.etQFF, res.qff.f1()); setField(binding.et850, res.h850.f1()) }

        when (trigger) {
            Trigger.T12 -> setQffAnd850()
            Trigger.WT -> {
                if (useRH) {
                    // WT edited while RH is the source: only DP follows.
                    if (wet != null) try {
                        setField(binding.etDP, MetCalc.computeFromWetBulbGivenQFE(qfe, selectedHeight, selectedLatDeg, selectedLatMin, t12, dry, wet).dewPointC.f1())
                    } catch (_: Throwable) {}
                } else { setOther(); setDP(); setQffAnd850() }
            }
            else -> { setOther(); setDP(); setQffAnd850() }
        }
    }

    // ------------------------------------------------- pressure tendency a3 / ppp3

    private fun setupTendency() {
        val w = watcher { updateTendency() }
        binding.etP1.addTextChangedListener(w)
        binding.etP2.addTextChangedListener(w)
        binding.etP3.addTextChangedListener(w)
    }

    private fun updateTendency() {
        val now = binding.etQFE.num()
        val p1 = binding.etP1.num()
        val p2 = binding.etP2.num()
        val p3 = binding.etP3.num()
        if (now == null || p3 == null) { setSpinnerValue(binding.spA3, "/"); setField(binding.etPpp3, ""); return }
        val r = PressureTendency.compute(p3, p2, p1, now)
        setSpinnerValue(binding.spA3, r.a.toString())
        setField(binding.etPpp3, r.changeHpa.f1())
    }

    // ---------------------------------------------- time, IR, Ix, hour sections

    private fun irForHour(hour: Int) = when (hour) { 0, 6, 12, 18 -> 1; 3, 9, 15, 21 -> 4; else -> 2 }
    private fun typeNameForHour(hour: Int) = T(when (hour) { 0, 6, 12, 18 -> "type_sm"; 3, 9, 15, 21 -> "type_si"; else -> "type_sn" })

    private fun setupTimeAndReportType() {
        binding.etHour.addTextChangedListener(watcher {
            val h = binding.etHour.int()
            if (h != null && h in 0..23) {
                setSpinnerValue(binding.spIR, irForHour(h).toString())   // automatic; the user may change it afterwards
                binding.tvReportType.text = typeNameForHour(h)
                applyHourSections(h)
            } else {
                binding.tvReportType.text = ""
                applyHourSections(null)
            }
            autofillFromHistory()
            updateMinMxPage()
        })
        binding.etDay.addTextChangedListener(watcher { autofillFromHistory(); updateMinMxPage() })
    }

    private fun applyIxLock() {
        val open = spinnerText(binding.spIX) == "1"
        for (s in listOf(binding.spWW, binding.spW1, binding.spW2)) {
            s.isEnabled = open
            if (!open) s.setSelection(0)
        }
    }

    private fun show(v: View, visible: Boolean) { v.visibility = if (visible) View.VISIBLE else View.GONE }

    /** Shows only the fields the selected hour needs. */
    private fun applyHourSections(hour: Int?) {
        val h = hour ?: -1
        val sm = h == 0 || h == 6 || h == 12 || h == 18
        show(binding.secSnowGroup, sm)
        show(binding.secETg, h == 6)
        show(binding.secEPrime, sm)
        show(binding.secFreshEvap, sm)
        show(binding.secTminTmax, h == 3 || h == 15)
        show(binding.secSoilT, h == 3 || h == 9 || h == 15)
        show(binding.secSoilM, h == 9)
        show(binding.secSun, h == 18)
        show(binding.secMaxWind, h == 21)
        fun box(f: View) = f.parent as View
        show(box(binding.etEvap), h == 6)                // evaporation: 06 UTC only; E and Tg are also 06 UTC only
        binding.tvSnowTitle.text = keepTogether(
            if (h == 6 || h < 0) T("g_snow")
            else Td("g_snow_sm", "Group 4Esss (E' & Snow Depth) 9SPSPSPSP (Height of Fresh Snow)", "\u06AF\u0631\u0648\u0647 4Esss (E' \u0648 \u0627\u0631\u062A\u0641\u0627\u0639 \u0628\u0631\u0641) 9SPSPSPSP (\u0627\u0631\u062A\u0641\u0627\u0639 \u0628\u0631\u0641 \u062A\u0627\u0632\u0647)"))
        show(box(binding.etR1h), h >= 0 && h % 3 != 0)   // SN hours only
        show(box(binding.etR6h), sm)                     // SM hours only
        show(box(binding.etR24h), h >= 0 && h % 3 == 0)  // 00, 03, ..., 21
    }

    private fun fillPhoneTime() {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.add(Calendar.MINUTE, 30) // report hour = nearest UTC hour (20:30 -> 21:00)
        binding.etDay.setText(utc.get(Calendar.DAY_OF_MONTH).toString())
        binding.etHour.setText(utc.get(Calendar.HOUR_OF_DAY).toString())
    }

    /** Names the hours T12 and the three earlier pressures belong to, and fills them from saved reports. */
    private fun autofillFromHistory() {
        val day = binding.etDay.int()
        val hour = binding.etHour.int()
        if (day == null || hour == null || hour !in 0..23) {
            binding.etT12.hint = boldText(T("hint_temp_utc"))
            for (f in listOf(binding.etP1, binding.etP2, binding.etP3)) f.hint = boldText(T("hint_utc"))
            return
        }
        binding.etT12.hint = boldText(fmt("fmt_temp_utc", history.hourOf(day, hour, 12)))
        binding.etP1.hint = boldText(fmt("fmt_qfe_utc", history.hourOf(day, hour, 1)))
        binding.etP2.hint = boldText(fmt("fmt_qfe_utc", history.hourOf(day, hour, 2)))
        binding.etP3.hint = boldText(fmt("fmt_qfe_utc", history.hourOf(day, hour, 3)))
        if (selectedStationName.isEmpty()) return
        val saved = ReportStore.load(this)
        fun at(back: Int): Report? {
            val c = history.timeKey(day, hour, back).second
            return ReportStore.find(saved, selectedStationName, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY))
        }
        // a value that came from a saved report is replaced when the hour changes; a value the user typed is kept
        fun fill(key: String, f: EditText, value: Double?) {
            val cur = f.text.toString()
            if (cur.isBlank() || cur == autoFilled[key]) {
                if (value != null) { setField(f, value.f1()); autoFilled[key] = value.f1() } else { if (cur.isNotBlank()) setField(f, ""); autoFilled.remove(key) }
            }
        }
        fill("t12", binding.etT12, at(12)?.dry)
        handle(Trigger.T12)
        val fields = listOf(binding.etP1, binding.etP2, binding.etP3)
        for (k in 1..3) fill("p$k", fields[k - 1], at(k)?.qfe)
        updateTendency()
    }

    // ------------------------------------------------- N, h, layers, visibility

    private fun setupCloudAndN() {
        setSpinnerValue(binding.spH, "/")
        binding.spN.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onNChanged(); scheduleRefresh() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        for (i in 0 until 4) {
            layerValueSpinners[i].onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { applyLayerEnable(i); updateCloudDerived(); scheduleRefresh() }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            layerTypeSpinners[i].onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { updateCloudDerived(); scheduleRefresh() }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            layerHeightFields[i].addTextChangedListener(watcher { autoH() })
        }
        onNChanged()
    }

    /** Value choices for the layers depend on N: N=2 -> 0 1 2 / TR, N=4 -> 0 1 2 3 4 / TR, ... */
    private fun layerOptions(n: String): List<String>? {
        val k = n.toIntOrNull()
        return when {
            n == "TR" -> listOf("-", "0", "1", "/", "TR")
            k != null && k in 1..8 -> listOf("-") + (0..k).map { it.toString() } + listOf("/", "TR")
            else -> null // 0, 9 and / : layers are closed
        }
    }

    private fun onNChanged() {
        val n = spinnerText(binding.spN)
        val options = layerOptions(n)
        val open = options != null

        for (i in 0 until 4) {
            val sp = layerValueSpinners[i]
            val previous = spinnerText(sp)
            val list = options ?: listOf("-")
            sp.adapter = StyledArrayAdapter(this, list)
            val keep = list.indexOf(previous)
            sp.setSelection(if (open && keep >= 0) keep else 0)
            sp.isEnabled = open
            applyLayerEnable(i)
        }

        val vvOpen = n == "9"
        binding.etVertVis.isEnabled = vvOpen
        if (!vvOpen) setField(binding.etVertVis, "")

        when (n) {
            "0" -> { setSpinnerValue(binding.spH, "9"); binding.spH.isEnabled = false }
            "/" -> { setSpinnerValue(binding.spH, "/"); binding.spH.isEnabled = false }
            else -> binding.spH.isEnabled = true
        }
        updateCloudDerived()
    }

    /** Type and Height open as soon as the layer has a Value. */
    private fun applyLayerEnable(i: Int) {
        val v = spinnerText(layerValueSpinners[i])
        val hasValue = layerValueSpinners[i].isEnabled && v != "-" && v.isNotEmpty()
        layerTypeSpinners[i].isEnabled = hasValue
        layerHeightFields[i].isEnabled = hasValue
        if (!hasValue) {
            layerTypeSpinners[i].setSelection(0)
            setField(layerHeightFields[i], "")
        }
    }

    /** h follows the lowest entered layer height unless the observer picks it by hand afterwards. */
    private fun autoH() {
        if (!binding.spH.isEnabled) return
        val lowest = layerHeightFields.mapNotNull { f -> f.int() }.minOrNull() ?: return
        setSpinnerValue(binding.spH, CloudTypes.hCodeForHeight(lowest))
    }

    private fun readCloudLayers(): List<CloudTypes.CloudLayer> = (0 until 4).map { i ->
        CloudTypes.CloudLayer(spinnerText(layerValueSpinners[i]), spinnerText(layerTypeSpinners[i]), layerHeightFields[i].int())
    }

    private var nhItems: List<String> = emptyList()
    private var lastAutoNh = ""

    /** Nh choices: 0 up to the total cloud amount N (never 9), then "/". */
    private fun nhOptions(): List<String> {
        val n = spinnerText(binding.spN)
        val k = n.toIntOrNull()
        val nums = when {
            k != null && k in 1..8 -> (0..k).map { it.toString() }
            n == "TR" -> listOf("0", "1")
            n == "9" || n == "/" -> emptyList()
            else -> listOf("0")
        }
        return nums + listOf("/")
    }

    /** CL, CM and CH are filled by the app. Nh follows the layers by itself until you pick another value from its list. */
    private fun updateCloudDerived() {
        val layers = readCloudLayers()
        val d = CloudTypes.derive(layers)
        val (cm, ch) = cloudDigits(layers, d)
        binding.tvCL.text = d.cl.toString()
        binding.tvCM.text = cm
        binding.tvCH.text = ch
        val n = spinnerText(binding.spN)
        val auto = if (n == "9" || n == "/") "/" else d.nh.toString()
        val items = nhOptions()
        if (items != nhItems) {
            nhItems = items
            binding.spNh.adapter = StyledArrayAdapter(this, items)
            lastAutoNh = ""
        }
        val current = spinnerText(binding.spNh)
        if (lastAutoNh.isEmpty() || current == lastAutoNh || current !in items) setSpinnerValue(binding.spNh, if (auto in items) auto else items.first())
        lastAutoNh = auto
    }

    private fun setupVisibilityUnit() {
        binding.spVisUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val meters = pos == 1
                if (meters != visInMeters) {
                    val value = binding.etVis.num()
                    visInMeters = meters
                    if (value != null) setField(binding.etVis, if (meters) (value * 1000).roundToInt().toString() else (value / 1000).toString())
                }
                scheduleRefresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // ------------------------------------------------------- live SYNOP / METAR

    /**
     * Builds both codes from whatever is filled in right now. Anything still missing is written as "/"
     * (so 1//// 2//// 3//// 4//// 5//// appear until the values exist). Only a selected station is required.
     */
    private fun refreshCodes() {
        updateInfoCards()
        updateRequired()
        if (selectedStationName.isEmpty()) {
            currentSynop = ""; currentMetar = ""
            binding.tvSynopOut.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            binding.tvSynopOut.layoutDirection = View.LAYOUT_DIRECTION_INHERIT
            binding.tvSynopOut.text = T("msg_select_station")
            binding.tvMetarOut.text = ""
            return
        }

        val day = binding.etDay.int()
        val hour = binding.etHour.int()?.takeIf { it in 0..23 }
        val synopCode = binding.etSynopCode.text.toString().trim().ifEmpty { "/////" }
        val metarCode = binding.etMetarCode.text.toString().trim().uppercase().ifEmpty { "////" }
        val dry = binding.etDry.num()
        val wet = binding.etWet.num()
        val rh = binding.etRH.num()
        val dewPoint = binding.etDP.num()
        val qfe = binding.etQFE.num()
        val qff = binding.etQFF.num()
        val qnh = binding.etQNH.num()
        val qnhInch = binding.etQNHInch.num()
        val dd = binding.etDd.int()
        val ff = binding.etFf.int()
        val gust = binding.etGust.int()
        val ffExact = binding.etFf.num()        // METAR wind uses the exact m/s (2.7 m/s = 05KT)
        val gustExact = binding.etGust.num()
        val variable = dd == 99 // dd 99 means variable wind (SYNOP 99, METAR VRB)
        val visInput = binding.etVis.num()
        val n = spinnerText(binding.spN)
        val h = spinnerText(binding.spH).ifEmpty { "/" }
        val ix = spinnerText(binding.spIX).toIntOrNull() ?: 2
        val visM = visInput?.let { if (visInMeters) it.roundToInt() else (it * 1000).roundToInt() }
        val hr = hour ?: -1
        val ir = if (hour != null) spinnerText(binding.spIR).toIntOrNull() else null

        val layers = readCloudLayers()
        val validLayers = layers.filter { l ->
            val k = CloudTypes.amountToOktas(l.amount)
            k != null && k > 0 && CloudTypes.legend.containsKey(l.typeLabel)
        }
        val cloud = CloudTypes.derive(layers)

        currentSynop = buildSynop(day, hour, hr, ir, synopCode, dry, wet, rh, dewPoint, qfe, qff, dd, ff, variable, visM, n, h, ix, validLayers, cloud)
        currentMetar = buildMetar(day, hour, metarCode, dry, dewPoint, qnh, qnhInch, dd, ffExact, gustExact, variable, visM, n, ix, validLayers)
        // the codes always read left to right, also in Persian
        for (tv in listOf(binding.tvSynopOut, binding.tvMetarOut)) {
            tv.layoutDirection = View.LAYOUT_DIRECTION_LTR
            tv.textDirection = View.TEXT_DIRECTION_LTR
        }
        binding.tvSynopOut.text = currentSynop
        binding.tvMetarOut.text = currentMetar
    }

    private fun buildSynop(
        day: Int?, hour: Int?, hr: Int, ir: Int?, synopCode: String,
        dry: Double?, wet: Double?, rh: Double?, dewPoint: Double?, qfe: Double?, qff: Double?,
        dd: Int?, ff: Int?, variable: Boolean, visM: Int?, n: String, h: String, ix: Int,
        validLayers: List<CloudTypes.CloudLayer>, cloud: CloudTypes.DerivedCloudGroup
    ): String {
        val header = "AAXX " + (if (day != null && hour != null) SynopEncoder.yygg(day, hour, spinnerText(binding.spIW).toIntOrNull() ?: 1) else "////1")
        val g = mutableListOf<String>()
        g.add(SynopEncoder.iiiii(synopCode))
        val vv = if (visM != null) "%02d".format(SynopEncoder.visibilityCode(visM)) else "//"
        g.add("%s%d%s%s".format(ir?.toString() ?: "/", ix, h, vv))
        g.add(SynopEncoder.groupNddff(n, dd, variable, ff))
        g.add(dry?.let { SynopEncoder.group1_T(it) } ?: "1////")
        g.add(dewPoint?.let { SynopEncoder.group2_Td(it) } ?: "2////")
        g.add(qfe?.let { SynopEncoder.group3_QFE(it) } ?: "3////")
        g.add(qff?.let { SynopEncoder.group4_QFF(it) } ?: "4////")
        g.add(SynopEncoder.groupTendency(spinnerText(binding.spA3).toIntOrNull(), binding.etPpp3.num()))
        if (ir == 1) g.add(SynopEncoder.groupPrecip(binding.etR6h.precip(), 1)) // SM hours: 6 h amount in section 1
        if (ix == 1) {
            g.add(SynopEncoder.group7_weather(
                spinnerText(binding.spWW).toIntOrNull() ?: 0,
                spinnerText(binding.spW1).toIntOrNull() ?: 0,
                spinnerText(binding.spW2).toIntOrNull() ?: 0))
        }
        if (validLayers.isNotEmpty()) g.add(cloud8(cloud, validLayers))

        // ---- Section 3
        val s3 = mutableListOf<String>()
        if (hr == 3 || hr == 15) {
            binding.etTmax12.num()?.let { s3.add(SynopEncoder.groupTmax(it)) }
            binding.etTmin12.num()?.let { s3.add(SynopEncoder.groupTmin(it)) }
        }
        if (hr == 6) {
            val tg = binding.etTg.num()
            val e = spinnerText(binding.spE)
            if (tg != null || e != "/") s3.add(SynopEncoder.groupTg(e, tg))
        }
        var snowGroupAdded = false
        if (hr == 0 || hr == 6 || hr == 12 || hr == 18) {
            val ep = spinnerText(binding.spEPrime)
            val sss = spinnerText(binding.spTotalSnow).toIntOrNull()
            if (sss != null || ep != "/") { s3.add(SynopEncoder.groupSnow(ep, sss)); snowGroupAdded = true }
        }
        if (hr == 6) binding.etEvap.num()?.let { s3.add(SynopEncoder.groupEvaporation(it)) }
        if (hr == 18) binding.etSun.num()?.let { s3.add(SynopEncoder.groupSunshine(it)) }
        if (ir == 2) s3.add(SynopEncoder.groupPrecip(binding.etR1h.precip(), 5)) // SN hours: 1 h amount in section 3
        if (hr >= 0 && hr % 3 == 0) SynopEncoder.group7Precip24(binding.etR24h.precip())?.let { s3.add(it) }
        if (n == "9") {
            binding.etVertVis.int()?.let { s3.add(SynopEncoder.groupVerticalVisibility(it)) }
        } else {
            s3.addAll(cloudLayerGroups(validLayers))
        }
        binding.etGust.int()?.let { s3.add("910%02d".format(it.coerceIn(0, 99))) }
        // fresh snow: only together with the snow group; written with a zero value when none is entered
        if ((hr == 0 || hr == 6 || hr == 12 || hr == 18) && snowGroupAdded) {
            s3.add(SynopEncoder.FRESH_SNOW_TIME_GROUP)
            s3.add(SynopEncoder.groupFreshSnow(spinnerText(binding.spFresh).toIntOrNull() ?: 0))
        }
        if (s3.isNotEmpty()) { g.add("333"); g.addAll(s3) }

        // ---- Section 5 (wet bulb and humidity are always present, as slashes when unknown)
        g.add("555")
        g.add(wet?.let { SynopEncoder.group1_Tw(it) } ?: "1////")
        g.add(rh?.let { SynopEncoder.group29_RH(it.roundToInt().toLong()) } ?: "29///")
        if (hr == 21) {
            val md = binding.etMaxWindDd.int(); val mf = binding.etMaxWindFf.int()
            val gd = binding.etMaxGustDd.int(); val gf = binding.etMaxGustFf.int()
            if (md != null && mf != null) g.add(SynopEncoder.groupMaxWind(md, mf))
            if (gd != null && gf != null) g.add(SynopEncoder.groupGust24(gd, gf))
        }
        if (hr == 3 || hr == 9 || hr == 15) {
            SynopEncoder.soilTemperatureBlock(listOf(binding.etSoilT5, binding.etSoilT10, binding.etSoilT20, binding.etSoilT30, binding.etSoilT50, binding.etSoilT100).map { it.num() })?.let { g.add(it) }
        }
        if (hr == 9) {
            SynopEncoder.soilMoistureBlock(listOf(binding.etSoilM5, binding.etSoilM10, binding.etSoilM20, binding.etSoilM30, binding.etSoilM50, binding.etSoilM70, binding.etSoilM100).map { it.num() })?.let { g.add(it) }
        }
        return header + "\n" + g.joinToString(" ") + "=\nNNNN"
    }

    /**
     * 8NsChshs groups for section 3: layers from the lowest up; the lowest needs 1/8 or more, the second
     * 3/8 or more, the third 5/8 or more (trace "TR" is below 1/8 and is not reported); Cb is always kept.
     */
    private fun cloudLayerGroups(validLayers: List<CloudTypes.CloudLayer>): List<String> {
        val out = mutableListOf<String>()
        val thresholds = listOf(1, 3, 5)
        var taken = 0
        for (l in validLayers.filter { it.amount != "TR" }.sortedBy { it.heightM ?: Int.MAX_VALUE }) {
            val oktas = CloudTypes.amountToOktas(l.amount) ?: continue
            val genus = CloudTypes.genusDigit(l.typeLabel) ?: continue
            val hm = l.heightM ?: continue
            val isCb = genus == 9
            if (isCb || (taken < thresholds.size && oktas >= thresholds[taken])) {
                out.add(SynopEncoder.groupCloudLayer(oktas, genus, hm))
                if (!isCb) taken++
            }
            if (out.size >= 4) break
        }
        return out
    }

    private fun buildMetar(
        day: Int?, hour: Int?, metarCode: String, dry: Double?, dewPoint: Double?, qnh: Double?, qnhInch: Double?,
        dd: Int?, ff: Double?, gust: Double?, variable: Boolean, visM: Int?, n: String, ix: Int,
        validLayers: List<CloudTypes.CloudLayer>
    ): String {
        val m = mutableListOf("METAR", metarCode)
        m.add(if (day != null && hour != null) "%02d%02d00Z".format(day, hour) else "//////Z")
        m.add(if ((dd != null || variable) && ff != null) MetarEncoder.windGroup(dd ?: 0, ff, gust, variable) else "/////KT")
        val wx = if (ix == 1) MetarWeather.fromSynopWw(spinnerText(binding.spWW).toIntOrNull(), visM) else null
        val skyObscured = n == "9"
        val lowOrConvective = validLayers.any { (it.heightM ?: 0) < 1500 || MetarEncoder.metarCloudSuffix(it.typeLabel).orEmpty().isNotEmpty() }
        if (visM != null && visM >= 10000 && !lowOrConvective && wx == null && !skyObscured) {
            m.add("CAVOK")
        } else {
            m.add(if (visM != null) MetarEncoder.visibilityGroup(visM) else "////")
            if (wx != null) m.add(wx)
            if (skyObscured) {
                binding.etVertVis.int()?.let { m.add(MetarEncoder.verticalVisibilityGroup(it)) }
            } else if (validLayers.isEmpty()) {
                if (n == "0") m.add("NSC")
            } else {
                for (l in validLayers.sortedBy { it.heightM ?: Int.MAX_VALUE }) {
                    val oktas = CloudTypes.amountToOktas(l.amount) ?: continue
                    m.add(MetarEncoder.cloudHeightGroupMeters(MetarEncoder.cloudAmountCode(oktas), l.heightM ?: 0, MetarEncoder.metarCloudSuffix(l.typeLabel)))
                }
            }
        }
        fun t(x: Double?): String = if (x == null) "//" else (Math.round(x).toInt()).let { (if (it < 0) "M" else "") + "%02d".format(
            abs(it)
        ) }
        m.add(t(dry) + "/" + t(dewPoint))
        m.add(if (qnh != null) MetarEncoder.qnhGroup(qnh) else "Q////")
        if (binding.cbIncludeQnhInch.isChecked && qnhInch != null) m.add(MetarEncoder.qnhInchGroup(qnhInch))
        return m.joinToString(" ") + "=\nNNNN"
    }
}
