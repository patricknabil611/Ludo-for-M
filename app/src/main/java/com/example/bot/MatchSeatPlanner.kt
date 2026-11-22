package com.example.bot

import com.example.engine.BotDifficulty
import com.example.engine.GameModeId
import com.example.engine.PlayerColor
import com.example.engine.PlayerControllerType
import com.example.engine.PlayerSetup

enum class SeatOccupancy(val label: String) {
    HUMAN("Human"),
    BOT("Bot"),
    EMPTY("Empty")
}

data class SeatConfig(
    val seatIndex: Int,
    val occupancy: SeatOccupancy,
    val nickname: String,
    val color: PlayerColor,
    val botDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    val avatarId: String = ""
)

/**
 * Resolves the 4 setup seats + chosen player count + mode constraints into valid [PlayerSetup] entries.
 */
object MatchSeatPlanner {

    fun buildMatchSetups(
        seats: List<SeatConfig>,
        targetPlayerCount: Int,
        mode: GameModeId = GameModeId.CLASSIC
    ): List<PlayerSetup> {
        require(seats.size == 4) { "Expected 4 seats" }
        val desiredCount = targetPlayerCount.coerceIn(mode.minPlayers, mode.maxPlayers)

        val nonEmpty = seats.filter { it.occupancy != SeatOccupancy.EMPTY }.toMutableList()
        val emptySeats = seats.filter { it.occupancy == SeatOccupancy.EMPTY }.toMutableList()

        val neededBots = (desiredCount - nonEmpty.size).coerceAtLeast(0)
        repeat(neededBots) {
            val slot = emptySeats.removeFirstOrNull() ?: return@repeat
            nonEmpty += slot.copy(
                occupancy = SeatOccupancy.BOT,
                nickname = "Bot ${slot.seatIndex + 1}",
                botDifficulty = BotDifficulty.MEDIUM
            )
        }

        val activeSeats = nonEmpty.take(desiredCount)

        val finalColors = when {
            mode == GameModeId.TEAMS_2V2 -> {
                // Partners sit opposite: Red + Yellow vs Green + Blue
                val used = mutableListOf<PlayerColor>()
                activeSeats.map { seat ->
                    if (seat.color !in used) {
                        used += seat.color
                        seat.color
                    } else {
                        val free = PlayerColor.entries.first { it !in used }
                        used += free
                        free
                    }
                }
            }
            activeSeats.size == 2 -> {
                val c0 = activeSeats[0].color
                listOf(c0, c0.opposite())
            }
            else -> {
                val used = mutableListOf<PlayerColor>()
                activeSeats.map { seat ->
                    if (seat.color !in used) {
                        used += seat.color
                        seat.color
                    } else {
                        val free = PlayerColor.entries.first { it !in used }
                        used += free
                        free
                    }
                }
            }
        }

        return activeSeats.mapIndexed { idx, seat ->
            val defaultName = if (seat.occupancy == SeatOccupancy.BOT) {
                "Bot ${idx + 1}"
            } else {
                "Player ${idx + 1}"
            }
            PlayerSetup(
                seatIndex = seat.seatIndex,
                nickname = seat.nickname.trim().ifEmpty { defaultName },
                color = finalColors[idx],
                controllerType = if (seat.occupancy == SeatOccupancy.BOT) {
                    PlayerControllerType.BOT
                } else {
                    PlayerControllerType.HUMAN_LOCAL
                },
                botDifficulty = seat.botDifficulty,
                avatarId = seat.avatarId
            )
        }
    }
}
