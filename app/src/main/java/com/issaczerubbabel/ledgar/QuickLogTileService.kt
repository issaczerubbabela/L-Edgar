package com.issaczerubbabel.ledgar

import android.app.PendingIntent
import android.os.Build
import android.content.Intent
import android.service.quicksettings.TileService

class QuickLogTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val quickLogIntent = Intent(this, QuickLogActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            quickLogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchQuickLogAction = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(quickLogIntent)
            }
        }

        if (isLocked) {
            unlockAndRun(launchQuickLogAction)
        } else {
            launchQuickLogAction()
        }

        qsTile?.let { tile ->
            tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }
}
