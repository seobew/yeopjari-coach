package dev.seobew.yeopjari.store

import dev.seobew.yeopjari.domain.LevelState
import dev.seobew.yeopjari.domain.PracticeStore
import dev.seobew.yeopjari.domain.SegmentKey
import org.json.JSONObject
import java.io.File

/** 구간별 안내 단계를 filesDir/practice.json 에 저장. 쓸 때마다 전체를 다시 쓴다 (ponytail: 구간 수가 적다). */
class JsonPracticeStore(private val file: File) : PracticeStore {
    private val map = LinkedHashMap<String, LevelState>()

    init {
        if (file.exists()) runCatching {
            val root = JSONObject(file.readText())
            root.keys().forEach { k ->
                val o = root.getJSONObject(k)
                map[k] = LevelState(o.getInt("level"), o.getInt("streak"), o.optBoolean("graduated"))
            }
        }
    }

    override fun get(key: SegmentKey) = map[key.storeKey()] ?: LevelState()

    override fun put(key: SegmentKey, state: LevelState) {
        map[key.storeKey()] = state
        save()
    }

    fun clear() {
        map.clear()
        file.delete()
    }

    fun snapshot(): Map<String, LevelState> = map.toMap()

    private fun save() {
        val root = JSONObject()
        map.forEach { (k, s) ->
            root.put(k, JSONObject().put("level", s.level).put("streak", s.confirmedStreak).put("graduated", s.graduated))
        }
        file.writeText(root.toString())
    }
}
