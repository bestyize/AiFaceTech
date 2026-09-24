package com.aifacemate.face.model

import kotlin.test.Test
import kotlin.test.assertEquals

class FaceExpressionTest {

    @Test
    fun nextCyclesThroughEveryExpressionAndWrapsAround() {
        var current = FaceExpression.IDLE
        val visited = mutableListOf(current)

        repeat(FaceExpression.entries.size - 1) {
            current = current.next()
            visited += current
        }

        assertEquals(FaceExpression.entries.toList(), visited, "next() 应当按声明顺序遍历所有表情")
        assertEquals(FaceExpression.entries.first(), current.next(), "最后一个表情的下一个应当回到开头")
    }

    @Test
    fun previousIsTheInverseOfNext() {
        FaceExpression.entries.forEach { expression ->
            assertEquals(expression, expression.next().previous())
        }
    }
}
