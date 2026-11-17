package ovh.battistella.sillon.activity

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import ovh.battistella.sillon.data.activity.ActivityFeed
import ovh.battistella.sillon.data.activity.ActivityState
import ovh.battistella.sillon.data.activity.ActivityStore

@HiltWorker
class ActivityCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: ActivityStore,
    private val feed: ActivityFeed,
    private val announcer: ListenAnnouncer,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!store.notify.first()) return Result.success()
        val state = feed.load()
        if (state is ActivityState.Live) announcer.announce(state.listens)
        return Result.success()
    }
}
