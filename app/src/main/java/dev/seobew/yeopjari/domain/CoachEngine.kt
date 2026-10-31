package dev.seobew.yeopjari.domain

/** 엔진이 밖으로 내는 것. 발화는 텍스트만 — TTS는 어댑터 몫. */
sealed class CoachOutput {
    /** level 2: 문장, level 1: 알림음 + 한 단어. level 0은 발화 없음. */
    data class Say(val level: Int, val text: String) : CoachOutput()
    data class SegmentOpened(val key: SegmentKey, val level: Int, val announced: Boolean) : CoachOutput()
    data class SegmentClosed(val key: SegmentKey, val outcome: Outcome, val after: LevelState) : CoachOutput()
    /** 주차(기어 P) 확인 뒤 한 번. 복기 카드의 재료. */
    data class Summary(val routeId: String?, val results: List<SegmentClosed>) : CoachOutput()
}

/**
 * 기획서 3장의 평가 규칙 구현. 입력은 CoachEvent뿐, 상태는 PracticeStore뿐.
 *
 * - 평가 구간: TBT 선두 회전(좌/우)의 남은 거리가 [windowOpenM] 이하가 된 시점 ~ 그 회전이 선두에서 빠진 시점.
 *   음성 안내 여부와 무관하게 만든다.
 * - 관측 충분 조건: 구간 시작 시 방향지시등 상태를 알고 있고, 구간 동안 SignalError가 없음.
 * - 제외: 재탐색·경로 취소·오류·연속 회전으로 구간 모호([ambiguousGapM] 안에 다음 좌/우회전).
 */
class CoachEngine(
    private val store: PracticeStore,
    private val windowOpenM: Double = 150.0,
    private val ambiguousGapM: Double = 30.0,
    private val emit: (CoachOutput) -> Unit,
) {
    private var routeId: String? = null
    private var rerouteCount = 0
    private var ordinal = 0
    private var lastHeadKey: String? = null
    private var signal: SignalState? = null
    private var signalHealthy = true
    private var window: Window? = null
    private val session = ArrayList<CoachOutput.SegmentClosed>()

    private class Window(val key: SegmentKey, val headKey: String, var observed: Boolean, var excluded: Boolean)

    fun on(event: CoachEvent) {
        when (event) {
            is CoachEvent.RouteStarted -> {
                closeWindow(forceExcluded = true)
                routeId = event.routeId; rerouteCount = 0; ordinal = 0; lastHeadKey = null
                session.clear()
            }
            is CoachEvent.TbtUpdated -> onTbt(event.turns)
            is CoachEvent.TurnSignalChanged -> {
                // 구독이 다시 값을 주면 관측 신뢰성이 회복된 것으로 본다. 이미 열린 구간의 제외 표시는 유지한다.
                signal = event.state
                signalHealthy = true
                window?.let { w -> if (event.state == w.key.direction.toSignal()) w.observed = true }
            }
            is CoachEvent.SignalError -> {
                signalHealthy = false
                window?.excluded = true
            }
            is CoachEvent.RerouteEnded -> {
                closeWindow(forceExcluded = true)
                rerouteCount++; ordinal = 0; lastHeadKey = null
            }
            is CoachEvent.RouteCancelled -> {
                closeWindow(forceExcluded = true)
                routeId = null; lastHeadKey = null
            }
            is CoachEvent.DestinationArrived -> closeWindow(forceExcluded = false)
            is CoachEvent.GearChanged -> if (event.gear == GearState.PARK && session.isNotEmpty()) {
                closeWindow(forceExcluded = false)
                emit(CoachOutput.Summary(routeId, session.toList()))
                session.clear()
            }
        }
    }

    private fun onTbt(turns: List<Turn>) {
        val head = turns.firstOrNull()
        val headKey = head?.let { "${it.direction}|${it.description}" }
        if (headKey != lastHeadKey) {
            closeWindow(forceExcluded = false)
            if (head != null) ordinal++
            lastHeadKey = headKey
        }
        if (head == null || window != null || routeId == null) return
        if (head.direction == TurnDirection.OTHER || head.distanceM > windowOpenM) return

        val key = SegmentKey(effectiveRouteId(), ordinal, head.direction, head.description)
        val ambiguous = turns.drop(1).firstOrNull { it.direction != TurnDirection.OTHER }
            ?.let { it.distanceM - head.distanceM < ambiguousGapM } ?: false
        val sufficient = signal != null && signalHealthy
        val alreadyOn = signal == head.direction.toSignal()
        val w = Window(key, headKey!!, observed = alreadyOn, excluded = ambiguous || !sufficient)
        window = w

        val level = store.get(key).level
        val announced = !ambiguous && !alreadyOn && level > LevelState.SILENT
        if (announced) emit(CoachOutput.Say(level, phrase(level, head.direction)))
        emit(CoachOutput.SegmentOpened(key, level, announced))
    }

    private fun closeWindow(forceExcluded: Boolean) {
        val w = window ?: return
        window = null
        val outcome = when {
            forceExcluded || w.excluded -> Outcome.EXCLUDED
            w.observed -> Outcome.CONFIRMED
            else -> Outcome.UNCONFIRMED
        }
        val after = store.get(w.key).apply(outcome)
        store.put(w.key, after)
        val closed = CoachOutput.SegmentClosed(w.key, outcome, after)
        session.add(closed)
        emit(closed)
    }

    private fun effectiveRouteId() = if (rerouteCount == 0) routeId!! else "${routeId!!}#r$rerouteCount"

    private fun TurnDirection.toSignal() = when (this) {
        TurnDirection.LEFT -> SignalState.LEFT
        TurnDirection.RIGHT -> SignalState.RIGHT
        TurnDirection.OTHER -> null
    }

    companion object {
        fun phrase(level: Int, direction: TurnDirection): String {
            val word = if (direction == TurnDirection.LEFT) "좌회전" else "우회전"
            return when (level) {
                LevelState.FULL -> "다음 $word, 깜빡이 준비예요."
                LevelState.SHORT -> word
                else -> ""
            }
        }
    }
}
