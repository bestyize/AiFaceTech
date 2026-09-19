package com.aifacemate.face.model

/**
 * 75 个表情位，对齐三张设计图：
 * `04_伴侣机器人表情系统.png`（01-25）、`表情26-50.png`（扩展 I）、`表情51-75.png`（扩展 II）。
 *
 * 第一阶段只精修 [alphaOne] 中的 5 个表情，其余表情保留统一结构，
 * 后续按 Face Pack 逐步替换参数，不需要改动渲染器。
 */
enum class FaceExpression(val cnName: String, val enName: String) {
    // ---------- 基础表情 01-25 ----------
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
    CHARGING("充电", "Charging"),

    // ---------- 扩展表情 I 26-50 ----------
    RELAXED("轻松", "Relaxed"),
    CONTENT("满足", "Content"),
    PLAYFUL("调皮", "Playful"),
    WINK("眨眼", "Wink"),
    ANTICIPATING("期待", "Anticipating"),
    CALM("平静", "Calm"),
    FOCUSED("专注", "Focused"),
    DETERMINED("决心", "Determined"),
    INSPIRED("灵感", "Inspired"),
    REALIZED("恍然", "Realized"),
    SMIRKING("偷笑", "Smirking"),
    AFFECTIONATE("撒娇", "Affectionate"),
    HUG_ME("求抱抱", "Hug Me"),
    NERVOUS("紧张", "Nervous"),
    ALERT("警觉", "Alert"),
    ALARMED("受惊", "Alarmed"),
    BORED("无聊", "Bored"),
    AGGRIEVED("委屈", "Aggrieved"),
    HELPLESS("无奈", "Helpless"),
    GRATEFUL("感谢", "Grateful"),
    ENCOURAGING("鼓励", "Encouraging"),
    APPROVING("赞同", "Approving"),
    REFUSING("拒绝", "Refusing"),
    SECRETIVE("保密", "Secretive"),
    DAYDREAMING("发呆", "Daydreaming"),

    // ---------- 扩展表情 II 51-75 ----------
    CELEBRATING("庆祝", "Celebrating"),
    TRIUMPHANT("凯旋", "Triumphant"),
    MEDITATING("冥想", "Meditating"),
    DREAMING("做梦", "Dreaming"),
    YAWNING("哈欠", "Yawning"),
    MISCHIEVOUS("顽皮", "Mischievous"),
    SERIOUS("认真", "Serious"),
    PROTECTIVE("守护", "Protective"),
    OBSERVING("观察", "Observing"),
    DEVOTED("倾心", "Devoted"),
    APOLOGETIC("道歉", "Apologetic"),
    ASKING_HELP("求助", "Asking Help"),
    COMFORTING("安慰", "Comforting"),
    CHEERING("喝彩", "Cheering"),
    PEEKING("偷看", "Peeking"),
    SLY("坏笑", "Sly"),
    FROZEN("石化", "Frozen"),
    DIZZY("晕乎", "Dizzy"),
    UNWELL("生病", "Unwell"),
    GROGGY("迷糊", "Groggy"),
    SCANNING("侦测", "Scanning"),
    PROCESSING("处理中", "Processing"),
    RECORDING("记录", "Recording"),
    SYNCING("同步", "Syncing"),
    UPDATING("升级", "Updating");

    /** 小写英文 id，用于配置、日志与后续 Face Pack 资源命名。 */
    val id: String get() = name.lowercase()

    companion object {
        /** Alpha 1 需要精修并冻结的高频表情（Idle / Happy / Curious / Sleep / Greeting）。 */
        val alphaOne: List<FaceExpression> = listOf(IDLE, HAPPY, CURIOUS, SLEEPING, GREETING)

        /** 基础表情 01-25。 */
        val basic: List<FaceExpression> = entries.subList(0, 25)

        /** 扩展表情 I 26-50。 */
        val extensionOne: List<FaceExpression> = entries.subList(25, 50)

        /** 扩展表情 II 51-75。 */
        val extensionTwo: List<FaceExpression> = entries.subList(50, 75)
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
