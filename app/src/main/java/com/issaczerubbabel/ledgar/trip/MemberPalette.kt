package com.issaczerubbabel.ledgar.trip

/**
 * The Member colours, as 0xRRGGBB. Each takes white text at 4:1 or better. A colour identifies a
 * person and never means money direction; gets back and owes keep their own colours.
 */
object MemberPalette {
    val colors: List<Long> = listOf(
        0x4F5BD5, 0xD9487D, 0xB96A00, 0x8E59D9, 0x1A7FC0, 0x0B8089, 0xB5652E, 0x5B6B7A
    )

    fun colorFor(index: Int): Long = colors[Math.floorMod(index, colors.size)]

    /** The first colour no one in the Trip uses yet, or the next in rotation when all are taken. */
    fun nextFree(used: Collection<Int>): Int {
        val taken = used.map { Math.floorMod(it, colors.size) }.toSet()
        return colors.indices.firstOrNull { it !in taken } ?: (used.size % colors.size)
    }
}
