package com.aifacemate.face
import com.aifacemate.face.feature.aibrain.presentation.theme.*
import com.aifacemate.face.feature.aibrain.data.*
import com.aifacemate.face.feature.aibrain.domain.*
import com.aifacemate.face.feature.aibrain.presentation.*

import com.aifacemate.face.feature.aibrain.presentation.AiBrainViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 真机上的 `viewModel()` 用反射实例化 ViewModel，只会去找无参构造函数。
 *
 * 这个测试把「必须保留无参构造」这条约束钉死：一旦有人给 [AiBrainViewModel] 加上
 * 必填或带默认值的构造参数，这里会先失败，而不是等到装到手机上点进 AI 大脑才崩。
 */
class AiBrainViewModelInstantiationTest {

    @Test
    fun viewModelHasParameterlessConstructor() {
        val constructor = AiBrainViewModel::class.java.getDeclaredConstructor()
        assertTrue(constructor.parameterCount == 0)
    }

    @Test
    fun canBeInstantiatedThroughThatConstructor() {
        val viewModel = AiBrainViewModel::class.java.getDeclaredConstructor().newInstance()
        // 构造函数会读一次本地配置；测试环境读不到东西时应当干净地退回默认值。
        assertEquals(
            viewModel.uiState.providers.size,
            viewModel.uiState.providers.distinctBy { it.id }.size,
            "内置服务商目录不应出现重复 id",
        )
    }
}
