package com.aifacemate.face.vision

import com.aifacemate.face.capability.DeviceEnvironment
import com.aifacemate.face.capability.ModelStore
import com.aifacemate.face.capability.Provider

internal actual fun platformVisionProviders(store: ModelStore, environment: DeviceEnvironment): List<Provider<*>> =
    onnxVisionProviders(store, environment)
