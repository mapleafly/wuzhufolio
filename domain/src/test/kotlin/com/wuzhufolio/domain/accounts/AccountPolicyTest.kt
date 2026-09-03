package com.wuzhufolio.domain.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 口令策略（原型 pwScore 口径定量）。 */
class AccountPolicyTest {

    @Test
    fun `minimum gate requires 8 plus letter and digit`() {
        assertFalse(AccountPolicy.meetsMinimum("short1A"))
        assertFalse(AccountPolicy.meetsMinimum("abcdefgh"))
        assertFalse(AccountPolicy.meetsMinimum("12345678"))
        assertTrue(AccountPolicy.meetsMinimum("password1"))
        assertTrue(AccountPolicy.meetsMinimum("zbpw1Aa!"))
        assertFalse(AccountPolicy.meetsMinimum(""))
    }

    @Test
    fun `strength tiers follow prototype pwScore rules`() {
        assertEquals(AccountPolicy.Strength.WEAK, AccountPolicy.strength("pass1"))
        assertEquals(AccountPolicy.Strength.WEAK, AccountPolicy.strength("password1"))
        assertEquals(AccountPolicy.Strength.MEDIUM, AccountPolicy.strength("passw0rd-a"))
        assertEquals(AccountPolicy.Strength.STRONG, AccountPolicy.strength("Passw0rd-zb!"))
        assertEquals(AccountPolicy.Strength.WEAK, AccountPolicy.strength(""))
    }

    @Test
    fun `username validity`() {
        assertFalse(AccountPolicy.isValidUsername(""))
        assertFalse(AccountPolicy.isValidUsername("   "))
        assertTrue(AccountPolicy.isValidUsername("Alex"))
        assertTrue(AccountPolicy.isValidUsername("alex-2026"))
    }
}
