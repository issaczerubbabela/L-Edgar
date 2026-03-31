package com.sheetsync

import android.content.Intent
import android.service.quicksettings.TileService

class QuickLogTileService : TileService() {

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickLogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }
}
