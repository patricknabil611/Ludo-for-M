package com.example.engine

/**
 * Pure 15x15 Ludo board coordinate system.
 * Coordinates are (row, col) with row 0..14 (top to bottom) and col 0..14 (left to right).
 */
data class BoardCoord(
    val row: Int,
    val col: Int
)

object LudoBoardGeometry {

    const val GRID_SIZE = 15

    /**
     * 8 safe squares on the 52-cell outer track:
     * Each player's start square (0, 13, 26, 39) + star square 8 steps after each start (8, 21, 34, 47).
     */
    val START_TRACK_INDICES: Set<Int> = setOf(0, 13, 26, 39)
    val STAR_TRACK_INDICES: Set<Int> = setOf(8, 21, 34, 47)
    val SAFE_TRACK_INDICES: Set<Int> = START_TRACK_INDICES + STAR_TRACK_INDICES

    /**
     * Exact 52-cell clockwise outer track coordinates.
     * Index 0 is Red's start square at (6, 1).
     */
    val OUTER_TRACK: List<BoardCoord> = listOf(
        // 0..4: Left arm top row moving right toward center
        BoardCoord(6, 1), // 0 - Red Start (Safe)
        BoardCoord(6, 2), // 1
        BoardCoord(6, 3), // 2
        BoardCoord(6, 4), // 3
        BoardCoord(6, 5), // 4
        // 5..10: Top arm left column moving up
        BoardCoord(5, 6), // 5
        BoardCoord(4, 6), // 6
        BoardCoord(3, 6), // 7
        BoardCoord(2, 6), // 8 - Star Safe (8 steps after Red start)
        BoardCoord(1, 6), // 9
        BoardCoord(0, 6), // 10
        // 11..12: Top bridge
        BoardCoord(0, 7), // 11 - Green home column entry
        BoardCoord(0, 8), // 12
        // 13..17: Top arm right column moving down
        BoardCoord(1, 8), // 13 - Green Start (Safe)
        BoardCoord(2, 8), // 14
        BoardCoord(3, 8), // 15
        BoardCoord(4, 8), // 16
        BoardCoord(5, 8), // 17
        // 18..23: Right arm top row moving right
        BoardCoord(6, 9),  // 18
        BoardCoord(6, 10), // 19
        BoardCoord(6, 11), // 20
        BoardCoord(6, 12), // 21 - Star Safe (8 steps after Green start)
        BoardCoord(6, 13), // 22
        BoardCoord(6, 14), // 23
        // 24..25: Right bridge
        BoardCoord(7, 14), // 24 - Yellow home column entry
        BoardCoord(8, 14), // 25
        // 26..30: Right arm bottom row moving left
        BoardCoord(8, 13), // 26 - Yellow Start (Safe)
        BoardCoord(8, 12), // 27
        BoardCoord(8, 11), // 28
        BoardCoord(8, 10), // 29
        BoardCoord(8, 9),  // 30
        // 31..36: Bottom arm right column moving down
        BoardCoord(9, 8),  // 31
        BoardCoord(10, 8), // 32
        BoardCoord(11, 8), // 33
        BoardCoord(12, 8), // 34 - Star Safe (8 steps after Yellow start)
        BoardCoord(13, 8), // 35
        BoardCoord(14, 8), // 36
        // 37..38: Bottom bridge
        BoardCoord(14, 7), // 37 - Blue home column entry
        BoardCoord(14, 6), // 38
        // 39..43: Bottom arm left column moving up
        BoardCoord(13, 6), // 39 - Blue Start (Safe)
        BoardCoord(12, 6), // 40
        BoardCoord(11, 6), // 41
        BoardCoord(10, 6), // 42
        BoardCoord(9, 6),  // 43
        // 44..49: Left arm bottom row moving left
        BoardCoord(8, 5), // 44
        BoardCoord(8, 4), // 45
        BoardCoord(8, 3), // 46
        BoardCoord(8, 2), // 47 - Star Safe (8 steps after Blue start)
        BoardCoord(8, 1), // 48
        BoardCoord(8, 0), // 49
        // 50..51: Left bridge
        BoardCoord(7, 0), // 50 - Red home column entry
        BoardCoord(6, 0)  // 51
    )

