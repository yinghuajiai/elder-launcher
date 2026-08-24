package com.elder.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * 长辈桌面无障碍服务：让视力不佳的长辈真正"用得上"手机。
 *
 * 核心能力：
 *  1. 通知朗读 —— 收到通知时用系统 TTS 读出标题与内容；
 *  2. 点读 —— 点按/聚焦控件时读出其文本或无障碍描述；
 *  3. 前台应用监听 —— 记录当前前台包名，供守护模块使用。
 */
class ElderAccessibilityService : AccessibilityService() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        initTts()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentForegroundPackage = event.packageName?.toString()
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                if (AccessibilitySettings.readNotifications(this)) speakNotification(event)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (AccessibilitySettings.tapToRead(this)) speakNode(event.source)
            }
        }
    }

    /** 读出一条通知（标题 + 正文）。 */
    private fun speakNotification(event: AccessibilityEvent) {
        val extras = (event.parcelableData as? Notification)?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val content = listOf(title, text).filter { it.isNotEmpty() }
        if (content.isNotEmpty()) speak(content.joinToString("，"))
    }

    /** 读出一个控件的文本或无障碍描述。 */
    private fun speakNode(node: AccessibilityNodeInfo?) {
        val text = node?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: node?.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        speak(text)
    }

    private fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "elder_speak")
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = configureLanguage()
            }
        }
    }

    /** 依次尝试中文各区域与系统默认语言，直到找到可用的引擎。 */
    private fun configureLanguage(): Boolean {
        val t = tts ?: return false
        val candidates = listOf(
            Locale.CHINA,
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINESE,
            Locale.getDefault()
        )
        for (locale in candidates) {
            when (t.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> continue
                else -> return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        tts?.stop()
    }

    override fun onDestroy() {
        isRunning = false
        currentForegroundPackage = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        /** 服务是否正在运行。 */
        @Volatile
        var isRunning: Boolean = false

        /** 当前前台应用包名（守护模块可用）。 */
        @Volatile
        var currentForegroundPackage: String? = null
    }
}
