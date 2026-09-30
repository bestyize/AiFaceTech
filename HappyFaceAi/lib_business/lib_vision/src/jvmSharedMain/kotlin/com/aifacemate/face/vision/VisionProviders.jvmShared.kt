package com.aifacemate.face.vision

import com.aifacemate.face.capability.ModelStore
import com.aifacemate.face.capability.Provider

internal actual fun platformVisionProviders(store: ModelStore): List<Provider<*>> = onnxVisionProviders(store)
