package com.tektomic.dexvoice

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile "DeX Voice": tap to start/stop dictation. */
class VoiceTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(Dispatchers.Main.immediate)
        scope = s
        val c = VoiceController.get(this)
        s.launch { c.state.collect { render(it) } }
    }

    override fun onStopListening() {
        scope?.cancel(); scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        VoiceController.get(this).toggle()
    }

    private fun render(state: VoiceState) {
        val tile = qsTile ?: return
        tile.label = "DeX Voice"
        when (state) {
            is VoiceState.Recording -> { tile.state = Tile.STATE_ACTIVE; tile.subtitle = "Listening… tap to stop" }
            is VoiceState.Transcribing -> { tile.state = Tile.STATE_ACTIVE; tile.subtitle = "Transcribing…" }
            is VoiceState.Error -> { tile.state = Tile.STATE_INACTIVE; tile.subtitle = "Error, tap to retry" }
            else -> { tile.state = Tile.STATE_INACTIVE; tile.subtitle = "Tap to talk" }
        }
        tile.updateTile()
    }
}
