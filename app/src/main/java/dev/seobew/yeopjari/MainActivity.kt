package dev.seobew.yeopjari

import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * P0·P1 검증 화면. SDK 연결은 ProbeApp이 들고 있고, 여기서는 발화·복기·로그를 보여주고 버튼만 둔다.
 * 출시 화면(주행 중 한 줄 + 일시정지)은 아직 아니다.
 */
class MainActivity : AppCompatActivity() {
    private val app get() = application as ProbeApp
    private var onLine: ((String) -> Unit)? = null
    private var onCoach: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val say = TextView(this).apply { textSize = 26f; setTypeface(null, Typeface.BOLD); setPadding(24, 16, 24, 4) }
        val summary = TextView(this).apply { textSize = 16f; setPadding(24, 0, 24, 12) }
        val lines = TextView(this).apply { textSize = 13f; setPadding(24, 8, 24, 24) }
        val scroll = ScrollView(this).apply { addView(lines) }

        onLine = { line ->
            lines.append(line + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
        onCoach = {
            say.text = app.lastSay
            summary.text = app.lastSummary
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("경로 요청") { app.navi.requestSampleRoute() })
            addView(button("재탐색") { app.navi.requestReRoute() })
            addView(button("경로 취소") { app.navi.cancelRoute() })
            app.scenarios.list().forEach { file ->
                addView(button("▶ " + file.removeSuffix(".json")) {
                    val name = app.scenarios.play(file, app.store)
                    app.log.log("Scenario", "play", name)
                })
            }
            addView(button("기록 초기화") { app.store.clear(); app.log.log("Store", "cleared") })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(HorizontalScrollView(this@MainActivity).apply { addView(buttons) })
            addView(say)
            addView(summary)
            addView(scroll)
        })
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    // 콜백은 resume/pause에서만 잡는다. onDestroy에서 지우면 뒤로 가기 때 이미 재개된 CoachActivity의 콜백까지 끊긴다.
    override fun onResume() {
        super.onResume()
        app.log.onLine = onLine
        app.onCoachChanged = onCoach
    }

    override fun onPause() {
        app.log.onLine = null
        app.onCoachChanged = null
        super.onPause()
    }
}
