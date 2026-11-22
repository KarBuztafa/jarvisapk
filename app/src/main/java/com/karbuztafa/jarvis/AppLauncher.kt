package com.karbuztafa.jarvis

import android.content.Context
import android.content.Intent
import java.util.Locale

object AppLauncher {
    fun openAppByName(context: Context, appNameQuery: String): Boolean {
        val pm = context.packageManager
        val target = appNameQuery.lowercase(Locale("tr", "TR")).trim()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(mainIntent, 0)

        for (resolveInfo in apps) {
            val label = resolveInfo.loadLabel(pm).toString().lowercase(Locale("tr", "TR"))
            if (label.contains(target) || target.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }
}