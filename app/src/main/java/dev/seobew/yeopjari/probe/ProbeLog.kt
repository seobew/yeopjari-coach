package dev.seobew.yeopjari.probe

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P0 연동 검증용 로그. Logcat + 파일(filesDir/probe.log) + 화면에 같은 줄을 남긴다.
 * 파일은 `adb pull`로 꺼내 기획서 8장 "제출 전 확보할 증거"에 붙인다.
 */
class ProbeLog(context: Context, private val onLine: (String) -> Unit) {
    private val file = File(context.filesDir, "probe.log")
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(source: String, event: String, detail: Any? = null) {
        val line = buildString {
            append(fmt.format(Date())).append(' ')
            append('[').append(source).append("] ").append(event)
            if (detail != null) append(' ').append(detail)
        }
        Log.i(TAG, line)
        runCatching { file.appendText(line + "\n") }
        main.post { onLine(line) }
    }

    companion object {
        const val TAG = "YeopjariProbe"
    }
}
