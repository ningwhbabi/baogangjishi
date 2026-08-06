package com.chenyuan.baogang

/** 单个版本发布记录 */
data class Release(
    val version: String,
    val date: String,
    val items: List<String>
)

/**
 * 版本更新历史。
 * 约定：每次更新请在 releases 最前面插入一条（版本号递增），
 * 同时把 app/build.gradle 的 versionCode +1、versionName 改为对应版本（如 1.03）。
 */
object Changelog {
    val releases: List<Release> = listOf(
        Release(
            "1.02", "2026-08-06", listOf(
                "新增：版本号与「更新说明」展示，每次更新可在此查看修复/升级内容",
                "修复：长时间后台/息屏后报岗越来越晚（改用 setAlarmClock，Doze 省电无法延迟）",
                "优化：报岗时刻对齐基准改为北京时间当天零点，严格对齐整点/半点",
                "修复：前台兜底逻辑，停止提醒后下一轮仍可正常弹出"
            )
        ),
        Release(
            "1.01", "2026-08-04", listOf(
                "修复：APP 在前台时循环到点只弹通知、不弹「停止提醒」界面（改为前台服务直接拉起界面）",
                "修复：点停止提醒后主界面倒计时卡在 00:00（目标时刻持久化同步）"
            )
        ),
        Release(
            "1.00", "2026-08-03", listOf(
                "初版：黑金麒麟 + 企业 LOGO 界面",
                "报岗倒计时，自动模式提前 1 分钟（29/59 分）报岗，对齐北京时间墙钟",
                "前台服务 + 全屏通知，锁屏/息屏/切应用均可靠响铃 + 震动（走系统闹钟音量）",
                "支持 APP 内选择自定义铃声"
            )
        )
    )
}
