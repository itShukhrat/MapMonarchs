package com.mapmonarchs.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cadence copy contract.
 *
 * Instrumented for the same reason the lock copy test is: these are string ids
 * now, and only a Context turns an id into words. The assertion did not get less
 * useful when localisation moved it, so it moved too.
 *
 * WHAT IT GUARDS: a settings row that does not say what it costs is not a real
 * choice. Battery drain is the largest uninstall driver in this genre, and the
 * player is owed the trade in plain language before they pick.
 */
@RunWith(AndroidJUnit4::class)
class TrackingModeCopyTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyModeStatesItsBatteryTradeOff() {
        TrackingMode.entries.forEach { mode ->
            assertTrue("no label for $mode", context.getString(mode.labelRes).isNotBlank())
            assertTrue("no cost detail for $mode", context.getString(mode.detailRes).isNotBlank())
        }
    }
}
