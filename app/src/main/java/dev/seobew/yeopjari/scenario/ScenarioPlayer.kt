package dev.seobew.yeopjari.scenario

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.seobew.yeopjari.domain.CoachEvent
import dev.seobew.yeopjari.domain.GearState
import dev.seobew.yeopjari.domain.LevelState
import dev.seobew.yeopjari.domain.Outcome
import dev.seobew.yeopjari.domain.PracticeStore
import dev.seobew.yeopjari.domain.SegmentKey
import dev.seobew.yeopjari.domain.SignalState
import dev.seobew.yeopjari.domain.Turn
import dev.seobew.yeopjari.domain.TurnDirection
import org.json.JSONObject

/**
 * assets/scenarios/ 의 JSON을 시간순으로 재생해 CoachEvent로 흘린다 (가상 TBT 입력 모드).
 * 에뮬레이터에서 차량 이동을 만들 수 없어(내비가 NMEA만 쓰고 HAL 체크섬이 틀림) 주행 중 TBT 갱신은 이걸로 대신한다.
 *
 * 파일 형식:
 *  { "name", "routeId", "history": [{ordinal, direction, description, outcomes:[...]}],
 *    "events": [{ "t": ms, "type": "routeStarted|tbt|signal|gear|reroute|cancel|arrived|error", ... }] }
 *  tbt 항목은 "turns": [[direction, distanceM, description], ...]
 */
class ScenarioPlayer(private val context: Context, private val sink: (CoachEvent) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = ArrayList<Runnable>()

    fun list(): List<String> = context.assets.list("scenarios")?.sorted().orEmpty()

    /** [store]에 history를 심은 뒤 이벤트를 예약한다. 반환값은 시나리오 이름. */
    fun play(file: String, store: PracticeStore, speed: Double = 1.0): String {
        cancel()
        val json = JSONObject(context.assets.open("scenarios/$file").bufferedReader().readText())
        val routeId = json.getString("routeId")

        json.optJSONArray("history")?.let { h ->
            for (i in 0 until h.length()) {
                val o = h.getJSONObject(i)
                val key = SegmentKey(routeId, o.getInt("ordinal"), TurnDirection.valueOf(o.getString("direction")), o.getString("description"))
                val outcomes = o.getJSONArray("outcomes")
                var state = LevelState()
                for (j in 0 until outcomes.length()) state = state.apply(Outcome.valueOf(outcomes.getString(j)))
                store.put(key, state)
            }
        }

        val events = json.getJSONArray("events")
        for (i in 0 until events.length()) {
            val o = events.getJSONObject(i)
            val at = (o.optLong("t", 0L) / speed).toLong()
            val r = Runnable { sink(toEvent(o, routeId)) }
            pending.add(r)
            handler.postDelayed(r, at)
        }
        return json.optString("name", file)
    }

    fun cancel() {
        pending.forEach { handler.removeCallbacks(it) }
        pending.clear()
    }

    private fun toEvent(o: JSONObject, routeId: String): CoachEvent {
        val now = System.currentTimeMillis()
        return when (val type = o.getString("type")) {
            "routeStarted" -> CoachEvent.RouteStarted(now, routeId)
            "tbt" -> {
                val arr = o.getJSONArray("turns")
                CoachEvent.TbtUpdated(now, (0 until arr.length()).map { i ->
                    val t = arr.getJSONArray(i)
                    Turn(TurnDirection.valueOf(t.getString(0)), t.getDouble(1), t.getString(2))
                })
            }
            "signal" -> CoachEvent.TurnSignalChanged(now, SignalState.valueOf(o.getString("value")))
            "gear" -> CoachEvent.GearChanged(now, GearState.valueOf(o.getString("value")))
            "reroute" -> CoachEvent.RerouteEnded(now)
            "cancel" -> CoachEvent.RouteCancelled(now)
            "arrived" -> CoachEvent.DestinationArrived(now)
            "error" -> CoachEvent.SignalError(now, o.optString("reason", "scenario"))
            else -> error("unknown scenario event type: $type")
        }
    }
}
