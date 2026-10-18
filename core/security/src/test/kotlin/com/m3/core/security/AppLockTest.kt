package com.m3.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 1_000_000L
    private val lock = AppLock(timeoutMillis = 5_000, clock = { now })

    @Test
    fun `starts locked`() {
        assertTrue(lock.locked.value)
    }

    @Test
    fun `short trip to the background keeps it unlocked`() {
        lock.unlock()
        lock.onBackground()
        now += 4_999
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `locks again after the timeout`() {
        lock.unlock()
        lock.onBackground()
        now += 5_000
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `clock going backwards locks`() {
        lock.unlock()
        lock.onBackground()
        now -= 1
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `foreground without background changes nothing`() {
        lock.unlock()
        now += 60_000
        lock.onForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun `backgrounding while still locked does not start a timer`() {
        lock.onBackground()
        now += 60_000
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.locked.value)
    }
}
