package com.littlelock.app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.littlelock.app.databinding.ActivityAllowedAppsBinding

/**
 * Parent screen: tick the apps the child may use. Saved on every tick. Apps that let a child change the device or
 * install apps are marked, so they aren't allowed by accident.
 */
class AllowedAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllowedAppsBinding
    private var apps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllowedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        title = getString(R.string.settings_allowed_apps)

        apps = AppFilter.choosableApps(AppFilter.launchableApps(packageManager), packageName)
        val labels = apps.map {
            if (it.packageName in AppFilter.RISKY_PACKAGES) getString(R.string.allowed_apps_risky, it.label) else it.label
        }
        binding.listApps.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
        val allowed = Preferences.getAllowedApps(this)
        apps.forEachIndexed { i, app -> binding.listApps.setItemChecked(i, app.packageName in allowed) }
        binding.listApps.setOnItemClickListener { _, _, _, _ -> save() }
        binding.btnDone.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (!ParentSession.isUnlocked()) {
            startActivity(Intent(this, PinLockActivity::class.java).putExtra(PinLockActivity.EXTRA_MODE, PinLockActivity.MODE_ENTER))
            finish()
            return
        }
        ParentSession.unlock()
    }

    private fun save() {
        val checked = binding.listApps.checkedItemPositions
        val chosen = apps.filterIndexed { i, _ -> checked.get(i) }.map { it.packageName }.toSet()
        Preferences.setAllowedApps(this, chosen)
    }
}
