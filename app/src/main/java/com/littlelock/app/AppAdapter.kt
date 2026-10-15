package com.littlelock.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

/**
 * Adapter for displaying installed apps in a GridView.
 */
class AppAdapter(
    context: Context,
    private val apps: List<AppInfo>
) : ArrayAdapter<AppInfo>(context, R.layout.item_app, apps) {

    private val colorResIds = intArrayOf(
        R.drawable.ic_app_icon_1,
        R.drawable.ic_app_icon_2,
        R.drawable.ic_app_icon_3,
        R.drawable.ic_app_icon_4,
        R.drawable.ic_app_icon_5,
        R.drawable.ic_app_icon_6,
        R.drawable.ic_app_icon_7,
        R.drawable.ic_app_icon_8
    )

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]

        val ivAppIcon = view.findViewById<ImageView>(R.id.ivAppIcon)
        val tvAppName = view.findViewById<TextView>(R.id.tvAppName)

        ivAppIcon.setImageResource(colorResIds[position % colorResIds.size])
        tvAppName.text = app.label

        return view
    }
}
