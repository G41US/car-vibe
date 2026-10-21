package com.g41us.carvibe

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Every installed app, in a grid that starts right of the dead touch strip. */
class AppsActivity : BaseActivity() {

    private var apps: List<AppEntry> = emptyList()
    private val iconCache = HashMap<String, Drawable?>()
    private lateinit var count: TextView
    private val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = (convertView as? LinearLayout) ?: vbox(Gravity.CENTER_HORIZONTAL).apply {
                setPadding(dpi(6f), dpi(12f), dpi(6f), dpi(10f))
                Ui.clickable(this, Ui.round(Color.TRANSPARENT, dp(18f)))
                isClickable = false // the grid handles taps
                addView(ImageView(this@AppsActivity), LinearLayout.LayoutParams(dpi(56f), dpi(56f)))
                addView(label("", 13f, getColor(R.color.text_soft), R.font.manrope_medium).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(8f) })
                layoutParams = AbsListView.LayoutParams(MATCH, dpi(112f))
            }
            val app = apps[position]
            val key = app.pkg + "/" + app.cls
            val drawable = if (iconCache.containsKey(key)) iconCache[key] else app.icon(this@AppsActivity).also { iconCache[key] = it }
            (cell.getChildAt(0) as ImageView).setImageDrawable(drawable)
            (cell.getChildAt(1) as TextView).text = app.label
            return cell
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = hbox(Gravity.TOP).apply { setBackgroundColor(getColor(R.color.bg)) }
        root.addView(View(this), LinearLayout.LayoutParams(dpi(prefs.deadZone.toFloat()), MATCH))

        val main = vbox().apply { setPadding(dpi(6f), dpi(16f), dpi(18f), 0) }
        val top = hbox()
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(getColor(R.color.text))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "Back"
            Ui.clickable(this, Ui.round(getColor(R.color.card), dp(16f), getColor(R.color.card_edge), dpi(1f)))
            setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(dpi(52f), dpi(52f)))
        top.addView(label("Apps", 26f, getColor(R.color.text), R.font.manrope_semibold), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dpi(16f) })
        count = label("", 14f, getColor(R.color.text_mute))
        top.addView(count, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dpi(12f) })
        main.addView(top)

        val grid = GridView(this).apply {
            numColumns = 6
            verticalSpacing = dpi(4f)
            horizontalSpacing = dpi(4f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = Ui.round(Ui.withAlpha(prefs.accent, 0x22), dp(18f))
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dpi(12f), 0, dpi(16f))
            adapter = this@AppsActivity.adapter
            setOnItemClickListener { _, _, position, _ -> Apps.launch(this@AppsActivity, apps[position]) }
            setOnItemLongClickListener { _, _, position, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + apps[position].pkg)))
                } catch (e: Exception) { }
                true
            }
        }
        main.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(main, LinearLayout.LayoutParams(0, MATCH, 1f))
        setContentView(root)

        Net.async({ Apps.list(this) }) { list ->
            apps = list ?: emptyList()
            count.text = "${apps.size} apps"
            adapter.notifyDataSetChanged()
        }
    }
}
