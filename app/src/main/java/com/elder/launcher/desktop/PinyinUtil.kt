package com.elder.launcher.desktop

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/** 取字符串首字母用于 A-Z 索引：拉丁字母取大写，中文取拼音首字母，其余归「#」。 */
object PinyinUtil {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.UPPERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    fun firstLetter(text: String): Char {
        val c = text.firstOrNull() ?: return '#'
        if (c in 'a'..'z') return c.uppercaseChar()
        if (c in 'A'..'Z') return c
        if (c.code < 0x4E00 || c.code > 0x9FFF) return '#'
        return try {
            val arr = PinyinHelper.toHanyuPinyinStringArray(c, format)
            if (arr != null && arr.isNotEmpty() && arr[0].isNotEmpty()) arr[0][0] else '#'
        } catch (_: Exception) {
            '#'
        }
    }
}
