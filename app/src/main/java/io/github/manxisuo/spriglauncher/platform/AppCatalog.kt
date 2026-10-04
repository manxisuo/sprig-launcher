package io.github.manxisuo.spriglauncher.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import io.github.manxisuo.spriglauncher.domain.LaunchableEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppCatalog(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconCache = LruCache<String, Bitmap>(96)
    private val _entries = MutableStateFlow<List<LaunchableEntry>>(emptyList())
    val entries: StateFlow<List<LaunchableEntry>> = _entries.asStateFlow()
    private val _icons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val icons: StateFlow<Map<String, Bitmap>> = _icons.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val user = Process.myUserHandle()
        val serial = userManager.getSerialNumberForUser(user)
        val activities = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
        val values = activities.map { info ->
            val component = info.componentName.flattenToString()
            LaunchableEntry(
                id = "$serial:$component",
                packageName = info.applicationInfo.packageName,
                componentName = component,
                userSerial = serial,
                label = info.label?.toString()?.ifBlank { info.applicationInfo.packageName }
                    ?: info.applicationInfo.packageName,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        _entries.value = values
        val newIcons = buildMap {
            values.forEach { entry ->
                val cached = iconCache.get(entry.id)
                val bitmap = cached ?: activities.firstOrNull {
                    it.componentName.flattenToString() == entry.componentName
                }?.getBadgedIcon(0)?.toBitmap(144)?.also { iconCache.put(entry.id, it) }
                if (bitmap != null) put(entry.id, bitmap)
            }
        }
        _icons.value = newIcons
    }

    fun invalidatePackage(packageName: String?) {
        if (packageName == null) iconCache.evictAll()
        else entries.value.filter { it.packageName == packageName }.forEach { iconCache.remove(it.id) }
    }

    fun launch(entry: LaunchableEntry): Result<Unit> = runCatching {
        val user = userManager.getUserForSerialNumber(entry.userSerial)
            ?: error("用户资料已不可用")
        val component = ComponentName.unflattenFromString(entry.componentName)!!
        runCatching { launcherApps.startMainActivity(component, user, null, null) }
            .getOrElse {
                context.startActivity(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap.width == size && bitmap.height == size) return bitmap
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }
}
