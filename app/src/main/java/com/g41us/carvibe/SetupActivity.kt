package com.g41us.carvibe

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * First-run setup: 1) car brand, whose logo is fetched from Wikidata,
 * 2) model name, 3) phone app and permissions.
 */
class SetupActivity : BaseActivity() {

    private var step = 0
    private val steps = mutableListOf<View>()
    private val stepLabels = mutableListOf<Pair<TextView, TextView>>()

    // Step 1
    private lateinit var search: EditText
    private lateinit var status: TextView
    private var results: List<Brand> = emptyList()
    private val thumbs = HashMap<String, Bitmap?>()
    private var selectedId: String? = null
    private var searchToken = 0
    private val runSearch = Runnable { doSearch() }

    // Step 2
    private lateinit var model: EditText

    // Step 3
    private lateinit var accessList: LinearLayout

    private val gridAdapter = object : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val brand = results[position]
            val selected = brand.id == selectedId
            val tile = vbox(Gravity.CENTER).apply {
                background = if (selected) Ui.round(Color.parseColor("#0D1626"), dp(20f), prefs.accent, dpi(2f))
                else Ui.round(getColor(R.color.dock), dp(20f), getColor(R.color.card_edge), dpi(1f))
                layoutParams = AbsListView.LayoutParams(MATCH, dpi(124f))
            }
            val circle = FrameLayout(this@SetupActivity).apply {
                background = Ui.oval(Color.parseColor("#DDE3EC"))
                clipToOutline = true
            }
            val img = ImageView(this@SetupActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dpi(10f), dpi(10f), dpi(10f), dpi(10f))
                setImageBitmap(thumbs[brand.id])
            }
            circle.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
            tile.addView(circle, LinearLayout.LayoutParams(dpi(62f), dpi(62f)))
            tile.addView(label(brand.label, 14f, getColor(R.color.text), R.font.manrope_semibold).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dpi(8f), 0, dpi(8f), 0)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(10f) })
            return tile
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = hbox(Gravity.TOP).apply { setBackgroundColor(getColor(R.color.bg)) }
        root.addView(buildStrip(), LinearLayout.LayoutParams(dpi(prefs.deadZone.toFloat()), MATCH))

        val content = FrameLayout(this)
        steps += buildBrandStep()
        steps += buildModelStep()
        steps += buildAccessStep()
        steps.forEach { content.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }
        root.addView(content, LinearLayout.LayoutParams(0, MATCH, 1f))
        setContentView(root)
        show(0)
    }

    override fun onResume() {
        super.onResume()
        if (step == 2) renderAccess()
    }

    @Deprecated("Back moves between steps")
    override fun onBackPressed() {
        if (step > 0) show(step - 1) else if (prefs.setupDone) finish()
    }

    private fun show(index: Int) {
        step = index
        steps.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        stepLabels.forEachIndexed { i, (num, text) ->
            val active = i == index
            val done = i < index
            num.background = if (active || done) Ui.oval(prefs.accent)
            else Ui.round(Color.TRANSPARENT, dp(12f), Color.parseColor("#2E3A50"), dpi(1f))
            num.setTextColor(if (active || done) getColor(R.color.bg) else getColor(R.color.text_mute))
            text.setTextColor(if (active) getColor(R.color.text) else getColor(R.color.text_mute))
            text.typeface = resources.getFont(if (active) R.font.manrope_semibold else R.font.manrope_regular)
        }
        hideKeyboard()
        if (index == 2) renderAccess()
    }

    /* -------------------------------------------------------- left strip */

    private fun buildStrip(): View {
        val strip = vbox().apply {
            setPadding(dpi(24f), dpi(26f), dpi(8f), dpi(20f))
        }
        strip.addView(label("Setup", 11f, getColor(R.color.text_mute), R.font.manrope_bold, caps = true, spacing = 0.16f))
        listOf("Car brand", "Model", "Phone").forEachIndexed { i, name ->
            val row = hbox()
            val num = label("${i + 1}", 12f, getColor(R.color.text_mute), R.font.manrope_bold).apply { gravity = Gravity.CENTER }
            val text = label(name, 14f, getColor(R.color.text_mute))
            row.addView(num, LinearLayout.LayoutParams(dpi(24f), dpi(24f)))
            row.addView(text, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dpi(10f) })
            strip.addView(row, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(22f) })
            stepLabels += num to text
        }
        return strip
    }

    private fun page(title: String, subtitle: String): LinearLayout = vbox().apply {
        setPadding(dpi(30f), dpi(24f), dpi(26f), dpi(20f))
        addView(label(title, 30f, getColor(R.color.text), R.font.manrope_semibold))
        addView(label(subtitle, 14f, getColor(R.color.text_dim)), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(4f) })
    }

    private fun footer(note: String, vararg buttons: View): LinearLayout = hbox().apply {
        addView(label(note, 12f, getColor(R.color.text_mute)), LinearLayout.LayoutParams(0, WRAP, 1f))
        buttons.forEachIndexed { i, b ->
            if (i > 0) addView(View(this@SetupActivity), LinearLayout.LayoutParams(dpi(10f), 1))
            addView(b)
        }
    }

    /* -------------------------------------------------------- step 1 */

    private fun buildBrandStep(): View {
        val p = page("What car is this?", "Type the brand. Car Vibe finds its logo online and saves it on this unit.")
        p.addView(label("Car brand", 12f, getColor(R.color.text_dim), R.font.manrope_semibold),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(16f) })
        search = field("Type your car brand", prefs.brandName).apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawablePadding = dpi(12f)
            compoundDrawablesRelative[0]?.setTint(getColor(R.color.text_dim))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { Net.main.removeCallbacks(runSearch); doSearch(); hideKeyboard(); true } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    Net.main.removeCallbacks(runSearch)
                    Net.main.postDelayed(runSearch, 700)
                }
            })
        }
        p.addView(search, LinearLayout.LayoutParams(MATCH, dpi(54f)).apply { topMargin = dpi(6f) })
        status = label("", 13f, getColor(R.color.text_mute))
        p.addView(status, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(8f) })

        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dpi(12f)
            horizontalSpacing = dpi(12f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = Ui.round(Color.TRANSPARENT, 0f)
            isVerticalScrollBarEnabled = false
            adapter = gridAdapter
            setOnItemClickListener { _, _, position, _ -> choose(results[position]) }
        }
        p.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dpi(8f) })
        p.addView(footer("Logos come from Wikipedia. Not right? Use your own image instead.",
            pill("Use my own image", false) { pickImage() },
            pill("Next", true) { show(1) }
        ), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(12f) })
        if (prefs.brandName.isNotBlank()) Net.main.post { doSearch() }
        return p
    }

    private fun doSearch() {
        val q = search.text.toString().trim()
        val token = ++searchToken
        if (q.length < 2) {
            results = emptyList()
            gridAdapter.notifyDataSetChanged()
            status.text = if (q.isEmpty()) "" else "Keep typing…"
            return
        }
        status.text = "Searching for $q…"
        Net.async({ BrandSearch.search(q) }) { found ->
            if (token != searchToken) return@async
            results = found ?: emptyList()
            status.text = when {
                found == null -> "Couldn't reach the internet. Check the unit's Wi-Fi or hotspot."
                results.isEmpty() -> "No logos found for \"$q\". Try another spelling, or use your own image."
                else -> "Tap your brand"
            }
            gridAdapter.notifyDataSetChanged()
            results.forEach { brand ->
                if (thumbs.containsKey(brand.id)) return@forEach
                thumbs[brand.id] = null
                Net.async({ BrandSearch.downloadLogo(brand, 160) }) { bmp ->
                    thumbs[brand.id] = bmp
                    gridAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun choose(brand: Brand) {
        selectedId = brand.id
        gridAdapter.notifyDataSetChanged()
        status.text = "Saving the ${brand.label} logo…"
        Net.async({ BrandSearch.downloadLogo(brand, 320) }) { bmp ->
            if (bmp == null) {
                status.text = "Couldn't download that logo. Try again or use your own image."
                return@async
            }
            Logo.save(this, bmp)
            prefs.brandName = brand.label
            status.text = "${brand.label} logo saved"
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(intent, "Choose your badge image"), REQ_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker found on this unit", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Uses the platform API available on Android 8.1")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Net.async({ Logo.decodeScaled(this, uri, 512) }) { bmp ->
            if (bmp == null) {
                Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_LONG).show()
                return@async
            }
            Logo.save(this, bmp)
            val typed = search.text.toString().trim()
            if (typed.isNotBlank()) prefs.brandName = typed
            selectedId = null
            gridAdapter.notifyDataSetChanged()
            status.text = "Your image is saved as the badge"
        }
    }

    /* -------------------------------------------------------- step 2 */

    private fun buildModelStep(): View {
        val p = page("Which model?", "Shown under the logo on your home screen.")
        p.addView(label("Car model", 12f, getColor(R.color.text_dim), R.font.manrope_semibold),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dpi(16f) })
        model = field("Your car model", prefs.carModel)
        p.addView(model, LinearLayout.LayoutParams(MATCH, dpi(54f)).apply { topMargin = dpi(6f) })
        p.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        p.addView(footer("You can change this later in Settings.",
            pill("Back", false) { show(0) },
            pill("Next", true) {
                prefs.carModel = model.text.toString().trim()
                show(2)
            }
        ))
        return p
    }

    /* -------------------------------------------------------- step 3 */

    private fun buildAccessStep(): View {
        val p = page("Phone and access", "A few switches so speed, maps, music and your phone all work.")
        accessList = vbox()
        p.addView(accessList, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dpi(14f) })
        p.addView(footer("Change any of this later in Settings.",
            pill("Back", false) { show(1) },
            pill("Finish", true) {
                prefs.setupDone = true
                finish()
            }
        ))
        return p
    }

    private fun renderAccess() {
        accessList.removeAllViews()
        val phoneOk = Apps.installed(this, prefs.phoneApp)
        accessRow("CarPlay / Android Auto app", if (phoneOk) "Found. Tapping your phone opens it." else "Not found. Choose the app that runs CarPlay.", phoneOk, "Choose") {
            Apps.pick(this, "Which app runs CarPlay / Android Auto?") { prefs.phoneApp = it.pkg; renderAccess() }
        }
        val loc = LocationHub.hasPermission(this)
        accessRow("Location", "For live speed, the mini map and weather", loc, "Allow") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
        val media = MediaHub.accessGranted(this)
        accessRow("Media access", "Shows what's playing and spots your phone. Turn on Car Vibe in the list.", media, "Open") {
            try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (e: Exception) {
                Toast.makeText(this, "Open Android settings › Apps › Special access › Notification access", Toast.LENGTH_LONG).show()
            }
        }
        accessRow("Make Car Vibe your home screen", "Choose Car Vibe, then Always", false, "Open") {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            }
        }
    }

    private fun accessRow(title: String, subtitle: String, done: Boolean, action: String, onClick: () -> Unit) {
        val row = hbox().apply {
            background = Ui.round(getColor(R.color.card), dp(20f), getColor(R.color.card_edge), dpi(1f))
            setPadding(dpi(20f), dpi(10f), dpi(14f), dpi(10f))
            minimumHeight = dpi(72f)
        }
        val texts = vbox()
        texts.addView(label(title, 17f, getColor(R.color.text), R.font.manrope_semibold))
        texts.addView(label(subtitle, 13f, getColor(R.color.text_dim)))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (done) row.addView(icon(R.drawable.ic_check, prefs.accent, 28f))
        else row.addView(pill(action, true, onClick))
        accessList.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dpi(8f) })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        renderAccess()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    companion object {
        private const val REQ_IMAGE = 11
        private const val REQ_LOCATION = 12
    }
}
