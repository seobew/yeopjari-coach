package dev.seobew.yeopjari.probe

import android.content.Context
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import dev.seobew.yeopjari.domain.CoachEvent
import dev.seobew.yeopjari.domain.Turn
import dev.seobew.yeopjari.domain.TurnDirection

/**
 * NaviHelper SDK → CoachEvent 어댑터.
 * 실측(10/31): TBTInfo.distance는 m, 경로 설정 시 회전 목록이 한 번에 온다. 주행 중 갱신 주기는 미확인.
 */
class NaviProbe(context: Context, private val log: ProbeLog, private val sink: (CoachEvent) -> Unit) {
    private val navi = NaviHelper(context.applicationContext)
    private var routeSeq = 0

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            log.log("Navi", "onTBTInfo", tbtInfoList.joinToString { "${it.type}/${it.distance}/${it.description}" })
            sink(CoachEvent.TbtUpdated(now(), tbtInfoList.map { Turn(it.type.toDirection(), it.distance, it.description) }))
        }
        override fun onNavigationStatus(status: NavigationStatus) = log.log("Navi", "onNavigationStatus", status)
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log.log("Navi", "onRouteStateInfo", routeStateInfo)
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            log.log("Navi", "onRouteStarted", routeStartInfo)
            // ponytail: 경로 식별은 목적지 이름 + 세션 순번. 같은 경로를 같은 키로 묶으려면 사용자가 고른 연습 경로 id로 바꿔야 한다.
            sink(CoachEvent.RouteStarted(now(), "${routeStartInfo.name}#${++routeSeq}"))
        }
        override fun onRoutingCompleted() = log.log("Navi", "onRoutingCompleted")
        override fun onReRouteEnded() {
            log.log("Navi", "onReRouteEnded")
            sink(CoachEvent.RerouteEnded(now()))
        }
        override fun onRouteEnded() = log.log("Navi", "onRouteEnded")
        override fun onRouteCancelled() {
            log.log("Navi", "onRouteCancelled")
            sink(CoachEvent.RouteCancelled(now()))
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            log.log("Navi", "onDestinationArrived", destinationArrivedInfo)
            sink(CoachEvent.DestinationArrived(now()))
        }
        override fun onError(errorCode: NaviErrorCode) = log.log("Navi", "onError", errorCode)
    }

    fun start() {
        navi.initialize(
            onConnected = {
                log.log("Navi", "connected")
                navi.addListener(listener)
                navi.getRouteStateInfo()
                navi.getTBTInfo()
            },
            onFailed = { log.log("Navi", "initialize FAILED", it) })
    }

    /** Pleos 샘플의 테스트 좌표(42dot 본사, 경유지 소프트웨어드림센터)로 경로 요청. */
    fun requestSampleRoute() {
        log.log("Navi", "requestRouteWithWaypoints")
        navi.requestRouteWithWaypoints(
            RouteWithWaypointsRequest(
                destination = RouteInfo(
                    latitude = 37.48544722, longitude = 127.03636666,
                    poiId = "36khp37hh8gqcm5nqhe5", poiName = "42dot", poiSubId = "0",
                    address = "서울시 강남구 남부순환로 2621"),
                waypoints = listOf(
                    RequestWaypointInfo(
                        latitude = 37.41275833, longitude = 127.09481944,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "36khp2hdj2cfcm5nqhe3", poiName = "소프트웨어드림센터", poiSubId = "0",
                        address = "경기도 성남시 수정구 창업로40번길 6"))))
    }

    fun requestReRoute() {
        log.log("Navi", "requestReRoute")
        navi.requestReRoute()
    }

    fun cancelRoute() {
        log.log("Navi", "cancelRoute")
        navi.cancelRoute()
    }

    fun stop() {
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }.onFailure { log.log("Navi", "release FAILED", it) }
    }

    private fun now() = System.currentTimeMillis()

    private fun TurnType.toDirection() = when (this) {
        TurnType.TURN_LEFT, TurnType.TURN_SHARP_L, TurnType.TURN_UTURN -> TurnDirection.LEFT
        TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnDirection.RIGHT
        else -> TurnDirection.OTHER
    }
}
