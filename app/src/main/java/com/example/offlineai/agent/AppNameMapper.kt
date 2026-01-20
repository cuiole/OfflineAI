package com.example.offlineai.agent.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * App Name Mapper - maps app display names to package names
 * Supports both predefined mappings and fuzzy matching for installed apps
 */
object AppNameMapper {
    
    /**
     * Predefined app name to package name mappings
     * Covers 100+ popular Chinese and international apps
     */
    private val APP_NAME_MAP = mapOf(
        // Food & Delivery
        "美团" to "com.sankuai.meituan",
        "饿了么" to "me.ele",
        "大众点评" to "com.dianping.v1",
        "麦当劳" to "com.mcdonalds.gma.cn",
        "肯德基" to "com.yum.kfc",
        "星巴克" to "com.starbucks.cn",
        
        // E-commerce
        "淘宝" to "com.taobao.taobao",
        "天猫" to "com.tmall.wireless",
        "京东" to "com.jingdong.app.mall",
        "拼多多" to "com.xunmeng.pinduoduo",
        "闲鱼" to "com.taobao.idlefish",
        "唯品会" to "com.achievo.vipshop",
        "苏宁易购" to "com.suning.mobile.ebuy",
        "小红书" to "com.xingin.xhs",
        
        // Social & Communication
        "微信" to "com.tencent.mm",
        "QQ" to "com.tencent.mobileqq",
        "微博" to "com.sina.weibo",
        "抖音" to "com.ss.android.ugc.aweme",
        "快手" to "com.smile.gifmaker",
        "钉钉" to "com.alibaba.android.rimet",
        "企业微信" to "com.tencent.wework",
        "知乎" to "com.zhihu.android",
        "豆瓣" to "com.douban.frodo",
        "贴吧" to "com.baidu.tieba",
        
        // Payment & Finance
        "支付宝" to "com.eg.android.AlipayGphone",
        "云闪付" to "com.unionpay",
        "中国银行" to "com.chinamworld.bocmbci",
        "工商银行" to "com.icbc",
        "建设银行" to "com.ccb.android.mbank",
        "招商银行" to "cmb.pb",
        "农业银行" to "com.android.bankabc",
        
        // Travel & Transportation
        "高德地图" to "com.autonavi.minimap",
        "百度地图" to "com.baidu.BaiduMap",
        "腾讯地图" to "com.tencent.map",
        "滴滴出行" to "com.sdu.didi.psnger",
        "携程" to "ctrip.android.view",
        "去哪儿" to "com.Qunar",
        "飞猪" to "com.taobao.trip",
        "12306" to "com.MobileTicket",
        "哈啰" to "com.jingyao.easybike",
        "美团打车" to "com.sankuai.meituan.takeoutnew",
        
        // Entertainment & Media
        "抖音" to "com.ss.android.ugc.aweme",
        "快手" to "com.smile.gifmaker",
        "B站" to "tv.danmaku.bili",
        "爱奇艺" to "com.qiyi.video",
        "腾讯视频" to "com.tencent.qqlive",
        "优酷" to "com.youku.phone",
        "芒果TV" to "com.hunantv.imgo.activity",
        "网易云音乐" to "com.netease.cloudmusic",
        "QQ音乐" to "com.tencent.qqmusic",
        "酷狗音乐" to "com.kugou.android",
        "喜马拉雅" to "com.ximalaya.ting.android",
        
        // News & Reading
        "今日头条" to "com.ss.android.article.news",
        "腾讯新闻" to "com.tencent.news",
        "网易新闻" to "com.netease.newsreader.activity",
        "澎湃新闻" to "com.thepaper.paperapp",
        "微信读书" to "com.tencent.weread",
        "掌阅" to "com.chaozh.iReaderFree",
        "起点读书" to "com.qidian.QDReader",
        
        // Shopping & Lifestyle
        "盒马" to "com.wudaokou.hippo",
        "叮咚买菜" to "com.yaya.zone",
        "每日优鲜" to "cn.missfresh.application",
        "美团买菜" to "com.sankuai.meituan.meituanmaicai",
        "淘票票" to "com.taobao.movie.android",
        "猫眼" to "com.sankuai.movie",
        
        // Utilities
        "支付宝" to "com.eg.android.AlipayGphone",
        "微信" to "com.tencent.mm",
        "QQ邮箱" to "com.tencent.androidqqmail",
        "网易邮箱" to "com.netease.mail",
        "WPS" to "cn.wps.moffice_eng",
        "百度网盘" to "com.baidu.netdisk",
        "夸克" to "com.quark.browser",
        "UC浏览器" to "com.UCMobile",
        "Chrome" to "com.android.chrome",
        
        // System & Tools
        "相机" to "com.android.camera2",
        "图库" to "com.android.gallery3d",
        "文件管理" to "com.android.documentsui",
        "设置" to "com.android.settings",
        "日历" to "com.android.calendar",
        "时钟" to "com.android.deskclock",
        "计算器" to "com.android.calculator2",
        "联系人" to "com.android.contacts",
        "电话" to "com.android.dialer",
        "短信" to "com.android.mms",
        "下载管理器" to "com.android.providers.downloads.ui",
        
        // International Apps
        "YouTube" to "com.google.android.youtube",
        "Gmail" to "com.google.android.gm",
        "Google Maps" to "com.google.android.apps.maps",
        "Facebook" to "com.facebook.katana",
        "Instagram" to "com.instagram.android",
        "Twitter" to "com.twitter.android",
        "WhatsApp" to "com.whatsapp",
        "Telegram" to "org.telegram.messenger",
        "TikTok" to "com.zhiliaoapp.musically",
        "Netflix" to "com.netflix.mediaclient",
        "Spotify" to "com.spotify.music",
        "Amazon" to "com.amazon.mShop.android.shopping",
        "Uber" to "com.ubercab",
        "Airbnb" to "com.airbnb.android"
    )
    
    /**
     * Get package name for app display name
     * First checks predefined mappings, then performs fuzzy matching on installed apps
     */
    fun getPackageName(context: Context, appName: String): String? {
        // 1. Check predefined mappings (exact match)
        APP_NAME_MAP[appName]?.let { return it }
        
        // 2. Check predefined mappings (case-insensitive)
        APP_NAME_MAP.entries.find { 
            it.key.equals(appName, ignoreCase = true) 
        }?.value?.let { return it }
        
        // 3. Fuzzy match on installed apps
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
