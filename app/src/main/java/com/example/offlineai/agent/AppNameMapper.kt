package com.example.offlineai.agent.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings

/**
 * App Name Mapper - unified app launch strategy manager
 * Priority 1: Intent Action (system apps, best compatibility)
 * Priority 2: Package name mapping (third-party apps)
 * Priority 3: Fuzzy matching (fallback)
 */
object AppNameMapper {
    
    /**
     * App launch strategy sealed class
     */
    sealed class LaunchStrategy {
        data class IntentAction(
            val action: String,
            val uri: Uri? = null,
            val type: String? = null
        ) : LaunchStrategy() {
            fun createIntent(): Intent {
                val intent = Intent(action)
                uri?.let { intent.data = it }
                type?.let { intent.type = it }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return intent
            }
        }
        
        data class PackageName(val packageName: String) : LaunchStrategy()
    }
    
    /**
     * Unified app launch strategy mappings
     * System apps use Intent Actions, third-party apps use package names
     */
    private val APP_LAUNCH_MAP = mapOf<String, LaunchStrategy>(
        // System Apps - Intent Actions (Priority 1)
        "dialer" to LaunchStrategy.IntentAction(Intent.ACTION_DIAL),
        "phone" to LaunchStrategy.IntentAction(Intent.ACTION_DIAL),
        "电话" to LaunchStrategy.IntentAction(Intent.ACTION_DIAL),
        "拨号" to LaunchStrategy.IntentAction(Intent.ACTION_DIAL),
        
        "contacts" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, type = ContactsContract.Contacts.CONTENT_TYPE),
        "联系人" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, type = ContactsContract.Contacts.CONTENT_TYPE),
        
        "camera" to LaunchStrategy.IntentAction(MediaStore.ACTION_IMAGE_CAPTURE),
        "相机" to LaunchStrategy.IntentAction(MediaStore.ACTION_IMAGE_CAPTURE),
        
        "gallery" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, type = "image/*"),
        "图库" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, type = "image/*"),
        
        "settings" to LaunchStrategy.IntentAction(Settings.ACTION_SETTINGS),
        "设置" to LaunchStrategy.IntentAction(Settings.ACTION_SETTINGS),
        
        "calendar" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("content://com.android.calendar/time/")),
        "日历" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("content://com.android.calendar/time/")),
        
        "clock" to LaunchStrategy.IntentAction(AlarmClock.ACTION_SHOW_ALARMS),
        "时钟" to LaunchStrategy.IntentAction(AlarmClock.ACTION_SHOW_ALARMS),
        
        "messages" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("sms:")),
        "sms" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("sms:")),
        "短信" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("sms:")),
        
        "browser" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("https://www.google.com")),
        "浏览器" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("https://www.google.com")),
        
        "maps" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("geo:0,0?q=")),
        "地图" to LaunchStrategy.IntentAction(Intent.ACTION_VIEW, uri = Uri.parse("geo:0,0?q=")),
        
        // Third-Party Apps - Package Names (Priority 2)
        "美团" to LaunchStrategy.PackageName("com.sankuai.meituan"),
        "饿了么" to LaunchStrategy.PackageName("me.ele"),
        "大众点评" to LaunchStrategy.PackageName("com.dianping.v1"),
        "麦当劳" to LaunchStrategy.PackageName("com.mcdonalds.gma.cn"),
        "肯德基" to LaunchStrategy.PackageName("com.yum.kfc"),
        "星巴克" to LaunchStrategy.PackageName("com.starbucks.cn"),
        
        "淘宝" to LaunchStrategy.PackageName("com.taobao.taobao"),
        "天猫" to LaunchStrategy.PackageName("com.tmall.wireless"),
        "京东" to LaunchStrategy.PackageName("com.jingdong.app.mall"),
        "拼多多" to LaunchStrategy.PackageName("com.xunmeng.pinduoduo"),
        "闲鱼" to LaunchStrategy.PackageName("com.taobao.idlefish"),
        "唯品会" to LaunchStrategy.PackageName("com.achievo.vipshop"),
        "苏宁易购" to LaunchStrategy.PackageName("com.suning.mobile.ebuy"),
        "小红书" to LaunchStrategy.PackageName("com.xingin.xhs"),
        
        "微信" to LaunchStrategy.PackageName("com.tencent.mm"),
        "QQ" to LaunchStrategy.PackageName("com.tencent.mobileqq"),
        "微博" to LaunchStrategy.PackageName("com.sina.weibo"),
        "抖音" to LaunchStrategy.PackageName("com.ss.android.ugc.aweme"),
        "快手" to LaunchStrategy.PackageName("com.smile.gifmaker"),
        "钉钉" to LaunchStrategy.PackageName("com.alibaba.android.rimet"),
        "企业微信" to LaunchStrategy.PackageName("com.tencent.wework"),
        "知乎" to LaunchStrategy.PackageName("com.zhihu.android"),
        "豆瓣" to LaunchStrategy.PackageName("com.douban.frodo"),
        "贴吧" to LaunchStrategy.PackageName("com.baidu.tieba"),
        
        "支付宝" to LaunchStrategy.PackageName("com.eg.android.AlipayGphone"),
        "云闪付" to LaunchStrategy.PackageName("com.unionpay"),
        "中国银行" to LaunchStrategy.PackageName("com.chinamworld.bocmbci"),
        "工商银行" to LaunchStrategy.PackageName("com.icbc"),
        "建设银行" to LaunchStrategy.PackageName("com.ccb.android.mbank"),
        "招商银行" to LaunchStrategy.PackageName("cmb.pb"),
        "农业银行" to LaunchStrategy.PackageName("com.android.bankabc"),
        
        "高德地图" to LaunchStrategy.PackageName("com.autonavi.minimap"),
        "百度地图" to LaunchStrategy.PackageName("com.baidu.BaiduMap"),
        "腾讯地图" to LaunchStrategy.PackageName("com.tencent.map"),
        "滴滴出行" to LaunchStrategy.PackageName("com.sdu.didi.psnger"),
        "携程" to LaunchStrategy.PackageName("ctrip.android.view"),
        "去哪儿" to LaunchStrategy.PackageName("com.Qunar"),
        "飞猪" to LaunchStrategy.PackageName("com.taobao.trip"),
        "12306" to LaunchStrategy.PackageName("com.MobileTicket"),
        "哈啰" to LaunchStrategy.PackageName("com.jingyao.easybike"),
        "美团打车" to LaunchStrategy.PackageName("com.sankuai.meituan.takeoutnew"),
        
        "B站" to LaunchStrategy.PackageName("tv.danmaku.bili"),
        "爱奇艺" to LaunchStrategy.PackageName("com.qiyi.video"),
        "腾讯视频" to LaunchStrategy.PackageName("com.tencent.qqlive"),
        "优酷" to LaunchStrategy.PackageName("com.youku.phone"),
        "芒果TV" to LaunchStrategy.PackageName("com.hunantv.imgo.activity"),
        "网易云音乐" to LaunchStrategy.PackageName("com.netease.cloudmusic"),
        "QQ音乐" to LaunchStrategy.PackageName("com.tencent.qqmusic"),
        "酷狗音乐" to LaunchStrategy.PackageName("com.kugou.android"),
        "喜马拉雅" to LaunchStrategy.PackageName("com.ximalaya.ting.android"),
        
        "今日头条" to LaunchStrategy.PackageName("com.ss.android.article.news"),
        "腾讯新闻" to LaunchStrategy.PackageName("com.tencent.news"),
        "网易新闻" to LaunchStrategy.PackageName("com.netease.newsreader.activity"),
        "澎湃新闻" to LaunchStrategy.PackageName("com.thepaper.paperapp"),
        "微信读书" to LaunchStrategy.PackageName("com.tencent.weread"),
        "掌阅" to LaunchStrategy.PackageName("com.chaozh.iReaderFree"),
        "起点读书" to LaunchStrategy.PackageName("com.qidian.QDReader"),
        
        "盒马" to LaunchStrategy.PackageName("com.wudaokou.hippo"),
        "叮咚买菜" to LaunchStrategy.PackageName("com.yaya.zone"),
        "每日优鲜" to LaunchStrategy.PackageName("cn.missfresh.application"),
        "美团买菜" to LaunchStrategy.PackageName("com.sankuai.meituan.meituanmaicai"),
        "淘票票" to LaunchStrategy.PackageName("com.taobao.movie.android"),
        "猫眼" to LaunchStrategy.PackageName("com.sankuai.movie"),
        
        "QQ邮箱" to LaunchStrategy.PackageName("com.tencent.androidqqmail"),
        "网易邮箱" to LaunchStrategy.PackageName("com.netease.mail"),
        "WPS" to LaunchStrategy.PackageName("cn.wps.moffice_eng"),
        "百度网盘" to LaunchStrategy.PackageName("com.baidu.netdisk"),
        "夸克" to LaunchStrategy.PackageName("com.quark.browser"),
        "UC浏览器" to LaunchStrategy.PackageName("com.UCMobile"),
        "Chrome" to LaunchStrategy.PackageName("com.android.chrome"),
        
        "YouTube" to LaunchStrategy.PackageName("com.google.android.youtube"),
        "Gmail" to LaunchStrategy.PackageName("com.google.android.gm"),
        "Google Maps" to LaunchStrategy.PackageName("com.google.android.apps.maps"),
        "Facebook" to LaunchStrategy.PackageName("com.facebook.katana"),
        "Instagram" to LaunchStrategy.PackageName("com.instagram.android"),
        "Twitter" to LaunchStrategy.PackageName("com.twitter.android"),
        "WhatsApp" to LaunchStrategy.PackageName("com.whatsapp"),
        "Telegram" to LaunchStrategy.PackageName("org.telegram.messenger"),
        "TikTok" to LaunchStrategy.PackageName("com.zhiliaoapp.musically"),
        "Netflix" to LaunchStrategy.PackageName("com.netflix.mediaclient"),
        "Spotify" to LaunchStrategy.PackageName("com.spotify.music"),
        "Amazon" to LaunchStrategy.PackageName("com.amazon.mShop.android.shopping"),
        "Uber" to LaunchStrategy.PackageName("com.ubercab"),
        "Airbnb" to LaunchStrategy.PackageName("com.airbnb.android")
    )
    
    /**
     * Get launch strategy for app name (Priority 1 & 2)
     * Returns null if not found in predefined mappings
     */
    fun getLaunchStrategy(appName: String): LaunchStrategy? {
        // 1. Exact match (case-sensitive)
        APP_LAUNCH_MAP[appName]?.let { return it }
        
        // 2. Case-insensitive match
        return APP_LAUNCH_MAP.entries.find { 
            it.key.equals(appName, ignoreCase = true) 
        }?.value
    }
    
    /**
     * Get package name for app display name (for backward compatibility)
     * Priority 3: Fuzzy matching on installed apps
     */
    fun getPackageName(context: Context, appName: String): String? {
        // Try fuzzy match on installed apps (Priority 3)
        return fuzzyMatchInstalledApp(context, appName)
    }
    
    /**
     * Fuzzy match app name against installed applications
     */
    private fun fuzzyMatchInstalledApp(context: Context, appName: String): String? {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        // First try exact match
        installedApps.find { app ->
            val label = pm.getApplicationLabel(app).toString()
            label.equals(appName, ignoreCase = true)
        }?.packageName?.let { return it }
        
        // Then try contains match
        installedApps.find { app ->
            val label = pm.getApplicationLabel(app).toString()
            label.contains(appName, ignoreCase = true) || 
            appName.contains(label, ignoreCase = true)
        }?.packageName?.let { return it }
        
        return null
    }
    
    /**
     * Get app display name from package name
     */
    fun getAppName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Check if app is installed
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Get all installed app names
     */
    fun getAllInstalledAppNames(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // Non-system apps
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                Pair(label, app.packageName)
            }
            .sortedBy { it.first }
    }
}
