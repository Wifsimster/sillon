package ovh.battistella.sillon.sonos

import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.sillon.data.plex.ServerEndpoint
import ovh.battistella.sillon.data.repository.SonosAccess
import ovh.battistella.sillon.data.repository.SonosAccessException
import ovh.battistella.sillon.playback.PlayableTrack
import ovh.battistella.sillon.playback.mystery.MysteryMask
import ovh.battistella.sillon.playback.mystery.MysteryTrack
import ovh.battistella.sillon.playback.mystery.SleeveLabels
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class SonosHandoverTest {
    private val zone = SonosZone("RINCON_A", "Salon", "http://192.168.1.21:1400")
    private val group = SonosGroup("RINCON_A:1", zone, listOf(zone))
    private val phone = FakePhone()
    private val speaker = FakeSpeaker()
    private val shown = mutableListOf<Output>()
    private val told = mutableListOf<OutputNotice>()
    private val attached = mutableListOf<SonosPlayer?>()
    private var opened = 0
    private var closed = 0
    private var accessError: Exception? = null
    private val phonePlayingWhileSending = mutableListOf<Boolean>()

    private fun item(key: String) = MediaItem.Builder()
        .setMediaId(key)
        .setUri("http://192.168.1.5:32400/library/parts/$key/file.flac?X-Plex-Token=account")
        .build()

    private fun masked(key: String) = MysteryMask.mask(
        MysteryTrack(PlayableTrack(key, "http://192.168.1.5:32400/$key.flac", "Secret $key", "Secret artist", "Secret album", 1, 1L, null), side = 1, number = 1),
        SleeveLabels(track = { side, n -> "Side $side, track $n" }, album = "Mystery", artworkUri = Uri.parse("file:///sleeve.png")),
    )

    private fun uri(key: String) = "http://192.168.1.5:32400/library/parts/$key/file.flac?X-Plex-Token=transient"

    private fun TestScope.handover(unplayable: Set<String> = emptySet(), client: SoapCaller = speaker) = SonosHandover(
        phone = phone,
        scope = backgroundScope,
        client = client,
        openAccess = {
            opened++
            accessError?.let { throw it }
            SonosAccess(ServerEndpoint("http://192.168.1.5:32400", "transient")) { closed++ }
        },
        tracks = { keys, _ ->
            phonePlayingWhileSending += phone.playWhenReady
            keys.filter { it !in unplayable }.associateWith { SonosTrack(it, uri(it), "<DIDL-Lite/>") }
        },
        refuse = { SonosRefusal.refuses(it, mystery = null) },
        togetherActive = { false },
        show = { shown += it },
        tell = { told += it },
        attach = { attached += it },
        now = { testScheduler.currentTime },
    )

    private fun playingOnPhone(vararg keys: String, index: Int = 0, positionMs: Long = 0) {
        phone.setMediaItems(keys.map(::item), index, positionMs)
        phone.play()
    }

    /** Lets the speaker be polled for [ms]. */
    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `the phone's queue goes to the speaker at its track and position`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b", "c", index = 1, positionMs = 42_000)
        val handover = handover()

        handover.playOn(group)

        assertEquals(listOf(uri("a"), uri("b"), uri("c")), speaker.queue)
        assertEquals("x-rincon-queue:RINCON_A#0", speaker.mediaUri)
        assertEquals(1, speaker.index)
        assertEquals(42_000L, speaker.positionMs)
        assertEquals("PLAYING", speaker.transport)
        assertFalse("the phone stops while the speaker plays", phone.playWhenReady)
        assertEquals("the phone stopped before the queue went out", listOf(false), phonePlayingWhileSending)
        assertSame(handover.player, attached.single())
        assertEquals(Output.OnSonos(group), shown.last())
        assertTrue(told.isEmpty())
        // Only the speaker's token travels.
        assertFalse(speaker.calls.any { call -> call.args.any { it.second.contains("account") } })
    }

    @Test
    fun `back on the phone, the music carries on where the speaker stood`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b", "c")
        val handover = handover()
        handover.playOn(group)
        speaker.index = 2
        speaker.positionMs = 10_000
        wait(1_100)

        handover.backToPhone(resume = true)

        assertEquals(listOf("a", "b", "c"), phone.items.map { it.mediaId })
        assertEquals(2, phone.currentMediaItemIndex)
        assertEquals(10_000L, phone.currentPosition)
        assertTrue(phone.playWhenReady)
        assertEquals("PAUSED_PLAYBACK", speaker.transport)
        assertEquals(1, closed)
        assertNull(attached.last())
        assertNull(handover.player)
        assertEquals(Output.Phone, shown.last())
    }

    @Test
    fun `tracks the speaker can't play are left out, and the phone finds its own index back`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b", "c", "d", index = 2)
        val handover = handover(unplayable = setOf("b"))

        handover.playOn(group)
        assertEquals(listOf(uri("a"), uri("c"), uri("d")), speaker.queue)
        assertEquals(1, speaker.index)

        speaker.index = 2
        wait(1_100)
        handover.backToPhone(resume = false)
        assertEquals(listOf("a", "b", "c", "d"), phone.items.map { it.mediaId })
        assertEquals(3, phone.currentMediaItemIndex)
    }

    @Test
    fun `a hidden mystery sleeve never reaches the speaker`() = runTest(timeout = 5.minutes) {
        phone.setMediaItems(listOf(masked("s1"), masked("s2")))
        phone.play()

        handover().playOn(group)

        assertTrue(speaker.calls.isEmpty())
        assertEquals(0, opened)
        assertEquals(listOf(OutputNotice.MYSTERY_ON_PHONE), told)
        assertTrue(phone.playWhenReady)
    }

    @Test
    fun `a sleeve started while on Sonos plays on the phone, and nothing more goes to the speaker`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val handover = handover()
        handover.playOn(group)
        val sonos = handover.player!!
        val adds = speaker.count("AddMultipleURIsToQueue")
        val plays = speaker.count("Play")

        // What the mystery launcher does through the media session.
        sonos.setMediaItems(listOf(masked("s1"), masked("s2")))
        sonos.prepare()
        sonos.play()
        runCurrent()

        assertEquals(adds, speaker.count("AddMultipleURIsToQueue"))
        assertEquals(plays, speaker.count("Play"))
        assertFalse(speaker.queue.any { it.contains("s1") || it.contains("s2") })
        assertEquals(listOf("s1", "s2"), phone.items.map { it.mediaId })
        assertTrue(phone.playWhenReady)
        assertTrue(OutputNotice.MYSTERY_ON_PHONE in told)
        assertNull(handover.player)
    }

    @Test
    fun `a sleeve added to the queue on Sonos is not sent`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val handover = handover()
        handover.playOn(group)
        val adds = speaker.count("AddMultipleURIsToQueue")

        handover.player!!.addMediaItems(listOf(masked("s1")))
        runCurrent()

        assertEquals(adds, speaker.count("AddMultipleURIsToQueue"))
        assertEquals(2, speaker.queue.size)
        assertEquals(listOf(OutputNotice.MYSTERY_NOT_ADDED), told)
    }

    @Test
    fun `when the speaker refuses the queue, the phone plays on`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val handover = handover(unplayable = setOf("a", "b"))

        handover.playOn(group)

        assertEquals(listOf(OutputNotice.FAILED), told)
        assertTrue(phone.playWhenReady)
        assertEquals(1, closed)
        assertNull(handover.player)
    }

    @Test
    fun `no token for the speaker, nothing is sent and the phone plays on`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a")
        accessError = SonosAccessException(SonosAccessException.Reason.PLEX_TOKEN)

        handover().playOn(group)

        assertTrue(speaker.calls.isEmpty())
        assertEquals(listOf(OutputNotice.PLEX_TOKEN_REFUSED), told)
        assertTrue(phone.playWhenReady)
        assertEquals(Output.Phone, shown.last())
    }

    @Test
    fun `a pause right after starting keeps the session`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        speaker.starts = false
        speaker.pauseShows = false
        val handover = handover()
        handover.playOn(group)

        handover.player!!.pause()
        runCurrent()
        wait(30_000)

        assertTrue(told.isEmpty())
        assertNotNull(handover.player)
    }

    @Test
    fun `a speaker that never starts sends the music back to the phone, paused`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        speaker.starts = false
        val handover = handover()
        handover.playOn(group)

        wait(25_000)

        assertEquals(listOf(OutputNotice.COULD_NOT_PLAY), told)
        assertNull(handover.player)
        assertFalse(phone.playWhenReady)
        assertEquals(1, closed)
    }

    @Test
    fun `taken over by another controller, the speaker is let go and left alone`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val handover = handover()
        handover.playOn(group)
        val pauses = speaker.count("Pause")

        speaker.mediaUri = "x-sonos-spotify:spotify%3atrack%3a1"
        wait(5_000)

        assertEquals(listOf(OutputNotice.TAKEN_OVER), told)
        assertEquals(pauses, speaker.count("Pause"))
        assertEquals("PLAYING", speaker.transport)
        assertNull(handover.player)
    }

    @Test
    fun `a paused speaker is polled less and less`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a")
        val handover = handover()
        handover.playOn(group)
        handover.player!!.pause()
        runCurrent()
        val before = speaker.count("GetTransportInfo")

        wait(10 * 60_000)

        // 4 s for two minutes, then once a minute: about 30 + 8 polls, not 150.
        val polls = speaker.count("GetTransportInfo") - before
        assertTrue("$polls polls", polls in 30..45)
    }

    @Test
    fun `quick volume key presses add up on the speaker`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a")
        val handover = handover()
        handover.playOn(group)
        val player = handover.player!!

        // Three presses before the first SetGroupVolume has gone out.
        repeat(3) { player.increaseDeviceVolume(0) }
        runCurrent()

        assertEquals(18, speaker.volume)
        assertEquals(18, player.deviceVolume)
    }

    @Test
    fun `mute shows in the player, also when set from the Sonos app`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a")
        val handover = handover()
        handover.playOn(group)
        val player = handover.player!!

        player.setDeviceMuted(true, 0)
        runCurrent()
        assertTrue(speaker.muted)
        assertTrue(player.isDeviceMuted)

        speaker.muted = false
        wait(6_000)
        assertFalse(player.isDeviceMuted)
    }

    @Test
    fun `an unreachable speaker hands the music back without waiting on it again`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val handover = handover()
        handover.playOn(group)

        speaker.unreachable = true
        wait(10_000)
        val callsWhenLost = speaker.calls.size
        wait(30_000)

        assertEquals(listOf(OutputNotice.UNREACHABLE), told)
        assertNull(handover.player)
        assertEquals("no pause sent to a dead speaker", 0, speaker.count("Pause"))
        assertEquals("nothing polled after the loss", callsWhenLost, speaker.calls.size)
        assertEquals(1, closed)
    }

    private val kitchen = SonosZone("RINCON_B", "Cuisine", "http://192.168.1.22:1400")
    private val bedroom = SonosZone("RINCON_C", "Chambre", "http://192.168.1.23:1400")

    @Test
    fun `several rooms picked play together, the queue going to the leader only`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val house = FakeHousehold(speaker, zone, kitchen, bedroom)
        val handover = handover(client = house)

        handover.playOn(listOf(zone, bedroom))

        assertEquals(mapOf("RINCON_C" to "RINCON_A"), house.joined)
        assertEquals(listOf(uri("a"), uri("b")), speaker.queue)
        assertEquals("PLAYING", speaker.transport)
        val shownGroup = (shown.last() as Output.OnSonos).group
        assertEquals("Salon + Chambre", shownGroup.name)
        assertEquals(setOf(house.baseOf("RINCON_A")), house.queueCallsTo)
    }

    @Test
    fun `adding or removing a room around the playing speaker doesn't move the music`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a", "b")
        val house = FakeHousehold(speaker, zone, kitchen, bedroom)
        val handover = handover(client = house)
        handover.playOn(listOf(zone, bedroom))
        val player = handover.player
        val added = speaker.count("AddMultipleURIsToQueue")

        handover.playOn(listOf(zone, kitchen))

        assertSame(player, handover.player)
        assertEquals(added, speaker.count("AddMultipleURIsToQueue"))
        assertEquals(mapOf("RINCON_B" to "RINCON_A"), house.joined)
        assertEquals("Salon + Cuisine", (shown.last() as Output.OnSonos).group.name)
        assertEquals("Salon + Cuisine", handover.player?.group?.name)
    }

    @Test
    fun `a room that won't join leaves the music on the phone`() = runTest(timeout = 5.minutes) {
        playingOnPhone("a")
        val house = FakeHousehold(speaker, zone, kitchen, bedroom).apply { refuses = "RINCON_C" }
        val handover = handover(client = house)

        handover.playOn(listOf(zone, bedroom))

        assertNull(handover.player)
        assertTrue(speaker.queue.isEmpty())
        assertEquals(Output.Phone, shown.last())
        assertEquals(listOf(OutputNotice.FAILED), told)
    }
}

