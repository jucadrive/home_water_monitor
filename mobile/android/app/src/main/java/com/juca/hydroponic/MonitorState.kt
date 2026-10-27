package com.juca.hydroponic

import com.juca.hydroponic.data.*

enum class Screen { SETTINGS, LIST, CREATE, DETAIL, MAINTENANCE, UNLINKED, SESSION, COMMAND }
data class MonitorState(
    val screen: Screen = Screen.SETTINGS, val sessionParent: Screen = Screen.UNLINKED, val origin: String = "", val device: String = "",
    val busy: Boolean = false, val error: String? = null, val pending: PendingMutation? = null,
    val authenticated: Boolean = false,
    val cultivations: List<Overview> = emptyList(), val moreCultivations: Boolean = false,
    val detail: Overview? = null, val maintenance: List<Maintenance> = emptyList(), val moreMaintenance: Boolean = false,
    val sessions: List<SessionSummary> = emptyList(), val moreSessions: Boolean = false,
    val trend: List<SessionSummary> = emptyList(), val unlinked: List<SessionSummary> = emptyList(),
    val moreUnlinked: Boolean = false, val session: SessionDetail? = null, val command: CommandStatus? = null,
    val deviceStatus: String? = null
)
