package com.sohum.bandlog.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.16: every CoverPresets board SVG parses into shapes and resolves its gradients. */
class CoverSvgTest {
    @Test fun allThirtyFourParse() {
        assertEquals(34, COVER_SVGS.size)
        COVER_SVGS.forEachIndexed { i, src ->
            val doc = parseSvg(src)
            assertTrue("cover ${i + 1} has shapes", doc.nodes.size >= 2)
            assertTrue("cover ${i + 1} has its background gradient", doc.gradients.isNotEmpty())
        }
        // Chalk (#11) blurs its clouds; Kettlebells (#07) nests groups.
        assertTrue(parseSvg(COVER_SVGS[10]).blurred.isNotEmpty())
        assertTrue(parseSvg(COVER_SVGS[6]).nodes.any { it is SvgNode.Group })
        // Track (#09) has its lane number as text.
        assertTrue(parseSvg(COVER_SVGS[8]).nodes.any { it is SvgNode.Shape && it.tag == "text" && it.text == "4" })
    }
}
