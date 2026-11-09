package tools.mo3ta.salo.data.community

import tools.mo3ta.salo.BuildConfig

internal actual fun experiencesApiLocalConfig(): ExperiencesApiLocalConfig =
    ExperiencesApiLocalConfig(
        debugOverride = if (BuildConfig.DEBUG) BuildConfig.EXPERIENCES_API_BASE_URL else "",
        fetchImmediately = BuildConfig.DEBUG,
    )
