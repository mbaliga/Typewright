// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.asoc.typewright.app.android.TypewrightApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.ZoneId

/**
 * The shared provider: every redraw reads the widget's data off the main thread (`goAsync`) and
 * hands the launcher one layout per size. Language, time and time-zone changes redraw too, since
 * the widgets print dates and their own words. The first widget of a kind that reads the open
 * project asks Typewright to publish it again, in case nothing was publishing while none was placed.
 */
abstract class TypewrightWidgetProvider(
    private val kind: WidgetKind,
) : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        val app = context.applicationContext as TypewrightApplication
        app.appScope.launch(Dispatchers.IO) {
            val renderer = WidgetRenderer(app)
            try {
                withTimeout(RENDER_TIMEOUT_MILLIS) { renderer.render(kind, appWidgetManager, appWidgetIds) }
            } catch (_: Exception) {
                runCatching { renderer.renderUnreadable(kind, appWidgetManager, appWidgetIds) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (kind in SNAPSHOT_KINDS) (context.applicationContext as TypewrightApplication).widgetPublisher.republish()
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> redrawAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    /** Redraws every placed widget of this provider. */
    private fun redrawAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        if (ids.isNotEmpty()) onUpdate(context, manager, ids)
    }

    private companion object {
        /** Well under the ten seconds a broadcast receiver has. */
        const val RENDER_TIMEOUT_MILLIS = 8_000L
    }
}

/** W1 Approved. */
class ApprovedWidgetProvider : TypewrightWidgetProvider(WidgetKind.APPROVED)

/**
 * W2 Saved. Also receives [WidgetAlarms]' save check: it redraws W2 and W3, and looks again
 * later while this same process is still saving.
 */
class SavedWidgetProvider : TypewrightWidgetProvider(WidgetKind.SAVED) {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != WidgetAlarms.ACTION_SAVE_CHECK) return super.onReceive(context, intent)
        val pending = goAsync()
        val app = context.applicationContext as TypewrightApplication
        app.appScope.launch(Dispatchers.IO) {
            try {
                if (saveCheckDue(WidgetSnapshotStore(app).read(), WidgetSnapshotStore.PROCESS_TOKEN)) WidgetAlarms.armSaveCheck(app)
                TypewrightWidgets.refresh(app, SAVE_CHECK_KINDS)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        WidgetAlarms.cancelSaveCheckIfUnused(context)
        super.onDisabled(context)
    }
}

/** W3 Recent projects. */
class RecentsWidgetProvider : TypewrightWidgetProvider(WidgetKind.RECENTS) {
    override fun onDisabled(context: Context) {
        WidgetAlarms.cancelSaveCheckIfUnused(context)
        super.onDisabled(context)
    }
}

/** W4 Workbook. */
class WorkbookWidgetProvider : TypewrightWidgetProvider(WidgetKind.WORKBOOK)

/** W6 Shortcuts: its large layout names today's era, so it turns at midnight with W5. */
class ShortcutsWidgetProvider : TypewrightWidgetProvider(WidgetKind.SHORTCUTS) {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetAlarms.armMidnight(context)
    }

    override fun onDisabled(context: Context) {
        WidgetAlarms.cancelMidnightIfUnused(context)
        super.onDisabled(context)
    }
}

/** W5 Lineages: turns to the next era once a day, and receives the midnight alarm for itself and W6. */
class LineagesWidgetProvider : TypewrightWidgetProvider(WidgetKind.LINEAGES) {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetAlarms.armMidnight(context)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action ==
            WidgetAlarms.ACTION_MIDNIGHT
        ) {
            TypewrightWidgets.refresh(context, MIDNIGHT_KINDS)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        WidgetAlarms.cancelMidnightIfUnused(context)
        super.onDisabled(context)
    }
}

/**
 * The widgets' two timed redraws, each one inexact, non-waking `AlarmManager.set(RTC)` with an
 * explicit, immutable broadcast to the app's own provider: no exact-alarm permission, no
 * WorkManager. A phone asleep at the time redraws when it next wakes.
 */
object WidgetAlarms {
    /** Just after local midnight: W5 and W6 turn to the next era. */
    const val ACTION_MIDNIGHT: String = "com.asoc.typewright.action.WIDGET_MIDNIGHT"

    /** A couple of minutes after a save was published still waiting: W2 and W3 look again. */
    const val ACTION_SAVE_CHECK: String = "com.asoc.typewright.action.WIDGET_SAVE_CHECK"

    /** A minute past midnight, so the redraw lands on the new day. */
    private const val MIDNIGHT_SLACK_MILLIS = 60_000L

    /** Long enough for a save to finish; short enough that a stopped Typewright shows soon. */
    private const val SAVE_CHECK_DELAY_MILLIS = 120_000L

    /** Sets (or moves) the midnight redraw. */
    fun armMidnight(context: Context) {
        val next = nextLocalMidnightMillis(System.currentTimeMillis(), ZoneId.systemDefault()) + MIDNIGHT_SLACK_MILLIS
        context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, next, midnight(context))
    }

    /** Cancels the midnight redraw once neither W5 nor W6 is placed. */
    fun cancelMidnightIfUnused(context: Context) {
        if (TypewrightWidgets.placedKinds(context).none { it in MIDNIGHT_KINDS }) {
            context.getSystemService(AlarmManager::class.java)?.cancel(midnight(context))
        }
    }

    /** Sets (or moves) the save check. */
    fun armSaveCheck(context: Context) {
        val at = System.currentTimeMillis() + SAVE_CHECK_DELAY_MILLIS
        context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at, saveCheck(context))
    }

    /** Cancels the save check: the last publish wasn't waiting on a save. */
    fun cancelSaveCheck(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(saveCheck(context))
    }

    /** Cancels the save check once neither W2 nor W3 is placed. */
    fun cancelSaveCheckIfUnused(context: Context) {
        if (TypewrightWidgets.placedKinds(context).none { it in SAVE_CHECK_KINDS }) cancelSaveCheck(context)
    }

    private fun midnight(context: Context): PendingIntent = broadcast(context, LineagesWidgetProvider::class.java, ACTION_MIDNIGHT, 0)

    private fun saveCheck(context: Context): PendingIntent = broadcast(context, SavedWidgetProvider::class.java, ACTION_SAVE_CHECK, 1)

    private fun broadcast(
        context: Context,
        receiver: Class<out AppWidgetProvider>,
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
