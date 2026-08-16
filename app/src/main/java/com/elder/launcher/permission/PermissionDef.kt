package com.elder.launcher.permission

import android.Manifest

/** 权限常量与"一键申请"分组定义 */
object PermissionDef {

    /** 存储组（老人相册/文件） */
    val STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    /** 定位组（天气/守护定位） */
    val LOCATION = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    /** 电话组（一键拨号） */
    val PHONE = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )

    /** 短信组（短信指令/转发） */
    val SMS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    /** 联系人组（快捷拨号卡片） */
    val CONTACTS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    /** 全部危险权限：首次引导一键全授 */
    val ALL_RUNTIME = STORAGE + LOCATION + PHONE + SMS + CONTACTS +
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CALL_LOG)
}