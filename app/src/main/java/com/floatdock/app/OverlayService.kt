package com.floatdock.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*

class OverlayService : Service() {
    companion object { const val EXTRA_PKG = "pkg" }

    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var bubble: ImageView
    private lateinit var menu: LinearLayout
    private lateinit var maxBtn: Button
    private lateinit var lp: WindowManager.LayoutParams
    private var selected: String? = null
    private var maximized = false
    private var tiny = false
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = "floatdock"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "FloatDock", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch).setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("FloatDock is running").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        buildUi()
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        i?.getStringExtra(EXTRA_PKG)?.let {
            selected = it; maximized = false; updateMax(); launch(1)
        }
        return START_STICKY
    }

    private fun btn(t: String, c: Int, a: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE); textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(c) }
        layoutParams = LinearLayout.LayoutParams(dp(150), dp(52)).apply { topMargin = dp(8) }
        setOnClickListener { a() }
    }

    private fun buildUi() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bubble = ImageView(this).apply { setImageResource(R.drawable.ic_bubble) }
        root.addView(bubble, dp(56), dp(56))
        menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        maxBtn = btn("Maximize", 0xFF2F6BFF.toInt()) { toggleSize() }
        menu.addView(btn("Close", 0xFFE5484D.toInt()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() })
        menu.addView(btn("Launch App", 0xFF16A34A.toInt()) {
            menu.visibility = View.GONE
            startActivity(Intent(this, PickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        menu.addView(maxBtn)
        root.addView(menu)

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(160) }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    if (moved) { lp.x = ox + dx; lp.y = oy + dy; wm.updateViewLayout(root, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onBubbleTap()
            }
            true
        }
        wm.addView(root, lp)
    }

    private fun onBubbleTap() {
        if (tiny) setTiny(false)
        menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun setTiny(t: Boolean) {
        tiny = t
        val s = if (t) dp(30) else dp(56)
        bubble.layoutParams = LinearLayout.LayoutParams(s, s)
        bubble.alpha = if (t) 0.55f else 1f
        if (t) menu.visibility = View.GONE
        wm.updateViewLayout(root, lp)
    }

    private fun updateMax() { maxBtn.text = if (maximized) "Minimize" else "Maximize" }

    private fun toggleSize() {
        if (selected == null) {
            menu.visibility = View.GONE
            startActivity(Intent(this, PickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!maximized) { maximized = true; setTiny(false); launch(2) }
        else { maximized = false; setTiny(true); launch(0) }
        updateMax()
        menu.visibility = View.GONE
    }

    // mode: 0 = small window, 1 = medium, 2 = large
    private fun launch(mode: Int) {
        val pkg = selected ?: return
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val r = when (mode) {
            0 -> Rect((w * .45).toInt(), (h * .08).toInt(), (w * .97).toInt(), (h * .40).toInt())
            1 -> Rect((w * .10).toInt(), (h * .15).toInt(), (w * .90).toInt(), (h * .80).toInt())
            else -> Rect((w * .04).toInt(), (h * .06).toInt(), (w * .96).toInt(), (h * .94).toInt())
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val o = ActivityOptions.makeBasic().setLaunchBounds(r)
        try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(o, 5) // freeform
        } catch (_: Throwable) {}
        try { startActivity(i, o.toBundle()) } catch (_: Throwable) { startActivity(i) }
        menu.visibility = View.GONE
    }

    override fun onDestroy() {
        try { wm.removeView(root) } catch (_: Throwable) {}
        super.onDestroy()
    }
}
