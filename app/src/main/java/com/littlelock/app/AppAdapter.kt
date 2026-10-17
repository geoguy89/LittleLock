package com.littlelock.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

/**
 * Adapter for displaying installed apps in a GridView. The tile size comes from the parent's choice
 * ([Look.TILES]); on a wallpaper the names are drawn in white with a shadow so they stay readable.
 */
class AppAdapter(
    context: Context,
    private val apps: List<AppInfo>,
    private val lightText: Boolean = false
) : ArrayAdapter<AppInfo>(context, R.layout.item_app, apps) {

    private val tile = Look.tile(Preferences.getTileSize(context))
    private val density = context.resources.displayMetrics.density

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]

        val ivAppIcon = view.findViewById<ImageView>(R.id.ivAppIcon)
        val tvAppName = view.findViewById<TextView>(R.id.tvAppName)

        val px = (tile.iconDp * density).toInt()
        ivAppIcon.layoutParams = ivAppIcon.layoutParams.apply {
            width = px
            height = px
        }
        tvAppName.setTextSize(TypedValue.COMPLEX_UNIT_SP, tile.textSp.toFloat())
        if (lightText) {
            tvAppName.setTextColor(Color.WHITE)
            tvAppName.setShadowLayer(4f, 0f, 1f, Color.BLACK)
        } else {
            tvAppName.setTextColor(context.getColor(R.color.littlelock_text_primary))
            tvAppName.setShadowLayer(0f, 0f, 0f, 0)
        }

        try {
            ivAppIcon.setImageDrawable(context.packageManager.getApplicationIcon(app.packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            ivAppIcon.setImageResource(R.drawable.ic_app_icon_1)
        }
        tvAppName.text = app.label
        view.contentDescription = app.label

        return view
    }
}
