package com.elder.launcher.desktop

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.elder.launcher.R

/**
 * 桌面应用网格适配器：展示已添加的应用图标 + 末尾一个「添加」磁贴。
 */
class AppGridAdapter(
    private val context: Context,
    private val apps: List<String>
) : BaseAdapter() {

    override fun getCount(): Int = apps.size + 1

    override fun getItem(position: Int): Any? =
        if (position < apps.size) apps[position] else null

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (position == apps.size) return addTile(convertView, parent)
        return appTile(apps[position], convertView, parent)
    }

    private fun appTile(pkg: String, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_desktop_app, parent, false)
        val icon = view.findViewById<ImageView>(R.id.img_icon)
        val label = view.findViewById<TextView>(R.id.tv_label)
        try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            icon.setImageDrawable(ai.loadIcon(context.packageManager))
            label.text = ai.loadLabel(context.packageManager)
        } catch (_: Exception) {
            label.text = pkg
        }
        return view
    }

    private fun addTile(convertView: View?, parent: ViewGroup): View =
        convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_desktop_add, parent, false)
}
