package com.littlelock.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Parent screen: the order of one child's apps and where each row starts. The apps are listed the way they appear on the home
 * screen; the arrows move an app, and "New row" starts a row at that app (so 3 apps can sit on the top row and 2 below).
 * Saved on every change. Without any row starts the apps simply fill the screen row by row, as they always did.
 */
class ArrangeAppsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "profile_id"
    }

    private class Entry(val app: AppInfo, var newRow: Boolean)

    private var pid = ""
    private val entries = ArrayList<Entry>()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        pid = intent.getStringExtra(EXTRA_ID).orEmpty()
        val profile = Profiles.get(this, pid)
        Accent.apply(this, profile?.accent)
        super.onCreate(savedInstanceState)
        title = getString(R.string.profile_arrange)
        if (profile == null) {
            finish()
            return
        }
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.littlelock_background))
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.arrange_explain)
            setTextColor(getColor(R.color.littlelock_text_secondary))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = (8 * d).toInt() })
        root.addView(Button(this).apply {
            setText(R.string.arrange_reset)
            setOnClickListener {
                Profiles.update(this@ArrangeAppsActivity, pid) { it.copy(order = emptyList()) }
                load()
            }
        })
        root.addView(Button(this).apply {
            setText(R.string.settings_done)
            setOnClickListener { finish() }
        })
        setContentView(root)
        root.applySystemBarInsets()
    }

    override fun onResume() {
        super.onResume()
        if (Profiles.get(this, pid) == null) return
        if (!ParentSession.isUnlocked()) {
            startActivity(Intent(this, PinLockActivity::class.java).putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER))
            finish()
            return
        }
        ParentSession.unlock()
        load()
    }

    /** The child's apps as they are shown now, with the row starts the parent chose. */
    private fun load() {
        val profile = Profiles.get(this, pid) ?: return
        val all = AppFilter.launchableApps(packageManager)
        val rows = Profiles.arrange(AppFilter.visibleApps(all, profile.allowed, packageName), profile.order)
        entries.clear()
        val custom = Profiles.ROW_BREAK in profile.order
        rows.forEach { row -> row.forEachIndexed { i, app -> entries.add(Entry(app, custom && i == 0)) } }
        render()
    }

    private fun save() {
        val order = ArrayList<String>()
        entries.forEachIndexed { i, e ->
            if (i > 0 && e.newRow) order.add(Profiles.ROW_BREAK)
            order.add(e.app.packageName)
        }
        Profiles.update(this, pid) { it.copy(order = order) }
    }

    private fun move(from: Int, to: Int) {
        if (to !in entries.indices) return
        val starts = entries.map { it.newRow }                 // where the rows start stays put; the apps move through them
        val e = entries.removeAt(from)
        entries.add(to, e)
        entries.forEachIndexed { i, x -> x.newRow = starts[i] }
        save()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val d = resources.displayMetrics.density
        var rowNo = 0
        entries.forEachIndexed { i, e ->
            if (i == 0 || e.newRow) {
                rowNo++
                list.addView(TextView(this).apply {
                    text = getString(R.string.arrange_row, rowNo)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(getColor(R.color.littlelock_text_primary))
                    setPadding(0, (12 * d).toInt(), 0, (2 * d).toInt())
                })
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(ImageView(this).apply {
                try {
                    setImageDrawable(packageManager.getApplicationIcon(e.app.packageName))
                } catch (ex: Exception) {
                    setImageResource(R.drawable.ic_app_icon_1)
                }
            }, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply { marginEnd = (10 * d).toInt() })
            row.addView(TextView(this).apply {
                text = e.app.label
                textSize = 16f
                setTextColor(getColor(R.color.littlelock_text_primary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(CheckBox(this).apply {
                text = getString(R.string.arrange_new_row)
                isChecked = i == 0 || e.newRow
                isEnabled = i > 0
                setOnClickListener {
                    e.newRow = isChecked
                    save()
                    render()
                }
            })
            row.addView(small("▲", i > 0) { move(i, i - 1) })
            row.addView(small("▼", i < entries.size - 1) { move(i, i + 1) })
            list.addView(row)
        }
    }

    private fun small(text: String, enabled: Boolean, onClick: () -> Unit): View = Button(this).apply {
        this.text = text
        minWidth = 0
        minimumWidth = (48 * resources.displayMetrics.density).toInt()
        isEnabled = enabled
        setOnClickListener { onClick() }
    }
}
