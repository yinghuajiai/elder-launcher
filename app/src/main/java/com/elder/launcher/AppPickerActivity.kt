package com.elder.launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.AppPickerAdapter
import com.elder.launcher.desktop.DesktopApps

/**
 * 应用选择器：搜索/浏览本机可启动应用，点击添加到桌面。
 * 列表 = 系统应用抽屉里的应用（含用户下载的应用 + 设置/相册/相机/电话等可启动系统应用），
 * 纯系统后台组件（系统 UI/键盘等无桌面图标者）天然不在其中。
 */
class AppPickerActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var searchInput: EditText
    private lateinit var adapter: AppPickerAdapter
    private var allApps: List<ResolveInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        listView = findViewById(R.id.list_apps)
        searchInput = findViewById(R.id.input_search)

        allApps = loadApps()
        adapter = AppPickerAdapter(this, allApps.toMutableList())
        listView.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val info = adapter.getItem(position) as ResolveInfo
            val pkg = info.activityInfo.packageName
            if (DesktopApps.list(this).contains(pkg)) {
                Toast.makeText(this, "该应用已在桌面", Toast.LENGTH_SHORT).show()
            } else {
                DesktopApps.add(this, pkg)
                finish()
            }
        }
    }

    private fun loadApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString() }
    }

    private fun applyFilter(query: String) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) allApps
        else allApps.filter { it.loadLabel(packageManager).toString().contains(q, ignoreCase = true) }
        adapter.submit(filtered)
    }
}
