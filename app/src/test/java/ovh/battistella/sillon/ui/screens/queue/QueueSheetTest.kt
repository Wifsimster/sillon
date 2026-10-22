package ovh.battistella.sillon.ui.screens.queue

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysteryTrack
import ovh.battistella.sillon.playback.mystery.SleeveLabels
import ovh.battistella.sillon.ui.theme.SillonTheme

private class RecordingActions : QueueActions {
    var jumped: Int? = null
    var removed: Int? = null
    var moved: Pair<Int, Int>? = null
    override fun jump(index: Int) { jumped = index }
    override fun remove(index: Int) { removed = index }
    override fun move(from: Int, to: Int) { moved = from to to }
}

private fun demoItem(key: String) = MediaItem.Builder()
    .setMediaId(key)
    .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $key").setArtist("Demo Artist").build())
    .build()

private fun maskedItem(number: Int) = MysteryMask.mask(
    MysteryTrack(
        PlayableTrack(
            ratingKey = "secret-$number",
            streamUrl = "https://demo.invalid/$number.flac",
            title = "Secret Song $number",
            artist = "Secret Artist",
            album = "Secret Album",
            trackNumber = number,
            durationMs = 200_000,
            artworkUrl = null,
            albumKey = "secret-album",
        ),
        side = 1,
        number = number,
    ),
    SleeveLabels(track = { side, n -> "Mystery sleeve · side $side, track $n" }, album = "Mystery album"),
)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xhdpi")
class QueueSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = RecordingActions()

    private fun show(items: List<MediaItem>, current: Int, context: QueueContext = QueueContext()) {
        val ui = QueueModel.build(QueueSnapshot(QueueItem.of(items), items.indices.toList(), current), context)
        compose.setContent {
            SillonTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { QueueSheetContent(ui, actions) }
            }
        }
    }

    private fun queueOf(vararg keys: String) = keys.map(::demoItem)

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
    }

    private fun SemanticsNodeInteraction.customActionLabels(): List<String> =
        fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

    @Test
    fun showsCurrentUpNextAndFoldedHistory() {
        show(queueOf("a", "b", "c", "d"), current = 1)
        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.onNodeWithText("Song b").assertIsDisplayed()
        compose.onNodeWithText("Up next").assertIsDisplayed()
        compose.onNodeWithText("Song c").assertIsDisplayed()
        compose.onNodeWithText("Song d").assertIsDisplayed()
        compose.onNodeWithText("Already played (1)").assertIsDisplayed()
        compose.onAllNodesWithText("Song a").assertCountEquals(0)

        compose.onNodeWithText("Already played (1)").performClick()
        compose.onNodeWithText("Song a").assertIsDisplayed()
    }

    @Test
    fun touchingATrackJumpsToIt() {
        show(queueOf("a", "b", "c", "d"), current = 1)
        compose.onNodeWithText("Song d").performClick()
        assertEquals(3, actions.jumped)
    }

    @Test
    fun removeActionTakesTheTrackOut() {
        show(queueOf("a", "b", "c", "d"), current = 1)
        compose.onNodeWithText("Song c").customAction("Remove")
        compose.waitForIdle()
        assertEquals(2, actions.removed)
    }

    @Test
    fun moveActionsReorder() {
        show(queueOf("a", "b", "c", "d"), current = 0)
        assertEquals(listOf("Remove", "Move down"), compose.onNodeWithText("Song b").customActionLabels())
        assertEquals(listOf("Remove", "Move up"), compose.onNodeWithText("Song d").customActionLabels())

        compose.onNodeWithText("Song b").customAction("Move down")
        compose.waitForIdle()
        assertEquals(1 to 2, actions.moved)

        compose.onNodeWithText("Song d").customAction("Move up")
        compose.waitForIdle()
        assertEquals(3 to 2, actions.moved)
    }

    @Test
    fun draggingTheHandleMovesByWholeRows() {
        show(queueOf("a", "b", "c", "d"), current = 0)
        val rowHeight = compose.onNodeWithText("Song b").fetchSemanticsNode().size.height.toFloat()
        compose.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true)[0].performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, rowHeight * 1.1f / 10f + 2f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1 to 2, actions.moved)
    }

    @Test
    fun hiddenSurpriseShowsOnlyTrackNumbers() {
        show(listOf(maskedItem(1), maskedItem(2), maskedItem(3)), current = 1)
        compose.onNodeWithText("Track 2 · Mystery sleeve").assertIsDisplayed()
        compose.onNodeWithText("Track 3 · Mystery sleeve").assertIsDisplayed()
        compose.onNodeWithText("Already played (1)").performClick()
        compose.onNodeWithText("Track 1 · Mystery sleeve").assertIsDisplayed()
        compose.onAllNodesWithText("Secret", substring = true).assertCountEquals(0)
    }

    @Test
    fun listeningTogetherIsReadOnly() {
        show(queueOf("a", "b", "c"), current = 0, context = QueueContext(together = true))
        compose.onNodeWithText("Listening together: the group's queue is read-only here.").assertIsDisplayed()
        compose.onNodeWithText("Song c").performClick()
        assertNull(actions.jumped)
        assertTrue(compose.onNodeWithText("Song c").customActionLabels().isEmpty())
        compose.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun radioAndSidesShowInTheQueue() {
        val keys = listOf("a1", "a2", "b1")
        val plan = ovh.battistella.sillon.playback.SidePlan(keys, listOf(0, 0, 1), listOf("A", "B"))
        show(keys.map(::demoItem), current = 0, context = QueueContext(sides = plan, radioContinues = true))
        compose.onNodeWithText("End of side A · flip to side B").assertIsDisplayed()
        compose.onNodeWithText("The radio continues…").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w411dp-h891dp-xhdpi")
class QueueSheetFrTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun surpriseAndTalkBackActionsInFrench() {
        val items = listOf(demoItem("a"), maskedItem(3), demoItem("c"), demoItem("d"))
        val ui = QueueModel.build(QueueSnapshot(QueueItem.of(items), items.indices.toList(), 0), QueueContext())
        compose.setContent {
            SillonTheme(darkTheme = true) { QueueSheetContent(ui, RecordingActions()) }
        }
        compose.onNodeWithText("Piste 3 · Pochette surprise").assertIsDisplayed()
        compose.onNodeWithText("À suivre").assertIsDisplayed()
        val labels = compose.onNodeWithText("Song c").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Retirer", "Monter", "Descendre"), labels)
    }
}
