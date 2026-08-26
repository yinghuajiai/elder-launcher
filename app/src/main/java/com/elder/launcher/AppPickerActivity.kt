package com.elder.launcher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.AppPickerAdapter
import com.elder.launcher.desktop.DesktopApps
import com.elder.launcher.desktop.LetterIndexBar
import com.elder.launcher.desktop.PinyinUtil
import java.text.Collator
import java.util.Locale

/**
 * 应用选择器：搜索/浏览本机可启动应用，点击添加到桌面。
 * 列表按拼音排序（中文按拼音），右侧 A-Z 索引条点击/滑动可快速定位。
 * 列表 = 系统应用抽屉里的应用，纯系统后台组件天然不在其中。
 */
class AppPickerActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var searchInput: EditText
    private lateinit var adapter: AppPickerAdapter
    private lateinit var indexBar: LetterIndexBar
    private lateinit var bubble: TextView

    private var allApps: List<ResolveInfo> = emptyList()
    private val letterToIndex = LinkedHashMap<Char, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        listView = findViewById(R.id.list_apps)
        searchInput = findViewById(R.id.input_search)
        indexBar = findViewById(R.id.index_bar)
        bubble = findViewById(R.id.tv_letter_bubble)

        allApps = loadApps()
        adapter = AppPickerAdapter(this, allApps.toMutableList())
        listView.adapter = adapter

        setupIndexBar()

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
        val collator = Collator.getInstance(Locale.CHINA)
        return packageManager.queryIntentActivities(intent, 0)
            .sortedWith { a, b ->
                collator.compare(a.loadLabel(packageManager).toString(), b.loadLabel(packageManager).toString())
            }
    }

    private fun setupIndexBar() {
        letterToIndex.clear()
        allApps.forEachIndexed { index, info ->
            val letter = PinyinUtil.firstLetter(info.loadLabel(packageManager).toString())
            if (!letterToIndex.containsKey(letter)) letterToIndex[letter] = index
        }
        val letters = letterToIndex.keys.sortedWith { a, b ->
            when {
                a == '#' -> 1
                b == '#' -> -1
                else -> a.compareTo(b)
            }
        }
        indexBar.letters = letters
        indexBar.onLetterSelected = { letter ->
            val index = letterToIndex[letter]
            if (index != null) {
                bubble.text = letter.toString()
                bubble.visibility = View.VISIBLE
                listView.setSelection(index)
            }
        }
        indexBar.onTouchEnded = { bubble.visibility = View.GONE }
    }

    private fun applyFilter(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            indexBar.visibility = View.VISIBLE
            adapter.submit(allApps)
        } else {
            indexBar.visibility = View.GONE
            val filtered = allApps.filter {
                it.loadLabel(packageManager).toString().contains(q, ignoreCase = true)
            }
            adapter.submit(filtered)
        }
    }
}
