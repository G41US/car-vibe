package com.g41us.carvibe

import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers for building the settings and setup screens in code. */
fun BaseActivity.vbox(gravity: Int = Gravity.NO_GRAVITY): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; this.gravity = gravity }

fun BaseActivity.hbox(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; this.gravity = gravity }

fun BaseActivity.label(
    text: CharSequence,
    size: Float = 15f,
    color: Int = getColor(R.color.text),
    font: Int = R.font.manrope_regular,
    caps: Boolean = false,
    spacing: Float = 0f,
): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    typeface = resources.getFont(font)
    isAllCaps = caps
    letterSpacing = spacing
}

fun BaseActivity.icon(res: Int, color: Int, sizeDp: Float = 22f): ImageView = ImageView(this).apply {
    setImageResource(res)
    setColorFilter(color)
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    layoutParams = LinearLayout.LayoutParams(dpi(sizeDp), dpi(sizeDp))
}

/** A pill button. Filled = the accent "primary" style; otherwise an outlined one. */
fun BaseActivity.pill(text: String, filled: Boolean, onClick: () -> Unit): TextView =
    label(text, 15f, if (filled) getColor(R.color.bg) else getColor(R.color.text),
        if (filled) R.font.manrope_bold else R.font.manrope_semibold).apply {
        gravity = Gravity.CENTER
        minWidth = dpi(110f)
        setPadding(dpi(22f), 0, dpi(22f), 0)
        val bg = if (filled) Ui.round(prefs.accent, dp(26f))
        else Ui.round(Color.TRANSPARENT, dp(26f), Color.parseColor("#2A3446"), dpi(1f))
        Ui.clickable(this, bg)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpi(52f))
    }

fun BaseActivity.field(hint: String, value: String): EditText = EditText(this).apply {
    setText(value)
    this.hint = hint
    textSize = 17f
    setTextColor(getColor(R.color.text))
    setHintTextColor(Color.parseColor("#7D889C"))
    typeface = resources.getFont(R.font.manrope_medium)
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    isSingleLine = true
    background = Ui.round(Color.parseColor("#0B1019"), dp(16f), prefs.accent, dpi(1f))
    setPadding(dpi(18f), 0, dpi(18f), 0)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(54f))
}

fun View.lp(w: Int, h: Int, weight: Float = 0f, top: Int = 0, start: Int = 0): View {
    layoutParams = LinearLayout.LayoutParams(w, h, weight).apply {
        topMargin = top
        marginStart = start
    }
    return this
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
