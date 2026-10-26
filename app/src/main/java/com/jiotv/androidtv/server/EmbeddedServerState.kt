package com.jiotv.androidtv.server

sealed interface EmbeddedServerState {
    object Stopped : EmbeddedServerState
    data class PreparingBinary(val progressPercent: Int, val message: String) : EmbeddedServerState
    object Starting : EmbeddedServerState
    data class Running(val host: String, val port: Int, val pid: Long = 0, val isReady: Boolean = true) : EmbeddedServerState
    data class Error(val errorMessage: String, val canRetry: Boolean = true) : EmbeddedServerState
}
