package tools.mo3ta.salo.data.community

import kotlin.native.Platform

internal actual fun experiencesApiLocalConfig(): ExperiencesApiLocalConfig =
    ExperiencesApiLocalConfig(
        debugOverride = "",
        fetchImmediately = Platform.isDebugBinary,
    )
