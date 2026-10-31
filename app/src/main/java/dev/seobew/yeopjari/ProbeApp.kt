package dev.seobew.yeopjari

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import dev.seobew.yeopjari.domain.CoachEngine
import dev.seobew.yeopjari.domain.CoachEvent
import dev.seobew.yeopjari.domain.CoachOutput
import dev.seobew.yeopjari.domain.Outcome
import dev.seobew.yeopjari.probe.NaviProbe
import dev.seobew.yeopjari.probe.ProbeLog
import dev.seobew.yeopjari.probe.VehicleProbe
import dev.seobew.yeopjari.scenario.ScenarioPlayer
import dev.seobew.yeopjari.store.JsonPracticeStore
import java.io.File
import java.util.Locale

/**
 * SDK 연결은 프로세스당 한 번만. Activity에 두면 멀티윈도우 리사이즈로 onCreate가 반복돼
 * Vehicle.release()가 다른 인스턴스의 리스너까지 끊는다(P0 실측에서 확인).
 *
 * 이벤트 흐름: SDK 어댑터 / ScenarioPlayer → post() → CoachEngine → 로그·화면·TTS.
 */
class ProbeApp : Application() {
    lateinit var log: ProbeLog
    lateinit var vehicle: VehicleProbe
    lateinit var navi: NaviProbe
    lateinit var store: JsonPracticeStore
    lateinit var engine: CoachEngine
    lateinit var scenarios: ScenarioPlayer

    /** 화면이 구독하는 최근 발화·복기 요약. */
    var lastSay: String = ""
        private set
    var lastSummary: String = ""
        private set
    var onCoachChanged: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        log = ProbeLog(this)
        log.log("App", "process start", "sdk=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MODEL}")

        store = JsonPracticeStore(File(filesDir, "practice.json"))
        engine = CoachEngine(store, emit = ::onOutput)
        scenarios = ScenarioPlayer(this, ::post)
        vehicle = VehicleProbe(this, log, ::post).also { it.start() }
        navi = NaviProbe(this, log, ::post).also { it.start() }

        // Pleos TTS SDK는 Playground에서 '준비 중'. 플랫폼 TTS 엔진이 있으면 그걸로 소리만 낸다 — 없으면 화면 텍스트만.
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS && tts?.setLanguage(Locale.KOREAN).let { it != null && it >= 0 }
            log.log("TTS", "platform engine", if (ttsReady) "ready (${tts?.defaultEngine})" else "unavailable status=$status")
        }
    }

    /** 모든 입력은 메인 스레드에서 엔진으로 들어간다 — SDK 콜백 스레드가 제각각이라 순서를 한 줄로 세운다. */
    fun post(event: CoachEvent) {
        main.post {
            log.log("Event", event::class.simpleName ?: "?", event.toString().substringAfter('('))
            engine.on(event)
        }
    }

    private fun onOutput(out: CoachOutput) {
        when (out) {
            is CoachOutput.Say -> {
                lastSay = out.text
                log.log("Coach", "say L${out.level}", out.text)
                if (ttsReady) tts?.speak(out.text, TextToSpeech.QUEUE_FLUSH, null, "coach")
            }
            is CoachOutput.SegmentOpened -> log.log("Coach", "open", "${out.key.ordinal}:${out.key.direction} level=${out.level} announced=${out.announced}")
            is CoachOutput.SegmentClosed -> log.log("Coach", "close", "${out.key.ordinal}:${out.key.direction} ${out.outcome} → level=${out.after.level} streak=${out.after.confirmedStreak}${if (out.after.graduated) " 졸업" else ""}")
            is CoachOutput.Summary -> {
                val evaluated = out.results.filter { it.outcome != Outcome.EXCLUDED }
                val confirmed = evaluated.count { it.outcome == Outcome.CONFIRMED }
                val excluded = out.results.size - evaluated.size
                val next = out.results.firstOrNull { it.outcome == Outcome.UNCONFIRMED }
                    ?.let { "다음 연습: ${it.key.ordinal}번째 ${if (it.key.direction.name == "LEFT") "좌회전" else "우회전"}(${it.key.description}) 깜빡이" }
                    ?: "다음 연습: 같은 경로를 한 번 더"
                lastSummary = "깜빡이 확인 $confirmed / 평가 ${evaluated.size} (제외 $excluded) · $next"
                lastSay = ""
                log.log("Coach", "summary", lastSummary)
            }
        }
        onCoachChanged?.invoke()
    }
}
