package dev.seobew.yeopjari

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.seobew.yeopjari.probe.NaviProbe
import dev.seobew.yeopjari.probe.ProbeLog
import dev.seobew.yeopjari.probe.VehicleProbe

/**
 * P0 연동 검증 화면. 기능 구현 전에 SDK 이벤트가 실제로 오는지 로그만 찍는다.
 * 로그 파일: adb shell run-as dev.seobew.yeopjari cat files/probe.log
 */
class MainActivity : AppCompatActivity() {
    private lateinit var log: ProbeLog
    private lateinit var vehicle: VehicleProbe
    private lateinit var navi: NaviProbe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lines = TextView(this).apply { textSize = 14f; setPadding(24, 24, 24, 24) }
        val scroll = ScrollView(this).apply { addView(lines) }
        log = ProbeLog(this) { line ->
            lines.append(line + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("경로 요청") { navi.requestSampleRoute() })
            addView(button("재탐색") { navi.requestReRoute() })
            addView(button("경로 취소") { navi.cancelRoute() })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
            addView(scroll)
        })

        log.log("App", "start", "sdk=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MODEL}")
        vehicle = VehicleProbe(this, log).also { it.start() }
        navi = NaviProbe(this, log).also { it.start() }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    override fun onDestroy() {
        navi.stop()
        vehicle.stop()
        super.onDestroy()
    }
}
