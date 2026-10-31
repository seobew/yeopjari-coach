package dev.seobew.yeopjari.probe

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener

/**
 * 기획서 8장 리스크 1·5 검증: 방향지시등 변경 구독 vs 상태 조회, 기어 P 신호(주차 확인 후보).
 */
class VehicleProbe(context: Context, private val log: ProbeLog) {
    private val vehicle = Vehicle(context.applicationContext)

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = log.log("TurnSignal", "updated", state)
        override fun onFailed(e: Exception) = log.log("TurnSignal", "listener FAILED", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = log.log("Gear", "selected", gear)
        override fun onFailed(e: Exception) = log.log("Gear", "listener FAILED", e)
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
                { log.log("TurnSignal", "current", it) },
                { log.log("TurnSignal", "current FAILED", it) })
            registerTurnSignalState(turnSignalListener)
        }

        vehicle.getGear().apply {
            getCurrentGearSelectionState(
                { log.log("Gear", "current", it) },
                { log.log("Gear", "current FAILED", it) })
            registerGearSelection(gearListener)
        }

        vehicle.getSeat().getCurrentSeatBeltBuckledState(
            SeatArea(SeatValue.ROW_1_LEFT),
            { log.log("SeatBelt", "driver buckled", it) },
            { log.log("SeatBelt", "driver FAILED", it) })
    }

    fun stop() {
        runCatching {
            vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener)
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        }.onFailure { log.log("Vehicle", "release FAILED", it) }
    }
}
