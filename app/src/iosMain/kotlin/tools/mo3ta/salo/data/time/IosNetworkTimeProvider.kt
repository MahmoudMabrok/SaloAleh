package tools.mo3ta.salo.data.time

import kotlinx.datetime.Clock
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

class IosNetworkTimeProvider : NetworkTimeProvider {
    override fun prime() = Unit
    override fun getCompetitionWindow(): MohamedLoversCompetitionWindow =
        buildCompetitionWindow(Clock.System.now())
}
