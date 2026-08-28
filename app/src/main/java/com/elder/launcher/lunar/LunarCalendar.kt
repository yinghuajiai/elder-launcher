package com.elder.launcher.lunar

import java.util.Calendar

/**
 * 公历 ↔ 农历转换（1900–2100）。
 * 数据与算法源自香港天文台农历数据、jjonline/calendar.js（MIT）。
 * 只做「年干支 + 生肖 + 月 + 日」的展示，不含节气。
 */
object LunarCalendar {

    /** 农历 1900–2100 闰月/大小月信息表（每年 4 字节）。 */
    private val lunarInfo = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, //1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, //1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, //1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, //1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, //1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, //1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, //1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, //1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, //1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, //1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, //2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, //2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, //2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, //2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, //2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, //2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, //2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, //2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, //2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, //2090-2099
        0x0d520 //2100
    )

    private val Gan = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val Zhi = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val Animals = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
    private val monthNames = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val dayPrefix = arrayOf("初", "十", "廿", "卅")
    private val dayNumbers = arrayOf("日", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    private fun lYearDays(y: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (lunarInfo[y - 1900] and i != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    private fun leapMonth(y: Int): Int = lunarInfo[y - 1900] and 0xf

    private fun leapDays(y: Int): Int =
        if (leapMonth(y) != 0) (if (lunarInfo[y - 1900] and 0x10000 != 0) 30 else 29) else 0

    private fun monthDays(y: Int, m: Int): Int =
        if (lunarInfo[y - 1900] and (0x10000 shr m) != 0) 30 else 29

    /** 公历年月日距离 1970-01-01 的天数（与闰年/大小月无关的纯算术）。 */
    private fun daysFromCivil(y0: Int, m0: Int, d0: Int): Long {
        var y = y0
        if (m0 <= 2) y -= 1
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m0 + (if (m0 > 2) -3 else 9)) + 2) / 5 + d0 - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    /** 公历 → 农历。 */
    fun solarToLunar(y: Int, m: Int, d: Int): LunarDate {
        var offset = (daysFromCivil(y, m, d) - daysFromCivil(1900, 1, 31)).toInt()
        var temp = 0
        var i = 1900
        while (i < 2101 && offset > 0) {
            temp = lYearDays(i)
            offset -= temp
            i++
        }
        if (offset < 0) {
            offset += temp
            i--
        }
        val year = i
        val leap = leapMonth(i)
        var isLeap = false
        var j = 1
        while (j < 13 && offset > 0) {
            if (leap > 0 && j == leap + 1 && !isLeap) {
                j--
                isLeap = true
                temp = leapDays(year)
            } else {
                temp = monthDays(year, j)
            }
            if (isLeap && j == leap + 1) isLeap = false
            offset -= temp
            j++
        }
        if (offset == 0 && leap > 0 && j == leap + 1) {
            if (isLeap) isLeap = false else {
                isLeap = true
                j--
            }
        }
        if (offset < 0) {
            offset += temp
            j--
        }
        return LunarDate(year, j, offset + 1, isLeap)
    }

    /** 农历年份 → 干支（如 丙午）。 */
    fun ganZhiYear(lunarYear: Int): String {
        var ganKey = (lunarYear - 3) % 10
        var zhiKey = (lunarYear - 3) % 12
        if (ganKey == 0) ganKey = 10
        if (zhiKey == 0) zhiKey = 12
        return Gan[ganKey - 1] + Zhi[zhiKey - 1]
    }

    /** 农历年份 → 生肖（如 马）。 */
    fun animal(lunarYear: Int): String = Animals[(lunarYear - 4) % 12]

    private fun monthName(m: Int, isLeap: Boolean): String =
        (if (isLeap) "闰" else "") + monthNames[m - 1] + "月"

    private fun dayName(d: Int): String = when (d) {
        10 -> "初十"
        20 -> "二十"
        30 -> "三十"
        else -> dayPrefix[d / 10] + dayNumbers[d % 10]
    }

    /** 完整农历文案，如「丙午马年七月十四」。 */
    fun fullText(y: Int, m: Int, d: Int): String {
        val lunar = solarToLunar(y, m, d)
        return ganZhiYear(lunar.year) + animal(lunar.year) + "年" +
                monthName(lunar.month, lunar.isLeap) + dayName(lunar.day)
    }

    /** 今天的农历文案（按设备当前时区）。 */
    fun todayText(): String {
        val c = Calendar.getInstance()
        return fullText(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}

/** 农历日期。 */
data class LunarDate(val year: Int, val month: Int, val day: Int, val isLeap: Boolean)
