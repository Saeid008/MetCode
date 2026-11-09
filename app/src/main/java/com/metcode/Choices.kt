package com.metcode

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView

/** One dropdown entry. toString() is what the closed spinner shows and what the code reads (the code or label). */
class Choice(val code: String, val desc: String, val label: String = code) {
    override fun toString() = label
}

/** Closed spinner shows only the code; the open list shows "code - description" in the app language. */
class ChoiceAdapter(context: Context, items: List<Choice>) :
    ArrayAdapter<Choice>(context, R.layout.spinner_item, items) {

    init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = super.getDropDownView(position, convertView, parent) as TextView
        val c = getItem(position)!!
        v.text = if (c.desc.isEmpty()) c.label else "${c.code} \u2014 ${c.desc}"
        v.isSingleLine = false
        v.maxLines = 5
        v.ellipsize = null
        return Style.styleInput(context, v)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        Style.styleInput(context, super.getView(position, convertView, parent) as TextView)
}

/** Plain text list (N, unit, cloud type...) with the app text size and font. */
class StyledArrayAdapter(context: Context, items: List<String>, layout: Int = R.layout.spinner_item) :
    ArrayAdapter<String>(context, layout, items) {
    init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        Style.styleInput(context, super.getView(position, convertView, parent) as TextView)
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        Style.styleInput(context, super.getDropDownView(position, convertView, parent) as TextView)
}

/** List for the snow boxes: always shows every item, and picking one puts only its code into the box. */
class SpecialAdapter(context: Context, private val all: List<Choice>) :
    ArrayAdapter<Choice>(context, R.layout.spinner_item, all) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = super.getView(position, convertView, parent) as TextView
        val c = getItem(position)!!
        v.text = "${c.code} \u2014 ${c.desc}"
        v.isSingleLine = false
        v.maxLines = 4
        v.ellipsize = null
        return Style.styleInput(context, v)
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) = FilterResults().apply { values = all; count = all.size }
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) { notifyDataSetChanged() }
        override fun convertResultToString(resultValue: Any?) = (resultValue as Choice).code
    }
}

/** The dropdown lists, read from lang_en.txt / lang_fa.txt (sections [a3], [e], [eprime], [h], [past], [ww], [iw], [ir], [ix], [total], [fresh]). */
object Choices {
    var a3: List<Choice> = emptyList()
    var e: List<Choice> = emptyList()
    var ePrime: List<Choice> = emptyList()
    var h: List<Choice> = emptyList()
    var past: List<Choice> = emptyList()
    var ww: List<Choice> = emptyList()
    var iw: List<Choice> = emptyList()
    var ir: List<Choice> = emptyList()
    var ix: List<Choice> = emptyList()
    var totalSnowSpecial: List<Choice> = emptyList()
    var freshSnowSpecial: List<Choice> = emptyList()
    /** Snow Depth and Fresh Snow dropdowns: "—" (nothing coded), then the special codes of the language files ([total] and [fresh]). */
    var totalSnow: List<Choice> = emptyList()
    var freshSnow: List<Choice> = emptyList()

    fun load(c: Context) {
        fun t(name: String) = Lang.table(c, name).map { Choice(it.first, it.second) }
        a3 = t("a3"); e = t("e"); ePrime = t("eprime"); h = t("h"); past = t("past")
        ww = t("ww"); iw = t("iw"); ir = t("ir"); ix = t("ix")
        totalSnowSpecial = t("total")
        freshSnowSpecial = t("fresh")
        val none = Choice("", "", "\u2014")
        totalSnow = listOf(none) + totalSnowSpecial
        freshSnow = listOf(none) + freshSnowSpecial
    }
}
