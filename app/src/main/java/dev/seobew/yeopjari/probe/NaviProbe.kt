package dev.seobew.yeopjari.probe

import android.content.Context
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
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

/**
 * 기획서 8장 리스크 2·4 검증: TBT 이벤트 주기·distance 단위, 재탐색 알림이 어떤 콜백으로 오는지.
 * 에뮬레이터에 기본 내비 앱이 없으면 initialize 이후 아무 이벤트도 오지 않는다 — 그것도 기록한다.
 */
class NaviProbe(context: Context, private val log: ProbeLog) {
    private val navi = NaviHelper(context.applicationContext)

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            log.log("Navi", "onTBTInfo", tbtInfoList.joinToString { "${it.type}/${it.distance}/${it.description}" })
        }
        override fun onNavigationStatus(status: NavigationStatus) = log.log("Navi", "onNavigationStatus", status)
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log.log("Navi", "onRouteStateInfo", routeStateInfo)
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log.log("Navi", "onRouteStarted", routeStartInfo)
        override fun onRoutingCompleted() = log.log("Navi", "onRoutingCompleted")
        override fun onReRouteEnded() = log.log("Navi", "onReRouteEnded")
        override fun onRouteEnded() = log.log("Navi", "onRouteEnded")
        override fun onRouteCancelled() = log.log("Navi", "onRouteCancelled")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            log.log("Navi", "onDestinationArrived", destinationArrivedInfo)
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
}
