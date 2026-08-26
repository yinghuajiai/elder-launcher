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
 * 整理模式下隐藏「添加」磁贴、显示 × 删除角标，并支持拖动排序。
 */
class AppGridAdapter(
    private val context: Context,
    private val apps: MutableList<String>
) : BaseAdapter() {

    var editing = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** 点击 × 删除角标回调，参数为应用在列表中的位置。 */
    var onDelete: ((Int) -> Unit)? = null

    private val TYPE_APP = 0
    private val TYPE_ADD = 1

    override fun getCount(): Int = if (editing) apps.size else apps.size + 1

    override fun getItem(position: Int): Any? =
        if (position < apps.size) apps[position] else null

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int =
        if (!editing && position == apps.size) TYPE_ADD else TYPE_APP

    override fun getViewTypeCount(): Int = 2

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (!editing && position == apps.size) return addTile(convertView, parent)
        return appTile(position, convertView, parent)
    }

    private fun appTile(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_desktop_app, parent, false)
        val icon = view.findViewById<ImageView>(R.id.img_icon)
        val label = view.findViewById<TextView>(R.id.tv_label)
        val del = view.findViewById<TextView>(R.id.btn_delete)

        val pkg = apps[position]
        try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            icon.setImageDrawable(ai.loadIcon(context.packageManager))
            label.text = ai.loadLabel(context.packageManager)
        } catch (_: Exception) {
            label.text = pkg
        }

        if (editing) {
            del.visibility = View.VISIBLE
            del.setOnClickListener { onDelete?.invoke(position) }
        } else {
            del.visibility = View.GONE
        }
        return view
    }

    private fun addTile(convertView: View?, parent: ViewGroup): View =
        convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_desktop_add, parent, false)
}
