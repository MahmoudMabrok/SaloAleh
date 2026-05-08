package tools.mo3ta.salo.data.time

import android.content.Context
import kotlinx.datetime.Clock
import tools.mo3ta.salo.domain.MohamedLoversCompetitionWindow

class KronosNetworkTimeProvider(context: Context) : NetworkTimeProvider {

    override fun prime() = Unit

    override fun getCompetitionWindow(): MohamedLoversCompetitionWindow =
        buildCompetitionWindow(Clock.System.now())
}
