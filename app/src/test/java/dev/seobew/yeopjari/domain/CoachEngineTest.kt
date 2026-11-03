package dev.seobew.yeopjari.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachEngineTest {
    private val out = ArrayList<CoachOutput>()
    private val store = InMemoryPracticeStore()
    private val engine = CoachEngine(store, windowOpenM = 150.0, ambiguousGapM = 30.0) { out.add(it) }
    private var t = 0L

    private fun right(d: Double, desc: String = "창업로") = Turn(TurnDirection.RIGHT, d, desc)
    private fun left(d: Double, desc: String = "남부순환로") = Turn(TurnDirection.LEFT, d, desc)
    private fun arrive(d: Double) = Turn(TurnDirection.OTHER, d, "도착")

    private fun start(route: String = "home-office") {
        engine.on(CoachEvent.RouteStarted(t++, route))
        engine.on(CoachEvent.TurnSignalChanged(t++, SignalState.OFF))
    }
    private fun tbt(vararg turns: Turn) = engine.on(CoachEvent.TbtUpdated(t++, turns.toList()))
    private fun signal(s: SignalState) = engine.on(CoachEvent.TurnSignalChanged(t++, s))
    private fun closed() = out.filterIsInstance<CoachOutput.SegmentClosed>()
    private fun said() = out.filterIsInstance<CoachOutput.Say>()

    @Test fun `signal inside window is confirmed`() {
        start()
        tbt(right(400.0), arrive(900.0))
        tbt(right(140.0), arrive(640.0))
        signal(SignalState.RIGHT)
        tbt(arrive(500.0))
        assertEquals(Outcome.CONFIRMED, closed().single().outcome)
        assertEquals("다음 우회전, 깜빡이 준비예요.", said().single().text)
    }

    @Test fun `no signal with healthy data is unconfirmed`() {
        start()
        tbt(right(140.0), arrive(640.0))
        tbt(arrive(500.0))
        assertEquals(Outcome.UNCONFIRMED, closed().single().outcome)
    }

    @Test fun `signal already on at window open counts and suppresses announcement`() {
        start()
        signal(SignalState.RIGHT)
        tbt(right(140.0), arrive(640.0))
        tbt(arrive(500.0))
        assertEquals(Outcome.CONFIRMED, closed().single().outcome)
        assertTrue(said().isEmpty())
    }

    @Test fun `window opens only when distance reaches threshold`() {
        start()
        tbt(right(160.0))
        assertTrue(out.filterIsInstance<CoachOutput.SegmentOpened>().isEmpty())
        tbt(right(150.0))
        assertEquals(1, out.filterIsInstance<CoachOutput.SegmentOpened>().size)
    }

    @Test fun `wrong direction signal is not confirmation`() {
        start()
        tbt(right(100.0), arrive(500.0))
        signal(SignalState.LEFT)
        tbt(arrive(400.0))
        assertEquals(Outcome.UNCONFIRMED, closed().single().outcome)
    }

    @Test fun `reroute excludes open window and later keys do not touch original route`() {
        start()
        tbt(right(100.0), arrive(500.0))
        engine.on(CoachEvent.RerouteEnded(t++))
        assertEquals(Outcome.EXCLUDED, closed().single().outcome)
        tbt(left(100.0), arrive(300.0))
        signal(SignalState.LEFT)
        tbt(arrive(200.0))
        assertEquals("home-office#r1", closed()[1].key.routeId)
        assertEquals(LevelState(), store.get(SegmentKey("home-office", 1, TurnDirection.LEFT, "남부순환로")))
    }

    @Test fun `unknown signal state at open is excluded not unconfirmed`() {
        engine.on(CoachEvent.RouteStarted(t++, "r"))
        tbt(right(100.0), arrive(500.0))
        tbt(arrive(400.0))
        assertEquals(Outcome.EXCLUDED, closed().single().outcome)
    }

    @Test fun `signal error during window is excluded`() {
        start()
        tbt(right(100.0), arrive(500.0))
        signal(SignalState.RIGHT)
        engine.on(CoachEvent.SignalError(t++, "binder died"))
        tbt(arrive(400.0))
        assertEquals(Outcome.EXCLUDED, closed().single().outcome)
    }

    @Test fun `signal update after error restores sufficiency for later windows`() {
        start()
        engine.on(CoachEvent.SignalError(t++, "lost"))
        signal(SignalState.OFF)
        tbt(right(100.0), arrive(500.0))
        signal(SignalState.RIGHT)
        tbt(arrive(400.0))
        assertEquals(Outcome.CONFIRMED, closed().single().outcome)
    }

    @Test fun `consecutive turns within gap are ambiguous and excluded`() {
        start()
        tbt(right(100.0), left(120.0, "골목"), arrive(500.0))
        signal(SignalState.RIGHT)
        tbt(arrive(400.0))
        assertEquals(Outcome.EXCLUDED, closed().single().outcome)
        assertTrue(said().isEmpty())
    }

    @Test fun `two confirmations reduce level, one unconfirmed restores, excluded keeps streak`() {
        val key = SegmentKey("home-office", 1, TurnDirection.RIGHT, "창업로")
        fun drive(outcome: Outcome) {
            start()
            tbt(right(100.0), arrive(500.0))
            when (outcome) {
                Outcome.CONFIRMED -> { signal(SignalState.RIGHT); tbt(arrive(400.0)) }
                Outcome.UNCONFIRMED -> tbt(arrive(400.0))
                Outcome.EXCLUDED -> engine.on(CoachEvent.RouteCancelled(t++))
            }
            signal(SignalState.OFF)
        }
        drive(Outcome.CONFIRMED)
        assertEquals(LevelState(2, 1), store.get(key))
        drive(Outcome.EXCLUDED)
        assertEquals(LevelState(2, 1), store.get(key))
        drive(Outcome.CONFIRMED)
        assertEquals(LevelState(1, 0), store.get(key))
        drive(Outcome.CONFIRMED)
        assertEquals("우회전", said().last().text)
        drive(Outcome.CONFIRMED)
        assertEquals(LevelState(0, 0), store.get(key))
        drive(Outcome.CONFIRMED)
        assertEquals(1, out.filterIsInstance<CoachOutput.SegmentOpened>().count { !it.announced && it.level == 0 })
        drive(Outcome.CONFIRMED)
        assertTrue(store.get(key).graduated)
        drive(Outcome.UNCONFIRMED)
        assertEquals(LevelState(1, 0), store.get(key))
        assertFalse(store.get(key).graduated)
    }

    @Test fun `park after drive emits summary of the session`() {
        start()
        tbt(right(100.0), left(500.0), arrive(900.0))
        signal(SignalState.RIGHT)
        tbt(left(400.0), arrive(800.0))
        signal(SignalState.OFF)
        tbt(left(120.0), arrive(520.0))
        tbt(arrive(400.0))
        engine.on(CoachEvent.DestinationArrived(t++))
        engine.on(CoachEvent.GearChanged(t++, GearState.PARK))
        val summary = out.filterIsInstance<CoachOutput.Summary>().single()
        assertEquals(listOf(Outcome.CONFIRMED, Outcome.UNCONFIRMED), summary.results.map { it.outcome })
        assertEquals(listOf(1, 2), summary.results.map { it.key.ordinal })
    }

    @Test fun `park with no closed segment still emits empty summary, but not outside a practice`() {
        engine.on(CoachEvent.GearChanged(t++, GearState.PARK))
        assertTrue(out.filterIsInstance<CoachOutput.Summary>().isEmpty())
        start()
        engine.on(CoachEvent.GearChanged(t++, GearState.PARK))
        assertTrue(out.filterIsInstance<CoachOutput.Summary>().single().results.isEmpty())
        engine.on(CoachEvent.GearChanged(t++, GearState.PARK))
        assertEquals(1, out.filterIsInstance<CoachOutput.Summary>().size)
    }

    @Test fun `practice ended mid window excludes it and summarizes`() {
        start()
        tbt(right(140.0), arrive(640.0))
        engine.on(CoachEvent.PracticeEnded(t++))
        val summary = out.filterIsInstance<CoachOutput.Summary>().single()
        assertEquals(listOf(Outcome.EXCLUDED), summary.results.map { it.outcome })
        assertEquals(LevelState(), store.get(summary.results.single().key))
    }

    @Test fun `pause excludes open window and blocks new ones until resumed`() {
        start()
        tbt(right(140.0), left(600.0), arrive(900.0))
        engine.on(CoachEvent.PauseChanged(t++, true))
        assertEquals(Outcome.EXCLUDED, closed().single().outcome)
        tbt(left(400.0), arrive(700.0))
        tbt(left(120.0), arrive(420.0))
        assertEquals(1, out.filterIsInstance<CoachOutput.SegmentOpened>().size)
        engine.on(CoachEvent.PauseChanged(t++, false))
        tbt(left(100.0), arrive(400.0))
        signal(SignalState.LEFT)
        tbt(arrive(300.0))
        assertEquals(listOf(Outcome.EXCLUDED, Outcome.CONFIRMED), closed().map { it.outcome })
        assertEquals(2, closed().last().key.ordinal)
    }
}
