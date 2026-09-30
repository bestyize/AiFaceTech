package com.aifacemate.face.behavior

import com.aifacemate.face.model.FaceExpression

internal data class SpeechMeaning(val expression: FaceExpression, val reason: String)

/** Small explicit phrase rules, shared by provisional and final text. No inference from loudness. */
internal fun speechMeaning(input: String): SpeechMeaning? {
    val text = input.trim().lowercase()
        .replace("不难过", "").replace("不伤心", "").replace("不是很难过", "")
        .replace("不可爱", "").replace("不喜欢你", "").replace("不开心吗", "")
    fun has(vararg phrases: String) = phrases.any { it in text }
    return when {
        has("难过", "伤心", "不开心", "心情不好") -> SpeechMeaning(FaceExpression.COMFORTING, "comfort")
        has("真可爱", "好可爱", "你很棒", "真棒", "喜欢你") -> SpeechMeaning(FaceExpression.BASHFUL_SMILE, "praise")
        has("晚安", "要睡了", "我要睡觉") -> SpeechMeaning(FaceExpression.GOOD_NIGHT, "good_night")
        has("谢谢", "感谢你") -> SpeechMeaning(FaceExpression.GRATEFUL, "thanks")
        has("对不起", "抱歉") -> SpeechMeaning(FaceExpression.REASSURED, "apology")
        has("好开心", "很开心", "太高兴", "好消息", "成功了") -> SpeechMeaning(FaceExpression.HAPPY, "good_news")
        has("你好", "早上好", "晚上好", "hello") -> SpeechMeaning(FaceExpression.GREETING, "greeting_text")
        else -> null
    }
}
