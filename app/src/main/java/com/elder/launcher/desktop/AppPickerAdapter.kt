package com.elder.launcher.desktop

import android.content.Context
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.elder.launcher.R

/**
 * 应用选择器列表适配器：展示可启动的应用（图标 + 名称），支持搜索过滤。
 */
class AppPickerAdapter(
    private val context: Context,
    private val items: MutableList<ResolveInfo>
) : BaseAdapter() {

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_app_picker, parent, false)
        val info = items[position]
        view.findViewById<ImageView>(R.id.img_icon).setImageDrawable(info.loadIcon(context.packageManager))
        view.findViewById<TextView>(R.id.tv_label).text = info.loadLabel(context.packageManager)
        return view
    }

    fun submit(newItems: List<ResolveInfo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
