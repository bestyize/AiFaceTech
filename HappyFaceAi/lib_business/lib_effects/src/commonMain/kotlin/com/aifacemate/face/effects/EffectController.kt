package com.aifacemate.face.effects

enum class EffectId(val label: String, val durationMillis: Long) {
    Fireworks("烟花庆祝", 3600), Hearts("爱心漂浮", 3000), RainToSun("小雨变晴", 5600), StarReward("星光奖励", 2200),
}

data class EffectScene(val instanceId: String, val effect: EffectId, val startedAtMillis: Long,
    val priority: Int = 10, val seed: Int = 1) {
    fun progress(now: Long) = ((now - startedAtMillis).toFloat() / effect.durationMillis).coerceIn(0f, 1f)
}

/** Bounded single-scene owner. No hardware, UI, network or recognition dependencies. */
class EffectController {
    private var active: EffectScene? = null
    private val seen = ArrayDeque<String>()
    fun play(scene: EffectScene, now: Long): Boolean {
        if (scene.instanceId in seen || scene.startedAtMillis > now || now - scene.startedAtMillis >= scene.effect.durationMillis) return false
        discard(scene.instanceId)
        val current = sceneAt(now)
        if (current != null && (current.effect == scene.effect || current.priority > scene.priority)) return false
        active = scene
        return true
    }
    fun sceneAt(now: Long): EffectScene? {
        if (active?.let { now - it.startedAtMillis >= it.effect.durationMillis } == true) active = null
        return active
    }
    fun clear() { active = null }
    fun discard(instanceId: String) {
        if (instanceId in seen) return
        seen.addLast(instanceId)
        if (seen.size > 32) seen.removeFirst()
    }
}
