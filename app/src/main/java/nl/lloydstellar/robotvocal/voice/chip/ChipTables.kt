package nl.lloydstellar.robotvocal.voice.chip

/**
 * The tables of one chip of the TMS5100 family: the loudness of each of the
 * 16 energy codes, the ten reflection coefficients each code stands for
 * (times 512), and the chirp that voiced frames start every period with.
 */
class ChipTables(val energy: IntArray, val k: Array<IntArray>, val chirp: IntArray) {
    companion object {
        /** The chip's own rate: 8 kHz, a frame every 25 ms. */
        const val RATE = 8000

        /**
         * The TMS5100 (TMC0281) of the 1978 Speak & Spell, from MAME's
         * tms5110r.hxx (BSD-3-Clause, see NOTICE), which takes them from US
         * patent 4,209,836 and from the chip itself.
         */
        val TMS5100 = ChipTables(
            energy = intArrayOf(0, 0, 1, 1, 2, 3, 5, 7, 10, 15, 21, 30, 43, 61, 86, 0),
            k = arrayOf(
                intArrayOf(
                    -501, -497, -493, -488, -480, -471, -460, -446, -427, -405, -378, -344, -305, -259, -206, -148,
                    -86, -21, 45, 110, 171, 227, 277, 320, 357, 388, 413, 434, 451, 464, 474, 498,
                ),
                intArrayOf(
                    -349, -328, -305, -280, -252, -223, -192, -158, -124, -88, -51, -14, 23, 60, 97, 133,
                    167, 199, 230, 259, 286, 310, 333, 354, 372, 389, 404, 417, 429, 439, 449, 506,
                ),
                intArrayOf(-397, -365, -327, -282, -229, -170, -104, -36, 35, 104, 169, 228, 281, 326, 364, 396),
                intArrayOf(-369, -334, -293, -245, -191, -131, -67, -1, 64, 128, 188, 243, 291, 332, 367, 397),
                intArrayOf(-319, -286, -250, -211, -168, -122, -74, -25, 24, 73, 121, 167, 210, 249, 285, 318),
                intArrayOf(-290, -252, -209, -163, -114, -62, -9, 44, 97, 147, 194, 238, 278, 313, 344, 371),
                intArrayOf(-291, -256, -216, -174, -128, -80, -31, 19, 69, 117, 163, 206, 246, 283, 316, 345),
                intArrayOf(-218, -133, -38, 59, 152, 235, 305, 361),
                intArrayOf(-226, -157, -82, -3, 76, 151, 220, 280),
                intArrayOf(-179, -122, -61, 1, 62, 123, 179, 231),
            ),
            chirp = intArrayOf(
                0x00, 0x2a, 0xd4, 0x32, 0xb2, 0x12, 0x25, 0x14, 0x02, 0xe1, 0xc5, 0x02, 0x5f, 0x5a, 0x05, 0x0f,
                0x26, 0xfc, 0xa5, 0xa5, 0xd6, 0xdd, 0xdc, 0xfc, 0x25, 0x2b, 0x22, 0x21, 0x0f, 0xff, 0xf8, 0xee,
                0xed, 0xef, 0xf7, 0xf6, 0xfa, 0x00, 0x03, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            ).map { it.toByte().toInt() }.toIntArray(),
        )
    }
}
