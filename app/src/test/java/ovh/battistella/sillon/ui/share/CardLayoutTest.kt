package ovh.battistella.sillon.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutTest {
    /** A fixed-width font: every character is 0.55 em wide, a little wider than DM Sans on average. */
    private val measure = TextMeasure { text, _, size -> text.length * size * 0.55f }

    private val everyCard = CardFormat.entries.flatMap { f -> CardTemplate.entries.map { f to it } }

    private fun layout(format: CardFormat, template: CardTemplate, text: CardText) =
        CardLayoutEngine.layout(format, template, text, measure)

    private val plain = CardText(title = "Nemesis", artist = "Leap Fidei", detail = "1979", stars = 4f)

    private val worst = CardText(
        title = "The Ballad of the Extraordinarily Long Album Title That Never Seems to End, Part Two (Deluxe Remastered Anniversary Edition)",
        artist = "Orchestre Philharmonique de la Radio, Chœur et Solistes, sous la direction d'un chef au nom très long",
        detail = "Disque bonus · 1971",
        stars = 3.5f,
        note = "n".repeat(10) + " " + "Une phrase personnelle qui prend toute la place permise, cent quarante caractères pile, pour voir si la carte tient.",
        lyrics = listOf(
            "And the first line of the chorus goes on and on, longer than any card is wide",
            "Then a second line comes, just as long, and asks for its own room as well",
        ),
    )

    @Test
    fun `everything stays inside the card and nothing overlaps`() {
        listOf(plain, worst, CardText(title = "X")).forEach { text ->
            everyCard.forEach { (format, template) ->
                val card = layout(format, template, text)
                val name = "$format/$template/${text.title.take(10)}"
                val boxes = listOf(card.cover, card.signature) + card.texts.map { it.box } + listOfNotNull(card.stars)
                boxes.forEach { assertTrue("$name: $it is inside", it.inside(card.width, card.height)) }
                listOfNotNull(card.disc, card.frame).forEach { assertTrue("$name: $it is inside", it.inside(card.width, card.height)) }
                val content = card.texts.map { it.box } + listOfNotNull(card.stars)
                content.forEach { box ->
                    assertFalse("$name: text $box overlaps the cover ${card.cover}", box.overlaps(card.cover))
                    assertFalse("$name: text $box overlaps the signature", box.overlaps(card.signature))
                    card.disc?.let { assertFalse("$name: text $box overlaps the record", box.overlaps(it)) }
                }
                content.forEachIndexed { i, a -> content.drop(i + 1).forEach { b -> assertFalse("$name: $a overlaps $b", a.overlaps(b)) } }
                card.texts.forEach { block ->
                    block.lines.forEach { line ->
                        assertTrue("$name: '$line' fits its box", measure.width(line, block.font, block.size) <= block.box.width - block.indent + 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun `the cover stays large`() {
        everyCard.forEach { (format, template) ->
            val easy = layout(format, template, plain)
            val hard = layout(format, template, worst)
            val floor = if (format == CardFormat.SQUARE) 300f else 520f
            assertTrue("$format/$template cover ${easy.cover.width}", easy.cover.width >= format.width * 0.44f)
            assertTrue("$format/$template cover ${hard.cover.width}", hard.cover.width >= floor)
            assertTrue("$format/$template: more text, smaller cover", hard.cover.width <= easy.cover.width)
        }
        assertTrue(layout(CardFormat.STORY, CardTemplate.POLAROID, plain).cover.width >= 800f)
    }

    @Test
    fun `a very long title shrinks, then ends with an ellipsis`() {
        val card = layout(CardFormat.SQUARE, CardTemplate.VINYL, worst)
        val title = card.texts.first { it.role == TextRole.TITLE }
        assertTrue("title shrank to ${title.size}", title.size < 64f)
        assertTrue(title.lines.last().endsWith(CardLayoutEngine.ELLIPSIS))
        assertTrue(title.lines.size <= 2)
    }

    @Test
    fun `a short title keeps its full size`() {
        val title = layout(CardFormat.STORY, CardTemplate.LABEL, plain).texts.first { it.role == TextRole.TITLE }
        assertEquals(84f, title.size)
        assertEquals(listOf("Nemesis"), title.lines)
    }

    @Test
    fun `a word wider than the card is broken`() {
        val card = layout(CardFormat.SQUARE, CardTemplate.LABEL, CardText(title = "W".repeat(120)))
        val title = card.texts.first { it.role == TextRole.TITLE }
        assertTrue(title.lines.size == 2)
        assertTrue(title.lines.last().endsWith(CardLayoutEngine.ELLIPSIS))
    }

    @Test
    fun `no year, no line for it`() {
        everyCard.forEach { (format, template) ->
            val card = layout(format, template, plain.copy(detail = null))
            assertTrue(card.texts.none { it.role == TextRole.DETAIL })
            assertFalse(card.text.contains("1979"))
        }
    }

    @Test
    fun `no rating, no stars`() {
        everyCard.forEach { (format, template) ->
            val card = layout(format, template, plain.copy(stars = null))
            assertNull(card.stars)
            assertNull(card.rating)
        }
        val rated = layout(CardFormat.SQUARE, CardTemplate.POLAROID, plain)
        assertNotNull(rated.stars)
        assertEquals(4f, rated.rating)
    }

    @Test
    fun `the note and the lyrics give up lines before the cover gets small`() {
        val square = layout(CardFormat.SQUARE, CardTemplate.LABEL, worst)
        val note = square.texts.first { it.role == TextRole.NOTE }
        assertTrue(note.lines.size in 1..3)
        assertTrue(square.texts.any { it.role == TextRole.LYRICS })
        val story = layout(CardFormat.STORY, CardTemplate.LABEL, worst)
        assertTrue(story.texts.first { it.role == TextRole.NOTE }.lines.size >= note.lines.size)
    }

    @Test
    fun `the card shows only its own text`() {
        val card = layout(CardFormat.STORY, CardTemplate.VINYL, worst)
        val allowed = worst.lines.joinToString(" ")
        card.text.forEach { line ->
            val words = line.removeSuffix(CardLayoutEngine.ELLIPSIS).split(" ").filter { it.isNotBlank() }
            words.forEach { assertTrue("'$it' comes from the card's text", allowed.contains(it)) }
        }
    }
}
