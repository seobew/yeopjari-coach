package dev.seobew.yeopjari.coach

import dev.seobew.yeopjari.domain.CoachEvent
import dev.seobew.yeopjari.domain.CoachOutput
import dev.seobew.yeopjari.domain.GearState
import dev.seobew.yeopjari.domain.LevelState
import dev.seobew.yeopjari.domain.Outcome
import dev.seobew.yeopjari.domain.TurnDirection

/**
 * 화면 상태 기계. 엔진(CoachEngine)과 화면(CoachActivity) 사이에서
 * "지금 무엇을 보여줄지"만 정한다. 판정은 하지 않는다.
 *
 * Select(정차·경로 선택) → Driving(주행 중 한 줄) → Review(주차 후 복기) → Select
 */
class PracticeSession(
    private val snapshot: () -> Map<String, LevelState>,
    private val emit: (CoachEvent) -> Unit,
    private val onChange: () -> Unit,
) {
    sealed class Screen {
        object Select : Screen()
        data class Driving(val routeId: String) : Screen()
        data class Review(val routeId: String?, val summary: CoachOutput.Summary) : Screen()
    }

    data class RouteStat(val routeId: String, val segments: Int, val reduced: Int, val graduated: Int)

    var screen: Screen = Screen.Select
        private set
    var gear: GearState? = null
        private set
    var belted: Boolean? = null
        private set
    var destination: String? = null
        private set
    var paused = false
        private set
    var lastSay = ""
        private set
    var devBypass = false

    fun onEvent(event: CoachEvent) {
        when (event) {
            is CoachEvent.GearChanged -> gear = event.gear
            is CoachEvent.RouteStarted -> { screen = Screen.Driving(event.routeId); lastSay = ""; paused = false }
            is CoachEvent.RouteCancelled -> if (screen is Screen.Driving) { screen = Screen.Select; lastSay = "" }
            else -> return
        }
        onChange()
    }

    fun onOutput(out: CoachOutput) {
        when (out) {
            is CoachOutput.Say -> lastSay = out.text
            is CoachOutput.SegmentClosed -> if (out.outcome == Outcome.CONFIRMED && lastSay.isNotEmpty()) lastSay = ""
            is CoachOutput.Summary -> { screen = Screen.Review(out.routeId, out); lastSay = "" }
            else -> return
        }
        onChange()
    }

    fun onBelt(buckled: Boolean) { belted = buckled; onChange() }
    fun onDestination(name: String?) { destination = name; onChange() }

    /** 시작 못 하는 이유. null이면 시작 가능. */
    fun blocker(): String? = when {
        destination.isNullOrBlank() -> "내비에서 목적지를 먼저 정해 주세요"
        gear != GearState.PARK && !devBypass -> "정차(기어 P) 상태에서 시작해요"
        belted != true && !devBypass -> "안전벨트를 매면 시작할 수 있어요"
        else -> null
    }

    fun start() {
        val id = destination ?: return
        emit(CoachEvent.RouteStarted(System.currentTimeMillis(), id))
    }

    fun togglePause() { paused = !paused; if (paused) lastSay = ""; onChange() }

    fun close() { screen = Screen.Select; lastSay = ""; onChange() }

    /** 재탐색 접미사(#rN)를 떼고 경로별로 묶은 통계. */
    fun routeStats(): List<RouteStat> = snapshot().entries
        .groupBy { it.key.substringBefore('|').substringBefore('#') }
        .map { (route, entries) ->
            RouteStat(route, entries.size,
                entries.count { it.value.level < LevelState.FULL },
                entries.count { it.value.graduated })
        }
        .sortedBy { it.routeId }

    companion object {
        fun directionWord(d: TurnDirection) = if (d == TurnDirection.LEFT) "좌회전" else "우회전"
    }
}
