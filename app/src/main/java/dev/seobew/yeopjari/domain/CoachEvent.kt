package dev.seobew.yeopjari.domain

/** 회전 방향. 깜빡이 평가 대상은 LEFT/RIGHT뿐이다. */
enum class TurnDirection { LEFT, RIGHT, OTHER }

enum class SignalState { OFF, LEFT, RIGHT }

enum class GearState { PARK, DRIVE, OTHER }

/** 내비 TBT 한 항목. distanceM 단위는 m (에뮬레이터 실측으로 확정). */
data class Turn(val direction: TurnDirection, val distanceM: Double, val description: String)

/**
 * 평가 엔진이 보는 유일한 입력. SDK 콜백이든 시나리오 파일이든 여기로 모인다.
 * atMs는 이벤트 발생 시각(ms). 엔진은 순서만 보고 시각은 기록용으로만 쓴다.
 */
sealed class CoachEvent {
    abstract val atMs: Long

    data class RouteStarted(override val atMs: Long, val routeId: String) : CoachEvent()
    data class TbtUpdated(override val atMs: Long, val turns: List<Turn>) : CoachEvent()
    data class TurnSignalChanged(override val atMs: Long, val state: SignalState) : CoachEvent()
    data class GearChanged(override val atMs: Long, val gear: GearState) : CoachEvent()
    data class RerouteEnded(override val atMs: Long) : CoachEvent()
    data class RouteCancelled(override val atMs: Long) : CoachEvent()
    data class DestinationArrived(override val atMs: Long) : CoachEvent()
    /** 방향지시등 구독·조회 실패 등 관측 신뢰성이 깨진 순간. */
    data class SignalError(override val atMs: Long, val reason: String) : CoachEvent()
}
