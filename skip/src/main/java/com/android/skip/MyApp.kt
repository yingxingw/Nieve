package com.android.skip

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import com.android.skip.util.DataStoreUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils

/**
 * SKIP bootstrap holder.
 *
 * Originally the standalone `Application` class of SKIP. In the merged Nieve app the
 * application entry is `com.yunx.app.YunXApp`, which calls [bootstrap] from
 * Application.onCreate so all SKIP behaviour (DataStore, utilcode logging, …) is
 * initialised exactly as before.
 */
object MyApp {

    @SuppressLint("StaticFieldLeak")
    lateinit var context: Context

    lateinit var deviceName: String

    fun bootstrap(app: Application) {
        if (this::context.isInitialized) return
        context = app
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        DataStoreUtils.init(app)

        Utils.init(app)
        LogUtils.getConfig()
            .setLogSwitch(false)                    // 是否输出日志开关
            .setConsoleSwitch(true)                 // 是否在控制台输出日志开关
            .setGlobalTag("SKIP_APP")               // 全局标签
            .setLog2FileSwitch(true)                // 是否写入日志文件开关
            .setDir("${app.cacheDir}/logs")         // 日志文件目录
            .setFilePrefix("log")                   // 日志文件前缀
            .setBorderSwitch(true)                  // 日志边框开关
            .setConsoleFilter(LogUtils.V)           // 控制台过滤器
            .setFileFilter(LogUtils.V)              // 文件过滤器
            .setStackDeep(1)                        // 栈深度
            .setSaveDays(7)                         // 日志保留天数
    }
}
