package io.github.manxisuo.spriglauncher.platform

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.provider.AlarmClock
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings

class SystemAccess(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun isHomeRoleHeld(): Boolean {
        val manager = context.getSystemService(RoleManager::class.java)
        return manager.isRoleAvailable(RoleManager.ROLE_HOME) && manager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun homeRoleRequest(): Intent? {
        val manager = context.getSystemService(RoleManager::class.java)
        return if (manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else null
    }

    fun usageSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun homeSettingsIntent() = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun appDetailsIntent(packageName: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun clockIntent(): Intent = context.packageManager
        .getLaunchIntentForPackage(XIAOMI_CLOCK_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun calendarIntent(): Intent {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        val preferred = handlers.firstOrNull {
            it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        } ?: handlers.firstOrNull()
        return preferred?.activityInfo?.let {
            intent.setComponent(ComponentName(it.packageName, it.name))
        } ?: intent
    }

    private companion object {
        const val XIAOMI_CLOCK_PACKAGE = "com.android.deskclock"
    }
}
