package dev.seobew.yeopjari.probe

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import dev.seobew.yeopjari.domain.CoachEvent
import dev.seobew.yeopjari.domain.GearState
import dev.seobew.yeopjari.domain.SignalState

/**
 * Vehicle SDK → CoachEvent 어댑터. 방향지시등 변경 구독·상태 조회, 기어(주차 확인), 운전석 벨트.
 * 리스너 파라미터는 SDK가 @Nullable로 선언해 Kotlin에서 `?`가 필요하다.
 */
class VehicleProbe(
    context: Context,
    private val log: ProbeLog,
    private val sink: (CoachEvent) -> Unit,
    private val onBelt: (Boolean) -> Unit = {},
) {
    private val vehicle = Vehicle(context.applicationContext)

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            log.log("TurnSignal", "updated", state)
            state?.let { sink(CoachEvent.TurnSignalChanged(now(), it.toDomain())) }
                ?: sink(CoachEvent.SignalError(now(), "null turn signal state"))
        }
        override fun onFailed(e: Exception) {
            log.log("TurnSignal", "listener FAILED", e)
            sink(CoachEvent.SignalError(now(), e.toString()))
        }
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            log.log("Gear", "selected", gear)
            gear?.let { sink(CoachEvent.GearChanged(now(), it.toDomain())) }
        }
        override fun onFailed(e: Exception) = log.log("Gear", "listener FAILED", e)
    }

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val beltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) {
            log.log("SeatBelt", "driver updated", buckled)
            buckled?.let(onBelt)
        }
        override fun onFailed(e: Exception) = log.log("SeatBelt", "listener FAILED", e)
    }

    fun start() {
        runCatching { vehicle.initialize() }
            .onFailure { log.log("Vehicle", "initialize FAILED", it); return }
        log.log("Vehicle", "initialized")

        vehicle.getTurnSignal().apply {
            checkTurnSignalStateCapabilities(
                { log.log("TurnSignal", "capability", it) },
                { log.log("TurnSignal", "capability FAILED", it) })
            getCurrentTurnSignalState(
                { log.log("TurnSignal", "current", it)
                  it?.let { v -> sink(CoachEvent.TurnSignalChanged(now(), v.toDomain())) }
                      ?: sink(CoachEvent.SignalError(now(), "null turn signal state")) },
                { log.log("TurnSignal", "current FAILED", it); sink(CoachEvent.SignalError(now(), it.toString())) })
            registerTurnSignalState(turnSignalListener)
        }

        vehicle.getGear().apply {
            getCurrentGearSelectionState(
                { log.log("Gear", "current", it); it?.let { g -> sink(CoachEvent.GearChanged(now(), g.toDomain())) } },
                { log.log("Gear", "current FAILED", it) })
            registerGearSelection(gearListener)
        }

        vehicle.getSeat().apply {
            getCurrentSeatBeltBuckledState(driverSeat,
                { log.log("SeatBelt", "driver buckled", it); it?.let(onBelt) },
                { log.log("SeatBelt", "driver FAILED", it) })
            registerSeatBeltBuckledState(driverSeat, beltListener)
        }
    }

    fun stop() {
        runCatching {
            vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getSeat().unregisterSeatBeltBuckledState(driverSeat, beltListener)
            vehicle.release()
        }.onFailure { log.log("Vehicle", "release FAILED", it) }
    }

    private fun now() = System.currentTimeMillis()

    private fun TurnSignalValue.toDomain() = when (this) {
        TurnSignalValue.LEFT -> SignalState.LEFT
        TurnSignalValue.RIGHT -> SignalState.RIGHT
        else -> SignalState.OFF
    }

    private fun VehicleGear.toDomain() = when (this) {
        VehicleGear.GEAR_PARK -> GearState.PARK
        VehicleGear.GEAR_DRIVE -> GearState.DRIVE
        else -> GearState.OTHER
    }
}
