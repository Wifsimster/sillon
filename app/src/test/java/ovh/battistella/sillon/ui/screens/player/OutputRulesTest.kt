package ovh.battistella.sillon.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputRulesTest {
    @Test
    fun `a speaker can be picked while something plays on the phone alone`() {
        assertNull(OutputRules.block(hasMedia = true, togetherActive = false, hiddenMystery = false))
    }

    @Test
    fun `listen together and a hidden sleeve keep the music on the phone`() {
        assertEquals(OutputBlock.TOGETHER, OutputRules.block(hasMedia = true, togetherActive = true, hiddenMystery = true))
        assertEquals(OutputBlock.MYSTERY, OutputRules.block(hasMedia = true, togetherActive = false, hiddenMystery = true))
        assertEquals(OutputBlock.NOTHING_PLAYING, OutputRules.block(hasMedia = false, togetherActive = false, hiddenMystery = false))
    }
}