/** A household of lone rooms that Sonos lets group; the [speaker] answers for the leader's transport. */
class FakeHousehold(private val speaker: FakeSpeaker, vararg rooms: SonosZone) : SoapCaller {
    private val rooms = rooms.toList()

    /** Room uuid → the coordinator it plays along with. */
    val joined = linkedMapOf<String, String>()
    var refuses: String? = null
    val queueCallsTo = mutableSetOf<String>()

    fun baseOf(uuid: String) = rooms.first { it.uuid == uuid }.baseUrl

    override suspend fun call(baseUrl: String, call: SoapCall): Map<String, String> {
        val room = rooms.first { it.baseUrl == baseUrl }
        val args = call.args.toMap()
        return when {
            call.action == "GetZoneGroupState" -> mapOf("ZoneGroupState" to state())
            call.action == "BecomeCoordinatorOfStandaloneGroup" -> emptyMap<String, String>().also {
                joined.remove(room.uuid)
                joined.entries.removeAll { it.value == room.uuid }
            }
            call.action == "SetAVTransportURI" && args["CurrentURI"].orEmpty().startsWith("x-rincon:") -> {
                if (room.uuid == refuses) throw SonosException(800, "join refused")
                emptyMap<String, String>().also { joined[room.uuid] = args.getValue("CurrentURI").removePrefix("x-rincon:") }
            }
            else -> {
                if (call.action == "AddMultipleURIsToQueue") queueCallsTo += baseUrl
                speaker.call(baseUrl, call)
            }
        }
    }

    private fun state(): String {
        val leaders = rooms.filter { it.uuid !in joined }
        return "<ZoneGroupState><ZoneGroups>" + leaders.joinToString("") { leader ->
            val members = listOf(leader) + rooms.filter { joined[it.uuid] == leader.uuid }
            "<ZoneGroup Coordinator=\"${leader.uuid}\" ID=\"${leader.uuid}:1\">" +
                members.joinToString("") { "<ZoneGroupMember UUID=\"${it.uuid}\" Location=\"${it.baseUrl}/xml/device_description.xml\" ZoneName=\"${it.name}\"/>" } +
                "</ZoneGroup>"
        } + "</ZoneGroups></ZoneGroupState>"
    }
}
