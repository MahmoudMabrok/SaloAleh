package tools.mo3ta.salo.data.time

import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

interface NetworkTimeProvider {
    fun prime()
    fun getCompetitionWindow(): MohamedLoversCompetitionWindow
}
