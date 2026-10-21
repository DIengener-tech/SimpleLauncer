package com.example.simplelauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

data class AppItem(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : Activity() {

    private companion object {
        const val COLUMNS = 4
        const val ROWS = 5
        const val DOCK_SIZE = 4
        const val HOME_PAGE = 1   // сторінка 0 — пошук, як у iOS 6
    }

    private lateinit var pager: ViewPager
    private lateinit var dock: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var searchPage: View
    private lateinit var searchBox: EditText
    private lateinit var searchAdapter: AppAdapter

    private var allApps: List<AppItem> = emptyList()
    private val iconCache = HashMap<String, Drawable>()
    private var pageCount = 0

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            iconCache.clear()
            loadApps()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pager = findViewById(R.id.pager)
        dock = findViewById(R.id.dock)
        dots = findViewById(R.id.dots)

        // Сторінка пошуку (Spotlight) на «льняному» фоні
        searchPage = LayoutInflater.from(this).inflate(R.layout.page_search, null)
        searchPage.background = IconStyler.linen(this, 0xFF3A3F48.toInt())
        searchBox = searchPage.findViewById(R.id.search)
        searchAdapter = AppAdapter(::launch, ::showMenu)
        searchPage.findViewById<RecyclerView>(R.id.results).apply {
            layoutManager = GridLayoutManager(this@MainActivity, COLUMNS)
            adapter = searchAdapter
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applySearch()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                if (position != 0) hideKeyboard()
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)

        loadApps()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // Кнопка «Додому» повертає на першу сторінку з іконками
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        goHome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()

    private fun goHome() {
        hideKeyboard()
        searchBox.setText("")
        if (pager.currentItem != HOME_PAGE) pager.setCurrentItem(HOME_PAGE, true)
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchBox.windowToken, 0)
        searchBox.clearFocus()
    }

    // ---------- Застосунки ----------

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val pkg = it.activityInfo.packageName
                val icon = iconCache.getOrPut(pkg) { IconStyler.style(this, it.loadIcon(packageManager)) }
                AppItem(it.loadLabel(packageManager).toString(), pkg, icon)
            }
            .sortedBy { it.label.lowercase() }

        if (!prefs.contains("favs")) {
            saveFavorites(defaultFavorites().filter { p -> allApps.any { it.packageName == p } }.take(DOCK_SIZE))
        }

        val pages = mutableListOf<View>(searchPage)
        val chunks = allApps.chunked(COLUMNS * ROWS)
        if (chunks.isEmpty()) pages.add(buildPage(emptyList())) else chunks.forEach { pages.add(buildPage(it)) }
        pageCount = pages.size

        val keep = if (pager.adapter == null) HOME_PAGE else pager.currentItem.coerceAtMost(pageCount - 1)
        pager.adapter = PagesAdapter(pages)
        pager.setCurrentItem(keep, false)
        updateDots(keep)

        refreshDock()
        applySearch()
    }

    private fun buildPage(items: List<AppItem>): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(12), dp(8), 0)
        }
        for (r in 0 until ROWS) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (c in 0 until COLUMNS) {
                val idx = r * COLUMNS + c
                val cell: View = if (idx < items.size) makeCell(items[idx], row) else View(this)
                cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                row.addView(cell)
            }
            page.addView(row)
        }
        return page
    }

    private fun makeCell(app: AppItem, parent: ViewGroup): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_app, parent, false)
        v.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
        v.findViewById<TextView>(R.id.label).text = app.label
        v.setOnClickListener { launch(app) }
        v.setOnLongClickListener { showMenu(it, app); true }
        return v
    }

    private fun applySearch() {
        val q = searchBox.text.toString().trim().lowercase()
        searchAdapter.submit(if (q.isEmpty()) emptyList() else allApps.filter { it.label.lowercase().contains(q) })
    }

    // ---------- Крапки сторінок ----------

    private fun updateDots(selected: Int) {
        dots.removeAllViews()
        for (i in 0 until pageCount) {
            val d = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == selected) Color.WHITE else 0x66FFFFFF)
            }
            val dot = View(this).apply {
                background = d
                layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { setMargins(dp(4), 0, dp(4), 0) }
            }
            dots.addView(dot)
        }
    }

    // ---------- Док ----------

    private fun favorites(): MutableList<String> =
        (prefs.getString("favs", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()

    private fun saveFavorites(list: List<String>) {
        prefs.edit().putString("favs", list.joinToString(",")).apply()
    }

    private fun defaultFavorites(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        return intents.mapNotNull {
            packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.distinct()
    }

    private fun refreshDock() {
        dock.removeAllViews()
        val apps = favorites().mapNotNull { p -> allApps.find { it.packageName == p } }
        for (i in 0 until DOCK_SIZE) {
            val app = apps.getOrNull(i)
            val v: View = if (app != null) {
                ImageView(this).apply {
                    setImageDrawable(app.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setOnClickListener { launch(app) }
                    setOnLongClickListener { showMenu(it, app); true }
                }
            } else View(this)
            v.layoutParams = LinearLayout.LayoutParams(0, dp(68), 1f)
            dock.addView(v)
        }
    }

    // ---------- Дії ----------

    private fun launch(app: AppItem) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    private fun showMenu(view: View, app: AppItem) {
        val favs = favorites()
        val isFav = app.packageName in favs
        PopupMenu(this, view).apply {
            menu.add(0, 1, 0, if (isFav) "Прибрати з дока" else "Додати в док")
            menu.add(0, 2, 1, "Про застосунок")
            menu.add(0, 3, 2, "Видалити")
            setOnMenuItemClickListener { item ->
                val uri = Uri.parse("package:${app.packageName}")
                when (item.itemId) {
                    1 -> {
                        if (isFav) {
                            favs.remove(app.packageName)
                        } else if (favs.size < DOCK_SIZE) {
                            favs.add(app.packageName)
                        } else {
                            Toast.makeText(this@MainActivity, "У доці максимум $DOCK_SIZE застосунки", Toast.LENGTH_SHORT).show()
                        }
                        saveFavorites(favs)
                        refreshDock()
                    }
                    2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    3 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
                true
            }
            show()
        }
    }
}

class PagesAdapter(private val pages: List<View>) : PagerAdapter() {
    override fun getCount() = pages.size
    override fun isViewFromObject(view: View, obj: Any) = view === obj
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val v = pages[position]
        container.addView(v)
        return v
    }
    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }
}

class AppAdapter(
    private val onClick: (AppItem) -> Unit,
    private val onLongClick: (View, AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    private var items: List<AppItem> = emptyList()

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener { onLongClick(it, app); true }
    }

    override fun getItemCount() = items.size
}
