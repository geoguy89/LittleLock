package com.littlelock.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PinHasherTest {

    @Test
    fun the_right_pin_verifies_and_a_wrong_one_does_not() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.verify("4821", salt, hash))
        assertFalse(PinHasher.verify("4822", salt, hash))
        assertFalse(PinHasher.verify("", salt, hash))
    }

    @Test
    fun the_same_pin_hashes_differently_with_a_different_salt() {
        val a = PinHasher.hash("1234", PinHasher.newSalt())
        val b = PinHasher.hash("1234", PinHasher.newSalt())
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun only_four_digit_pins_are_valid() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}
