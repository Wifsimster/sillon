package ovh.battistella.sillon.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ovh.battistella.sillon.MainActivity
import ovh.battistella.sillon.R
import ovh.battistella.sillon.data.activity.ActiveListen
import ovh.battistella.sillon.data.activity.ActivityStore
import ovh.battistella.sillon.data.activity.ListenKind
import ovh.battistella.sillon.data.activity.ListenLedger
import ovh.battistella.sillon.data.repository.PlexRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListenAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ActivityStore,
    private val repository: PlexRepository,
) {
    private val lock = Mutex()
    private var ledger: Map<String, Long>? = null
    private var savedAt = 0L

    suspend fun announce(listens: List<ActiveListen>, nowMs: Long = System.currentTimeMillis()) = lock.withLock {
        val serverId = runCatching { repository.currentServerId() }.getOrNull() ?: return@withLock
        val update = ListenLedger.update(ledger ?: store.ledger(), serverId, listens, nowMs)
        ledger = update.ledger
        if (update.started.isNotEmpty() || nowMs - savedAt >= SAVE_EVERY_MS) {
            store.saveLedger(update.ledger)
            savedAt = nowMs
        }
        update.started.forEach { post(it) }
    }

    private suspend fun post(listen: ActiveListen) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val user = listen.user.ifBlank { context.getString(R.string.activity_user_unknown) }
        val title = context.getString(
            if (listen.kind == ListenKind.VIDEO) R.string.activity_notification_watching else R.string.activity_notification_listening,
            user,
        )
        val text = listOfNotNull(listen.title.takeIf { it.isNotBlank() }, listen.subtitle).joinToString(" — ")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_activity)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(listen.device.takeIf { it.isNotBlank() })
            .setLargeIcon(cover(listen.artwork))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setTimeoutAfter(ListenLedger.QUIET_MS)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(listen.listenerKey.hashCode(), notification)
    }

    private suspend fun cover(url: String?): Bitmap? {
        if (url == null) return null
        return runCatching {
            withTimeoutOrNull(COVER_TIMEOUT_MS) {
                val request = ImageRequest.Builder(context).data(url).size(COVER_PX).allowHardware(false).build()
                (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            }
        }.getOrNull()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.activity_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private companion object {
        const val CHANNEL_ID = "activity"
        const val SAVE_EVERY_MS = 60_000L
        const val COVER_TIMEOUT_MS = 5_000L
        const val COVER_PX = 256
    }
}
