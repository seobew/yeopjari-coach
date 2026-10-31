package dev.seobew.yeopjari.domain

/** 기획서 3장 "기록: 관측 사실과 해석을 나눈다"의 세 결과. */
enum class Outcome { CONFIRMED, UNCONFIRMED, EXCLUDED }

/**
 * 반복 주행에서 같은 회전 구간을 맞추는 키 (기획서: 회전 순번 · 종류 · TBT 안내 문구).
 * 재탐색 뒤에는 routeId에 접미사가 붙어 이전 기록과 섞이지 않는다.
 */
data class SegmentKey(
    val routeId: String,
    val ordinal: Int,
    val direction: TurnDirection,
    val description: String,
) {
    fun storeKey() = "$routeId|$ordinal|$direction|$description"
}

/**
 * 구간별 안내 단계. 2=전체 예고, 1=짧은 예고, 0=말하지 않음 (초기 실험 설정: 확인 2회 연속 ↓, 미확인 1회 ↑).
 */
data class LevelState(val level: Int = FULL, val confirmedStreak: Int = 0, val graduated: Boolean = false) {
    fun apply(outcome: Outcome): LevelState = when (outcome) {
        Outcome.EXCLUDED -> this
        Outcome.UNCONFIRMED -> LevelState(level = minOf(FULL, level + 1), confirmedStreak = 0, graduated = false)
        Outcome.CONFIRMED -> {
            val streak = confirmedStreak + 1
            if (streak < STREAK_TO_REDUCE) copy(confirmedStreak = streak)
            else if (level > SILENT) LevelState(level = level - 1, confirmedStreak = 0, graduated = false)
            else LevelState(level = SILENT, confirmedStreak = 0, graduated = true)
        }
    }

    companion object {
        const val FULL = 2
        const val SHORT = 1
        const val SILENT = 0
        const val STREAK_TO_REDUCE = 2
    }
}

/** 구간 기록 저장소. 테스트는 메모리, 앱은 JSON 파일. */
interface PracticeStore {
    fun get(key: SegmentKey): LevelState
    fun put(key: SegmentKey, state: LevelState)
}

class InMemoryPracticeStore : PracticeStore {
    val map = LinkedHashMap<String, LevelState>()
    override fun get(key: SegmentKey) = map[key.storeKey()] ?: LevelState()
    override fun put(key: SegmentKey, state: LevelState) { map[key.storeKey()] = state }
}
