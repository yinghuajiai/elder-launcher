package com.elder.launcher

import android.os.Bundle
import com.elder.launcher.base.BaseActivity

/**
 * 基础桌面（HOME）：引导流程完成后的主界面。
 * 当前为空桌面，后续在此承载联系人卡片、快捷方式等。
 */
class DesktopActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)
    }

    override fun onBackPressed() {
        // 桌面作为主页，不响应返回键
    }
}
