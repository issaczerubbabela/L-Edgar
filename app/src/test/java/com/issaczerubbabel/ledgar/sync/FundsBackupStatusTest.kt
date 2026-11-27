package com.issaczerubbabel.ledgar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Redeploy the script to back up Funds" hint, whether the Backup ran from Settings or on its own. */
class FundsBackupStatusTest {

    @Test
    fun aScriptTooOldForFundsSetsTheFlag() {
        assertEquals(true, FundsBackupStatus.needsRedeploy(BackupWorker.SCRIPT_OUTDATED))
    }

    @Test
    fun aFundsBackupThatGoesThroughClearsIt() {
        assertEquals(false, FundsBackupStatus.needsRedeploy(3))
        assertEquals("no Funds to send means nothing is lost", false, FundsBackupStatus.needsRedeploy(0))
    }

    @Test
    fun aFailedOrSkippedFundsStepLeavesTheFlagAsItWas() {
        assertNull(FundsBackupStatus.needsRedeploy(null))
    }

    @Test
    fun theHintShowsOnlyWhileFlaggedAndThereIsAFund() {
        assertTrue(FundsBackupStatus.showHint(needsRedeploy = true, fundCount = 1))
        assertFalse(FundsBackupStatus.showHint(needsRedeploy = true, fundCount = 0))
        assertFalse(FundsBackupStatus.showHint(needsRedeploy = false, fundCount = 4))
    }
}
