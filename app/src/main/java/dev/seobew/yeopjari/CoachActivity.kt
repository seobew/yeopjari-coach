package dev.seobew.yeopjari

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.seobew.yeopjari.coach.PracticeSession
import dev.seobew.yeopjari.domain.Outcome

/**
 * 출시용 화면 3개. 주행 중에는 한 줄 + 발화 + 일시정지만 둔다 (1~2초 안에 읽히게, 움직이는 요소 없음).
 * 글자는 18sp 이상, 버튼 높이 72dp 이상 — 한 손 조작.
 */
class CoachActivity : AppCompatActivity() {
    private val app get() = application as ProbeApp
    private val session get() = app.session
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        app.onCoachChanged = ::render
        render()
    }

    override fun onPause() {
        app.onCoachChanged = null
        super.onPause()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(when (val s = session.screen) {
            is PracticeSession.Screen.Select -> selectScreen()
            is PracticeSession.Screen.Driving -> drivingScreen(s)
            is PracticeSession.Screen.Review -> reviewScreen(s)
        })
    }

    private fun selectScreen(): View = column {
        addView(text("옆자리 코치", 28f, bold = true))
        addView(text("반복할수록 코치 안내가 줄어드는 경로 연습", 18f, dim = true))
        addView(gap(24))

        val dest = session.destination?.let { "내비 목적지: $it" } ?: "내비 목적지: 없음"
        val gear = when (session.gear?.name) { "PARK" -> "P"; "DRIVE" -> "D"; null -> "?"; else -> "–" }
        val belt = when (session.belted) { true -> "착용"; false -> "미착용"; null -> "?" }
        addView(text("$dest · 기어 $gear · 벨트 $belt", 18f))
        addView(gap(16))

        val blocker = session.blocker()
        // 막힌 이유를 버튼에 그대로 보여준다. 비활성 버튼은 롱프레스를 못 받아 enabled로 두고 클릭만 무시한다.
        addView(bigButton(blocker ?: "지금 내비 경로로 연습 시작", enabled = true) { if (session.blocker() == null) session.start() }
            .apply { alpha = if (blocker == null) 1f else 0.6f; setOnLongClickListener { session.devBypass = true; render(); true } })
        addView(text("오늘 목표: 회전 전 깜빡이 · 안내는 예고만, 지시하지 않아요", 16f, dim = true))
        addView(gap(24))

        val stats = session.routeStats()
        addView(text(if (stats.isEmpty()) "연습한 경로가 아직 없어요" else "연습한 경로", 20f, bold = true))
        stats.forEach { st ->
            addView(text("${st.routeId} · 구간 ${st.segments} · 안내 줄어든 구간 ${st.reduced} · 졸업 ${st.graduated}", 18f))
        }
        addView(gap(24))
        addView(Button(this@CoachActivity).apply {
            text = "개발자 화면"; textSize = 14f
            setOnClickListener { startActivity(Intent(this@CoachActivity, MainActivity::class.java)) }
        })
    }

    private fun drivingScreen(s: PracticeSession.Screen.Driving): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(16), dp(24), dp(16))
        addView(text("연습 중 · ${s.routeId} · 목표: 회전 전 깜빡이", 18f, dim = true))
        addView(TextView(this@CoachActivity).apply {
            text = if (session.paused) "일시정지" else session.lastSay
            textSize = 36f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        addView(bigButton(if (session.paused) "계속" else "일시정지", enabled = true) { session.togglePause() })
    }

    private fun reviewScreen(s: PracticeSession.Screen.Review): View = column {
        val results = s.summary.results
        val evaluated = results.filter { it.outcome != Outcome.EXCLUDED }
        val confirmed = evaluated.count { it.outcome == Outcome.CONFIRMED }
        addView(text("주차 확인 · 오늘 연습 복기", 18f, dim = true))
        addView(text("깜빡이 확인 $confirmed / 평가 ${evaluated.size} (제외 ${results.size - evaluated.size})", 28f, bold = true))
        addView(gap(8))
        val next = results.firstOrNull { it.outcome == Outcome.UNCONFIRMED }
        addView(text(next?.let { "다음 연습: ${it.key.ordinal}번째 ${PracticeSession.directionWord(it.key.direction)}(${it.key.description}) 깜빡이" }
            ?: "다음 연습: 같은 경로를 한 번 더", 22f))
        addView(gap(24))
        addView(text("구간별 안내", 18f, bold = true))
        results.forEach { r ->
            val outcome = when (r.outcome) { Outcome.CONFIRMED -> "확인"; Outcome.UNCONFIRMED -> "미확인"; Outcome.EXCLUDED -> "제외" }
            val level = when (r.after.level) { 2 -> "전체 예고"; 1 -> "짧은 예고"; else -> if (r.after.graduated) "졸업" else "말하지 않음" }
            addView(text("${r.key.ordinal}. ${PracticeSession.directionWord(r.key.direction)} ${r.key.description} · $outcome → 다음엔 $level", 18f))
        }
        addView(gap(24))
        addView(bigButton("닫기", enabled = true) { session.close() })
    }

    private fun column(build: LinearLayout.() -> Unit): View = ScrollView(this).apply {
        addView(LinearLayout(this@CoachActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(24))
            build()
        })
    }

    private fun text(s: String, sp: Float, bold: Boolean = false, dim: Boolean = false) = TextView(this).apply {
        text = s; textSize = sp
        if (bold) setTypeface(null, Typeface.BOLD)
        if (dim) alpha = 0.7f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun bigButton(label: String, enabled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 20f; isEnabled = enabled; isAllCaps = false
        minimumHeight = dp(72)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun gap(dpValue: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dpValue))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