    private val HOME_COLUMNS: Map<PlayerColor, List<BoardCoord>> = mapOf(
        PlayerColor.RED to listOf(
            BoardCoord(7, 1), BoardCoord(7, 2), BoardCoord(7, 3), BoardCoord(7, 4), BoardCoord(7, 5)
        ),
        PlayerColor.GREEN to listOf(
            BoardCoord(1, 7), BoardCoord(2, 7), BoardCoord(3, 7), BoardCoord(4, 7), BoardCoord(5, 7)
        ),
        PlayerColor.YELLOW to listOf(
            BoardCoord(7, 13), BoardCoord(7, 12), BoardCoord(7, 11), BoardCoord(7, 10), BoardCoord(7, 9)
        ),
        PlayerColor.BLUE to listOf(
            BoardCoord(13, 7), BoardCoord(12, 7), BoardCoord(11, 7), BoardCoord(10, 7), BoardCoord(9, 7)
        )
    )

    /**
     * Fractional grid positions (row, col) for the 4 tokens in each player's 6x6 base yard.
     * Coordinates represent cell centers in 0f..15f board space.
     */
    fun baseTokenCenter(color: PlayerColor, tokenId: Int): Pair<Float, Float> {
        val (baseRowStart, baseColStart) = when (color) {
            PlayerColor.RED -> 0f to 0f
            PlayerColor.GREEN -> 0f to 9f
            PlayerColor.YELLOW -> 9f to 9f
            PlayerColor.BLUE -> 9f to 0f
        }
        val localOffsets = listOf(
            2.15f to 2.15f,
            2.15f to 3.85f,
            3.85f to 2.15f,
            3.85f to 3.85f
        )
        val (dr, dc) = localOffsets[tokenId.coerceIn(0, 3)]
        return (baseRowStart + dr) to (baseColStart + dc)
    }

    /**
     * Fractional center position inside each player's home triangle.
     */
    fun homeTriangleCenter(color: PlayerColor, tokenId: Int): Pair<Float, Float> {
        val jitter = listOf(
            -0.20f to -0.20f,
            -0.20f to 0.20f,
            0.20f to -0.20f,
            0.20f to 0.20f
        )[tokenId.coerceIn(0, 3)]
        val (baseR, baseC) = when (color) {
            PlayerColor.RED -> 7.5f to 6.55f
            PlayerColor.GREEN -> 6.55f to 7.5f
            PlayerColor.YELLOW -> 7.5f to 8.45f
            PlayerColor.BLUE -> 8.45f to 7.5f
        }
        return (baseR + jitter.first * 0.6f) to (baseC + jitter.second * 0.6f)
    }

    /**
     * Returns the exact fractional (row, col) center on the 15x15 grid for a token.
     */
    fun tokenBoardPosition(
        color: PlayerColor,
        tokenId: Int,
        progress: Int,
        outerWrap51: Boolean = false
    ): Pair<Float, Float> {
        return when {
            outerWrap51 -> {
                val trackIdx = (color.startTrackIndex + 51) % Token.TRACK_LENGTH
                val coord = OUTER_TRACK[trackIdx]
                (coord.row + 0.5f) to (coord.col + 0.5f)
            }
            progress == Token.BASE_PROGRESS -> baseTokenCenter(color, tokenId)
            progress in 0..Token.MAX_MAIN_TRACK_PROGRESS -> {
                val trackIdx = (color.startTrackIndex + progress) % Token.TRACK_LENGTH
                val coord = OUTER_TRACK[trackIdx]
                (coord.row + 0.5f) to (coord.col + 0.5f)
            }
            progress in (Token.MAX_MAIN_TRACK_PROGRESS + 1)..(Token.HOME_PROGRESS - 1) -> {
                val colIdx = progress - (Token.MAX_MAIN_TRACK_PROGRESS + 1)
                val coord = HOME_COLUMNS.getValue(color)[colIdx]
                (coord.row + 0.5f) to (coord.col + 0.5f)
            }
            else -> homeTriangleCenter(color, tokenId)
        }
    }

    fun outerTrackCenter(trackIndex: Int): Pair<Float, Float> {
        val coord = OUTER_TRACK[trackIndex.mod(Token.TRACK_LENGTH)]
        return (coord.row + 0.5f) to (coord.col + 0.5f)
    }

    fun isSafeTrackIndex(trackIndex: Int): Boolean = trackIndex in SAFE_TRACK_INDICES

    fun homeColumnCoords(color: PlayerColor): List<BoardCoord> = HOME_COLUMNS.getValue(color)
}
