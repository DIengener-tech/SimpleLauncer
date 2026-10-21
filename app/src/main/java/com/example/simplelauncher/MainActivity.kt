package com.example.simplelauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class AppItem(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : Activity() {

    private lateinit var search: EditText
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppItem> = emptyList()

    // Оновлюємо список, коли застосунок встановлено або видалено
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        search = findViewById(R.id.search)
        adapter = AppAdapter(::launch, ::showMenu)

        findViewById<RecyclerView>(R.id.apps).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = this@MainActivity.adapter
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
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

    // Натискання кнопки «Додому» — очистити пошук і сховати клавіатуру
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        search.setText("")
        search.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        findViewById<RecyclerView>(R.id.apps).scrollToPosition(0)
    }

    // На домашньому екрані кнопка «Назад» нічого не робить
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppItem(
                    it.loadLabel(packageManager).toString(),
                    it.activityInfo.packageName,
                    it.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
        applyFilter()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        adapter.submit(if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) })
    }

    private fun launch(app: AppItem) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    private fun showMenu(view: View, app: AppItem) {
        PopupMenu(this, view).apply {
            menu.add(0, 1, 0, "Про застосунок")
            menu.add(0, 2, 1, "Видалити")
            setOnMenuItemClickListener {
                val uri = Uri.parse("package:${app.packageName}")
                when (it.itemId) {
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
                true
            }
            show()
        }
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
