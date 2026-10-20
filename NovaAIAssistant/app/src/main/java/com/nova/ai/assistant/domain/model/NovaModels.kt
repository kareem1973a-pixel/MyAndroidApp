package com.nova.ai.assistant.domain.model

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val rmsDb: Float = 0f) : VoiceState
    data object Processing : VoiceState
    data class Speaking(val amplitude: Float = 0f) : VoiceState
    data class ExecutingAction(val actionName: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

sealed class NovaAction {
    data class OpenApp(val appName: String) : NovaAction()
    data class OpenUrl(val url: String) : NovaAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : NovaAction()
    data class CreateCalendarEvent(val title: String, val date: String, val hour: Int, val minute: Int) : NovaAction()
    data class ReadNotifications(val dummy: Unit = Unit) : NovaAction()
    data class ToggleDnd(val enable: Boolean = true) : NovaAction()
    data class PrepareCall(val phoneNumber: String, val contactName: String) : NovaAction()
    data class PrepareMessage(val recipient: String, val body: String) : NovaAction()
    data class OpenSettings(val settingType: SettingType) : NovaAction()
    data class QueryBattery(val dummy: Unit = Unit) : NovaAction()
    data class SaveMemory(val key: String, val value: String) : NovaAction()
    data class WebSearch(val query: String) : NovaAction()
}

enum class SettingType { WIFI, BLUETOOTH, SOUND, DISPLAY, GENERAL }
