package ovh.battistella.sillon.ui.screens.together

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.battistella.sillon.data.jellyfin.GroupState
import ovh.battistella.sillon.data.jellyfin.SyncPlayGroupInfo
import ovh.battistella.sillon.data.jellyfin.SyncPlayQueueItem
import ovh.battistella.sillon.data.jellyfin.SyncQueue
import ovh.battistella.sillon.together.EndReason
import ovh.battistella.sillon.together.MyStatus
import ovh.battistella.sillon.together.TogetherState
import ovh.battistella.sillon.ui.screens.player.PlayerHeader
import ovh.battistella.sillon.ui.theme.SillonTheme
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class TogetherRenders(private val locale: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = TogetherActions({}, {}, {}, {}, {}, {}, {})
    private val groups = GroupList.Loaded(
        listOf(
            SyncPlayGroupInfo("g1", "Road trip", "Playing", listOf("Ana", "Ben")),
            SyncPlayGroupInfo("g2", "Soirée chez Chloé", "Paused", listOf("Chloé")),
        ),
    )
    private val queue = SyncQueue(
        items = listOf(SyncPlayQueueItem("a1", "p1"), SyncPlayQueueItem("a2", "p2")),
        playingIndex = 0,
        startPositionMs = 0,
        isPlaying = true,
        reason = "NewPlaylist",
        shuffle = false,
        repeatMode = "RepeatNone",
    )

    private fun render(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        val view = compose.activity.window.decorView
        val window = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(window))
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInWindow
        val bitmap = Bitmap.createBitmap(
            window,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.width.toInt().coerceAtMost(window.width - bounds.left.toInt()),
            bounds.height.toInt().coerceAtMost(window.height - bounds.top.toInt()),
        )
        val dir = File("build/outputs/renders").apply { mkdirs() }
        File(dir, "$name-$locale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(name: String, state: TogetherState, available: Boolean = true, list: GroupList = groups) =
        render("together-$name") { TogetherContent(state, available, list, actions, PaddingValues()) }

    @Test
    fun lobby() = screen("1-lobby", TogetherState.Off, list = GroupList.Loaded(emptyList()))

    @Test
    fun lobbyWithSessions() = screen("2-sessions", TogetherState.Off)

    @Test
    fun connecting() = screen("3-connecting", TogetherState.Connecting("Road trip"))

    @Test
    fun inGroup() = screen(
        "4-in-group",
        TogetherState.InGroup(
            groupId = "g1",
            groupName = "Road trip",
            members = listOf("Ana", "Ben", "Chloé Delacroix-Montgomery"),
            groupState = GroupState.Playing,
            queue = queue,
            me = MyStatus.Playing,
            driftMs = -12,
        ),
    )

    @Test
    fun timedOut() = screen("6-timed-out", TogetherState.Ended(EndReason.TimedOut))

    @Test
    fun plex() = screen("5-plex", TogetherState.Off, available = false)

    @Test
    fun playerHeader() = render("player-header") {
        Box(Modifier.width(411.dp).padding(horizontal = 24.dp, vertical = 8.dp)) {
            PlayerHeader(onClose = {}, onTogether = {}, startingRadio = false, radioEnabled = true, onRadio = {})
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-night-xhdpi")
class TogetherRendersEn : TogetherRenders("en")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-night-xhdpi")
class TogetherRendersFr : TogetherRenders("fr")
