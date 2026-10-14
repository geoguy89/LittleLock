package com.littlelock.app.ui.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.R
import com.littlelock.app.data.local.AllowedAppEntity
import com.littlelock.app.data.local.AllowedAppDao
import com.littlelock.app.data.local.SettingsRepository
import com.littlelock.app.ui.main.MainActivity
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Child-facing launcher activity.
 * Shows only allowed apps in a grid layout.
 * Long press triggers parent authentication to exit.
 */
class KidsLauncherActivity : AppCompatActivity(), KoinComponent {

    private val allowedAppDao: AllowedAppDao by inject()
    private val settingsRepository: SettingsRepository by inject()

    private lateinit var gridApps: GridView
    private lateinit var adapter: AppGridAdapter
    private val appList = mutableListOf<AppItem>()

    // For detecting long press to exit
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kids_launcher)

        gridApps = findViewById(R.id.gridApps)

        // Load allowed apps
        lifecycleScope.launch {
            val allowedApps = allowedAppDao.getAllowedApps()
            appList.clear()
            appList.addAll(allowedApps.map { it.toAppItem(packageManager) })
            adapter = AppGridAdapter(appList)
            gridApps.adapter = adapter
        }

        // Set up click listener for apps
        gridApps.setOnItemClickListener { _, _, position, _ ->
            val app = appList[position]
            launchApp(app.packageName)
        }

        // Set up long press to exit (requires PIN)
        gridApps.setOnLongClickListener {
            // Show PIN prompt to exit
            exitWithAuthentication()
            true
        }

        // Prevent switching to other apps
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            // App not available, ignore
        }
    }

    private fun exitWithAuthentication() {
        // In a full implementation, this would show a PIN dialog
        // For now, navigate back to main activity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Re-lock when returning
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    /**
     * Data class for app items displayed in the grid.
     */
    data class AppItem(
        val packageName: String,
        val appName: String,
        val appIcon: android.graphics.drawable.Drawable?
    )

    /**
     * Adapter for the app grid.
     */
    inner class AppGridAdapter(
        private val apps: List<AppItem>
    ) : BaseAdapter() {

        override fun getCount(): Int = apps.size

        override fun getItem(position: Int): AppItem = apps[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(
                android.R.layout.simple_list_item_2,
                parent,
                false
            )

            val app = apps[position]
            val iconView = view.findViewById<ImageView>(android.R.id.icon)
            val textView = view.findViewById<TextView>(android.R.id.text1)

            iconView.setImageDrawable(app.appIcon)
            textView.text = app.appName
            textView.maxLines = 2

            return view
        }
    }

    /**
     * Extension to convert AllowedAppEntity to AppItem.
     */
    private fun AllowedAppEntity.toAppItem(pm: PackageManager): AppItem {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            AppItem(
                packageName = packageName,
                appName = if (appName.isNotBlank()) appName else info.loadLabel(pm).toString(),
                appIcon = info.loadIcon(pm)
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppItem(packageName, appName, null)
        }
    }
}
