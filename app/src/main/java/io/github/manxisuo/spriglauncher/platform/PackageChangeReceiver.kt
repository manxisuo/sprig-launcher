package io.github.manxisuo.spriglauncher.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.manxisuo.spriglauncher.SprigApplication

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SprigApplication ?: return
        app.catalog.invalidatePackage(intent.data?.schemeSpecificPart)
        app.requestCatalogRefresh()
    }
}
