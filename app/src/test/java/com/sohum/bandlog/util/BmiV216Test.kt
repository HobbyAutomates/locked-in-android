package com.sohum.bandlog.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** v2.16: the Progress BMI showed "19.0 · Above healthy" for 74.5 kg at 166 cm (it is 27.0). */
class BmiV216Test {
    @Test fun ownersNumbers() {
        val b = Bmi.bmi(74.5, 166.0)!!
        assertEquals(27.04, b, 0.01)
        assertEquals("27.0", Bmi.format1(b))
        assertEquals(Bmi.Band.OBESE, Bmi.bandIndia(b))
        assertEquals("Above healthy", Bmi.bandIndia(b).label)
    }

    @Test fun indianCutOffs() {
        assertEquals(Bmi.Band.UNDER, Bmi.bandIndia(18.4))
        assertEquals(Bmi.Band.HEALTHY, Bmi.bandIndia(18.5))
        assertEquals(Bmi.Band.HEALTHY, Bmi.bandIndia(22.9))
        assertEquals(Bmi.Band.OVER, Bmi.bandIndia(23.0))
        assertEquals(Bmi.Band.OVER, Bmi.bandIndia(24.9))
        assertEquals(Bmi.Band.OBESE, Bmi.bandIndia(25.0))
    }

    @Test fun bandAgreesWithTheShownNumber() {
        // 22.96 is shown as "23.0", so it must read "over", not "healthy".
        assertEquals("23.0", Bmi.format1(22.96))
        assertEquals(Bmi.Band.OVER, Bmi.bandIndia(22.96))
        assertEquals(Bmi.Band.UNDER, Bmi.bandIndia(18.44))
    }

    @Test fun healthyRangeFor166() {
        val r = Bmi.healthyRange(166.0)
        assertEquals(51.0, r.min, 0.1)
        assertEquals(63.1, r.max, 0.1)
    }
}
