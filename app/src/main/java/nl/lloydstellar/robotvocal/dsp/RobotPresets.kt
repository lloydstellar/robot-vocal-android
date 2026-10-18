package nl.lloydstellar.robotvocal.dsp

import nl.lloydstellar.robotvocal.dsp.Carrier.CHORD
import nl.lloydstellar.robotvocal.dsp.Carrier.NOISE
import nl.lloydstellar.robotvocal.dsp.Carrier.PULSE
import nl.lloydstellar.robotvocal.dsp.Carrier.SAW
import nl.lloydstellar.robotvocal.dsp.Carrier.SQUARE
import nl.lloydstellar.robotvocal.dsp.Carrier.SUB
import nl.lloydstellar.robotvocal.dsp.Carrier.SUPERSAW

/** A named robot effect: a chain of effects, or none for the dry voice. */
class RobotPreset(val id: String, val name: String, val description: String, val effect: Effect?)

/**
 * The robot effects. The vocoders follow the song's notes, so every one of
 * them turns any voice into a machine singing or rapping on that pitch.
 * docs/research/06 describes the hardware each one is modelled on.
 */
object RobotPresets {
    /** Before a robot effect: no rumble, and words at an even level. */
    private fun clean() = listOf(Eq(highPass = 70.0), Compressor(-22.0, 3.0))

    private fun chain(vararg effects: Effect) = Chain(clean() + effects)

    val ALL = listOf(
        RobotPreset("dry", "DRY", "The voice as the engine made it", null),
        RobotPreset("brazen1", "BRAZEN HEAD I", "Twenty-band vocoder on a raw sawtooth, some bare voice, coarse steps",
            chain(Vocoder(carrier = SAW, dry = 0.22), Crusher(rate = 48_000, bits = 5))),
        RobotPreset("brazen2", "BRAZEN HEAD II", "The vocoder on a choir of detuned minor triads", chain(Vocoder(carrier = CHORD))),
        RobotPreset("brazen3", "BRAZEN HEAD III", "The vocoder on a sub bass an octave and two below", chain(Vocoder(carrier = SUB))),
        RobotPreset("flat20", "FLAT TWENTY", "Wider bands, clearer words", chain(Vocoder(q = 3.0, carrier = SAW))),
        RobotPreset("sixteen", "SIXTEEN CHANNEL", "Sixteen bands from 50 Hz to 5 kHz on a pulse wave",
            chain(Vocoder(bands = 16, low = 50.0, high = 5080.0, q = 5.0, attackMs = 6.0, carrier = PULSE))),
        RobotPreset("supersaw", "SUPERSAW CHOIR", "Seven detuned saws, wide and bright", chain(Vocoder(q = 5.0, carrier = SUPERSAW))),
        RobotPreset("monotone", "MONOTONE UNIT", "Any voice on one note, clear and level",
            chain(Vocoder(bands = 24, q = 4.5, carrier = PULSE, hiss = 0.6, dry = 0.08))),
        RobotPreset("mouthtube", "MOUTH TUBE", "A talkbox on a sawtooth synth", chain(Talkbox(carrier = SAW))),
        RobotPreset("talktube", "TALK TUBE", "Talkbox with the hiss of the voice for clear consonants", chain(Talkbox(carrier = PULSE, hiss = 0.6))),
        RobotPreset("spellchip", "SPELLING CHIP", "Ten-pole LPC at 8 kHz, the way a 1978 talking toy spoke", chain(LpcChip())),
        RobotPreset("exterminator", "EXTERMINATOR", "Ring modulation at 30 Hz, lifted mids, driven", chain(RingMod(30.0, 2.5))),
        RobotPreset("centurion", "CENTURION", "A pulse-wave vocoder through a slow phaser, dark",
            chain(Vocoder(bands = 16, carrier = PULSE), Phaser(barsPerCycle = 2.0), Eq(lowPass = 8000.0))),
        RobotPreset("phase", "PHASE ROBOT", "A phaser that sweeps once a bar", chain(Phaser(barsPerCycle = 1.0, mix = 0.6))),
        RobotPreset("jet", "JET", "A flanger sweeping over two bars", chain(Flanger(barsPerCycle = 2.0))),
        RobotPreset("tincan", "TIN CAN", "Telephone band, a metal ring and coarse steps",
            chain(Eq(highPass = 400.0, lowPass = 3200.0), Comb(2.5, 0.7, 0.7), Crusher(11_025, 6))),
        RobotPreset("tight", "TIGHT DELAY", "A very short ringing echo and a slap one 16th later",
            chain(Comb(4.0, 0.55, 0.5, slapSixteenths = 1, slapMix = 0.3))),
        RobotPreset("stutter", "STUTTER", "A square-wave vocoder cut in 16ths on the beat",
            chain(Vocoder(q = 4.0, carrier = SQUARE), Gate("xx.xx.x.xx.xx.x."))),
        RobotPreset("droid", "BASS DROID", "A vocoder on a square an octave down, driven", chain(Vocoder(carrier = SQUARE, octave = -1), Drive(2.5))),
        RobotPreset("whisper", "WHISPER UNIT", "A vocoder on noise: words without pitch", chain(Vocoder(q = 4.0, carrier = NOISE, hiss = 0.7))),
        RobotPreset("eightbit", "EIGHT BIT", "Eight bits at 11 kHz, nothing more", Crusher(11_025, 8)),
    )

    fun byId(id: String): RobotPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
