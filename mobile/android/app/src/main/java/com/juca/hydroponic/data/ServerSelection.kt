package com.juca.hydroponic.data

object ServerSelection {
    // A pending operation must keep its original server, including across application upgrades.
    fun origin(defaultOrigin: String, pendingOrigin: String?): String = pendingOrigin ?: defaultOrigin
}
