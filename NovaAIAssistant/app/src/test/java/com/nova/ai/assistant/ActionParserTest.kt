package com.nova.ai.assistant

import com.nova.ai.assistant.data.repository.GeminiNovaAiRepository
import com.nova.ai.assistant.domain.model.NovaAction
import com.nova.ai.assistant.domain.model.SettingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ActionParserTest {

    private val repository = GeminiNovaAiRepository(apiKey = "DUMMY_KEY")

    @Test
    fun testParseAlarmAction() {
        val method = repository.javaClass.getDeclaredMethod("parseActionCommand", String::class.java)
        method.isAccessible = true

        val action = method.invoke(repository, "SET_ALARM:6,30,المذاكرة") as? NovaAction.SetAlarm
        assertNotNull(action)
        assertEquals(6, action?.hour)
        assertEquals(30, action?.minute)
        assertEquals("المذاكرة", action?.label)
    }

    @Test
    fun testParseSettingsAction() {
        val method = repository.javaClass.getDeclaredMethod("parseActionCommand", String::class.java)
        method.isAccessible = true

        val action = method.invoke(repository, "OPEN_SETTINGS:WIFI") as? NovaAction.OpenSettings
        assertNotNull(action)
        assertEquals(SettingType.WIFI, action?.settingType)
    }

    @Test
    fun testParseAppAction() {
        val method = repository.javaClass.getDeclaredMethod("parseActionCommand", String::class.java)
        method.isAccessible = true

        val action = method.invoke(repository, "OPEN_APP:YouTube") as? NovaAction.OpenApp
        assertNotNull(action)
        assertEquals("YouTube", action?.appName)
    }
}
