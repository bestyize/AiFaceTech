package com.aifacemate.face.model

/**
 * 25 个表情位，对齐设计图 `04_伴侣机器人表情系统.png`。
 *
 * 第一阶段只精修 [alphaOne] 中的 5 个表情，其余表情保留统一结构，
 * 后续按 Face Pack 逐步替换参数，不需要改动渲染器。
 */
enum class FaceExpression(val cnName: String, val enName: String) {
    IDLE("平静", "Idle"),
    HAPPY("开心", "Happy"),
    EXCITED("兴奋", "Excited"),
    LAUGHING("大笑", "Laughing"),
    PROUD("得意", "Proud"),
    CURIOUS("好奇", "Curious"),
    THINKING("思考", "Thinking"),
    CONFUSED("困惑", "Confused"),
    SURPRISED("惊讶", "Surprised"),
    SHY("害羞", "Shy"),
    LOVE("喜爱", "Love"),
    SLEEPY("困倦", "Sleepy"),
    SLEEPING("睡眠", "Sleeping"),
    SAD("难过", "Sad"),
    WORRIED("担心", "Worried"),
    ANGRY("生气", "Angry"),
    ANNOYED("不耐烦", "Annoyed"),
    CRYING("哭泣", "Crying"),
    EMBARRASSED("尴尬", "Embarrassed"),
    LISTENING("倾听", "Listening"),
    SPEAKING("说话", "Speaking"),
    GREETING("打招呼", "Greeting"),
    WAITING("等待", "Waiting"),
    OFFLINE("离线", "Offline"),
    CHARGING("充电", "Charging");

    /** 小写英文 id，用于配置、日志与后续 Face Pack 资源命名。 */
    val id: String get() = name.lowercase()

    companion object {
        /** Alpha 1 需要精修并冻结的高频表情（Idle / Happy / Curious / Sleep / Greeting）。 */
        val alphaOne: List<FaceExpression> = listOf(IDLE, HAPPY, CURIOUS, SLEEPING, GREETING)
    }
}

/** 下一个表情（循环）。原型阶段用来在真机屏幕上逐个走查表情。 */
fun FaceExpression.next(): FaceExpression = step(1)

/** 上一个表情（循环）。 */
fun FaceExpression.previous(): FaceExpression = step(-1)

private fun FaceExpression.step(delta: Int): FaceExpression {
    val all = FaceExpression.entries
    val index = (ordinal + delta).mod(all.size)
    return all[index]
}
