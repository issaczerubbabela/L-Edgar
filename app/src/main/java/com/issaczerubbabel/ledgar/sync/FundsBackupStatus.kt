package com.issaczerubbabel.ledgar.sync

/**
 * Whether the deployed Apps Script is too old to back up Funds, remembered across Backups so a
 * scheduled one can ask for a redeploy too (Database Setup shows the hint). Pure.
 */
object FundsBackupStatus {

    /**
     * What a Backup's Funds step (its count, [BackupWorker.SCRIPT_OUTDATED], or null when it failed
     * or didn't run) says: true when the script needs redeploying, false when it took the Funds,
     * null to leave the remembered answer as it was.
     */
    fun needsRedeploy(fundStep: Int?): Boolean? = when (fundStep) {
        null -> null
        BackupWorker.SCRIPT_OUTDATED -> true
        else -> false
    }

    /** The hint shows while the script needs redeploying and there's at least one Fund to lose. */
    fun showHint(needsRedeploy: Boolean, fundCount: Int): Boolean = needsRedeploy && fundCount > 0
}
