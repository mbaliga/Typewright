// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** The app's widget providers by kind, and the one way the app asks them to redraw. */
object TypewrightWidgets {
    /** [kind]'s provider class. */
    fun providerClass(kind: WidgetKind): Class<out AppWidgetProvider> =
        when (kind) {
            WidgetKind.APPROVED -> ApprovedWidgetProvider::class.java
            WidgetKind.SAVED -> SavedWidgetProvider::class.java
            WidgetKind.RECENTS -> RecentsWidgetProvider::class.java
            WidgetKind.WORKBOOK -> WorkbookWidgetProvider::class.java
            WidgetKind.LINEAGES -> LineagesWidgetProvider::class.java
            WidgetKind.SHORTCUTS -> ShortcutsWidgetProvider::class.java
        }

    /** The kinds with at least one widget on a home screen now; none where the device has no widgets. */
    fun placedKinds(context: Context): Set<WidgetKind> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptySet()
        return WidgetKind.entries.filterTo(
            mutableSetOf(),
        ) { manager.getAppWidgetIds(ComponentName(context, providerClass(it))).isNotEmpty() }
    }

    /** Asks every placed widget of [kinds] to redraw, with an explicit broadcast to its own provider. */
    fun refresh(
        context: Context,
        kinds: Collection<WidgetKind> = WidgetKind.entries,
    ) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (kind in kinds) {
            val component = ComponentName(context, providerClass(kind))
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) continue
            val intent =
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(component)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
