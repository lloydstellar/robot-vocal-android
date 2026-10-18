package nl.lloydstellar.robotvocal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.lloydstellar.la2l.La2lException
import nl.lloydstellar.la2l.ktx.awaitImport
import nl.lloydstellar.robotvocal.App
import nl.lloydstellar.robotvocal.render.VocalTake
import kotlin.math.roundToInt

/**
 * Pairing with LA2L and sending to Live, with the texts LA2L prescribes. It
 * lives as long as the Studio, so "Waiting for Import in Live" keeps running
 * while the user works on another page.
 */
class LiveSession(private val scope: CoroutineScope) {
    var paired by mutableStateOf<Boolean?>(null)
        private set
    var device by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private var blockedUntil = 0L
    private var waiting: Job? = null

    init {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        paired = App.live.isPaired()
        device = App.live.deviceName()
    }

    fun pair(code: String) = run {
        device = App.live.pair(code)
        paired = true
        status = null
    }

    fun unpair() = run {
        App.live.unpair()
        paired = false
        device = null
        status = null
    }

    fun send(title: String, take: VocalTake, withDry: Boolean) {
        if (System.currentTimeMillis() < blockedUntil) return
        waiting?.cancel()
        waiting = run {
            status = "Sending... 0%"
            val delivery = App.live.send(title, take, withDry) { share -> status = "Sending... ${(share * 100).roundToInt()}%" }
            status = "Waiting for Import in Live"
            busy = false
            val result = delivery.awaitImport()
            val imported = result.sounds.count { it.status == "imported" }
            status = if (result.status == "imported") "In Live" else "In Live: $imported of ${result.sounds.size} sounds"
        }
    }

    /** Runs a call of LA2L and turns its errors into what the user should see, per LA2L's rules. */
    private fun run(block: suspend () -> Unit): Job = scope.launch {
        busy = true
        error = null
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: La2lException) {
            when (e.code) {
                La2lException.Code.ABORTED -> Unit
                La2lException.Code.NOT_PAIRED -> {
                    paired = false
                    device = null
                    status = null
                    error = e.message
                }
                La2lException.Code.RATE_LIMITED -> {
                    blockedUntil = System.currentTimeMillis() + e.retryAfter * 1000L
                    status = null
                    error = e.message
                }
                else -> {
                    status = null
                    error = e.message
                }
            }
        } finally {
            busy = false
        }
    }
}
