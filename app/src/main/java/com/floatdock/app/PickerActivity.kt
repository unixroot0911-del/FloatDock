package com.floatdock.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class PickerActivity : Activity() {
    data class Item(val label: String, val pkg: String, val icon: Drawable)
    private var all = listOf<Item>()
    private var shown = listOf<Item>()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(q, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .map { Item(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        shown = all

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val search = EditText(this).apply { hint = "Search apps & games"; setSingleLine() }
        val list = ListView(this)
        root.addView(search, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(list, ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.65).toInt())
        setContentView(root)

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val row = LinearLayout(this@PickerActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val it = shown[p]
                row.addView(ImageView(this@PickerActivity).apply { setImageDrawable(it.icon) }, dp(44), dp(44))
                row.addView(TextView(this@PickerActivity).apply { text = it.label; textSize = 16f; setPadding(dp(14), 0, 0, 0) })
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, p, _ ->
            startService(Intent(this, OverlayService::class.java).putExtra(OverlayService.EXTRA_PKG, shown[p].pkg))
            finish()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val k = s.toString().trim().lowercase()
                shown = if (k.isEmpty()) all else all.filter { it.label.lowercase().contains(k) }
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }
}
