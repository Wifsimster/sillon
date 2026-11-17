package ovh.battistella.sillon.activity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.battistella.sillon.data.activity.ActivityFeed
import ovh.battistella.sillon.data.activity.ActivityState
import ovh.battistella.sillon.data.activity.ActivityStore
import ovh.battistella.sillon.data.repository.PlexRepository
import ovh.battistella.sillon.data.session.Session
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ServerActivityWatch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ActivityStore,
    private val feed: ActivityFeed,
    private val announcer: ListenAnnouncer,
    private val repository: PlexRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val visible = MutableStateFlow(false)
    private val playing = MutableStateFlow(false)
    private val _openRequested = MutableStateFlow(false)
    private var started = false

    private val server: Flow<String?> = repository.session
        .map { (it as? Session.Ready)?.server?.let { s -> "${s.machineId}|${s.endpoint.token.hashCode()}" } }
        .distinctUntilChanged()

    val states: Flow<ActivityState> = server
        .flatMapLatest { if (it == null) flowOf(ActivityState.Loading) else feed.watch() }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS, 0), 1)

    val allowed: StateFlow<Boolean> = server
        .flatMapLatest { id ->
            flow {
                emit(false)
                if (id != null) emit(runCatching { repository.activityAllowed() }.getOrDefault(false))
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS, 0), false)

    val notify: Flow<Boolean> = store.notify

    val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()

    fun start(app: Application) {
        if (started) return
        started = true
        app.registerActivityLifecycleCallbacks(Visibility())
        scope.launch {
            store.notify.distinctUntilChanged().collect { on -> if (on) schedule() else unschedule() }
        }
        scope.launch {
            combine(store.notify, visible, playing) { on, shown, plays -> on && (shown || plays) }
                .distinctUntilChanged()
                .collectLatest { awake ->
                    if (awake) states.collect { if (it is ActivityState.Live) announcer.announce(it.listens) }
                }
        }
    }

    fun setNotify(value: Boolean) {
        scope.launch { store.setNotify(value) }
    }

    fun playbackRunning(value: Boolean) {
        playing.value = value
    }

    fun requestOpen() {
        _openRequested.value = true
    }

    fun opened() {
        _openRequested.value = false
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<ActivityCheckWorker>(CHECK_EVERY_MIN, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    private fun unschedule() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }

    private inner class Visibility : Application.ActivityLifecycleCallbacks {
        private var shown = 0

        override fun onActivityStarted(activity: Activity) {
            shown++
            visible.value = true
        }

        override fun onActivityStopped(activity: Activity) {
            shown = (shown - 1).coerceAtLeast(0)
            visible.value = shown > 0
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val WORK_NAME = "activity-check"
        const val CHECK_EVERY_MIN = 15L
        const val STOP_AFTER_MS = 5_000L
    }
}
