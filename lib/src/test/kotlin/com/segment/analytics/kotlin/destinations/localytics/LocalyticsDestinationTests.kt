package com.segment.analytics.kotlin.destinations.localytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.localytics.androidx.Localytics
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.LenientJson
import io.mockk.*
import io.mockk.impl.annotations.MockK
import junit.framework.Assert.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalyticsDestinationTests {
    @MockK
    lateinit var mockApplication: Application
    @MockK
    lateinit var mockedContext: Context

    @MockK(relaxUnitFun = true)
    lateinit var mockedAnalytics: Analytics

    lateinit var mockedLocalyticsDestination: LocalyticsDestination

    private val sampleLocalyticsSettings: Settings = LenientJson.decodeFromString(
        """
            {
              "integrations": {
                "Localytics": {
                  "appKey": "3ccfac0c5c366f11105f26b-c8ab109c-b6e1-11e2-88e8-005cf8cbabd8",
                  "dimensions": {
                     "test1": "1",
                     "test2": "2"
                     },
                  "sessionTimeoutInterval": -1,
                  "setOrganizationScope": true
                }
              }
            }
        """.trimIndent()
    )
    init {
        MockKAnnotations.init(this)
    }
    @Before
    fun setUp() {
        mockkStatic(Localytics::class)
        mockedLocalyticsDestination = LocalyticsDestination()
        every { mockedAnalytics.configuration.application } returns mockApplication
        every { mockApplication.applicationContext } returns mockedContext
        every { mockApplication.packageName } returns "unknown"
        mockedAnalytics.configuration.application = mockedContext
        mockedLocalyticsDestination.analytics = mockedAnalytics
    }

    @Test
    fun `settings are updated correctly`() {
        // An Localytics example settings
        val localyticsSettings: Settings = sampleLocalyticsSettings
        mockedLocalyticsDestination.update(localyticsSettings, Plugin.UpdateType.Initial)

        /* assertions Localytics config */
        Assertions.assertNotNull(mockedLocalyticsDestination.localyticsSettings)
        with(mockedLocalyticsDestination.localyticsSettings!!) {
            assertEquals(mockedLocalyticsDestination.localyticsSettings!!.appKey, "3ccfac0c5c366f11105f26b-c8ab109c-b6e1-11e2-88e8-005cf8cbabd8")
            assertEquals(mockedLocalyticsDestination.localyticsSettings!!.dimensions.size, 2)
            assertEquals(mockedLocalyticsDestination.localyticsSettings!!.sessionTimeoutInterval, "-1")
            assertEquals(mockedLocalyticsDestination.localyticsSettings!!.setOrganizationScope, true)
        }
        Assertions.assertEquals(mockedLocalyticsDestination.attributeScope, Localytics.ProfileScope.ORGANIZATION)
    }

    @Test
    fun `activity resume handled correctly`() {
        val activity: Activity = mockkClass(Activity::class)
        val intent: Intent = mockkClass(Intent::class)
        every { activity.intent } returns intent
        mockedLocalyticsDestination.onActivityResumed(activity)
        verify { Localytics.openSession() }
        verify { Localytics.upload() }
        verify { Localytics.handleTestMode(intent) }
    }

    @Test
    fun `activity resume with Fragment Activity handled correctly`() {
        updateSettings(sampleLocalyticsSettings)
        val activity: FragmentActivity = mockkClass(FragmentActivity::class)
        val intent: Intent = mockkClass(Intent::class)
        every { activity.intent } returns intent
        mockedLocalyticsDestination.onActivityResumed(activity)
        verify {  Localytics.setInAppMessageDisplayActivity(activity) }
        verify { Localytics.openSession() }
        verify { Localytics.upload() }
        verify { Localytics.handleTestMode(intent) }
    }

    @Test
    fun `activity paused handled correctly`() {
        val activity: Activity = mockkClass(Activity::class)
        val intent: Intent = mockkClass(Intent::class)
        every { activity.intent } returns intent
        mockedLocalyticsDestination.onActivityPaused(activity)
        verify { Localytics.closeSession() }
        verify { Localytics.upload() }
    }

    @Test
    fun `activity paused with Fragment Activity handled correctly`() {
        updateSettings(sampleLocalyticsSettings)
        val activity: FragmentActivity = mockkClass(FragmentActivity::class)
        val intent: Intent = mockkClass(Intent::class)
        every { activity.intent } returns intent
        mockedLocalyticsDestination.onActivityPaused(activity)
        verify { Localytics.dismissCurrentInAppMessage() }
        verify { Localytics.clearInAppMessageDisplayActivity() }
        verify { Localytics.closeSession() }
        verify { Localytics.upload() }
    }

    @Test
    fun `identify handled correctly`() {
        val sampleEvent = IdentifyEvent(
            userId = "User-Id-123",
            traits = buildJsonObject {
            }
        ).apply { context = emptyJsonObject }
        mockedLocalyticsDestination.attributeScope = Localytics.ProfileScope.APPLICATION
        mockedLocalyticsDestination.identify(sampleEvent)
        verify { Localytics.setCustomerId("User-Id-123") }
    }

    @Test
    fun `identify with multiple fields handled correctly`() {
        val sampleEvent = IdentifyEvent(
            userId = "User-Id-123",
            traits = buildJsonObject {
                put("email", "email@.com")
                put("name", "First Last")
                put("firstName", "First")
                put("lastName", "Last")
                put("customKey", "Custom Value")
            }
        ).apply { context = emptyJsonObject }
        mockedLocalyticsDestination.attributeScope = Localytics.ProfileScope.APPLICATION
        mockedLocalyticsDestination.identify(sampleEvent)
        verify { Localytics.setCustomerId("User-Id-123") }
        verify { Localytics.setCustomerEmail("email@.com") }
        verify { Localytics.setIdentifier("customer_name", "First Last") }
        verify { Localytics.setCustomerFullName("First Last") }
        verify { Localytics.setCustomerFirstName("First") }
        verify { Localytics.setCustomerLastName("Last") }
        verify { Localytics.setProfileAttribute("email", "email@.com", Localytics.ProfileScope.APPLICATION) }
        verify { Localytics.setProfileAttribute("name", "First Last", Localytics.ProfileScope.APPLICATION) }
        verify { Localytics.setProfileAttribute("firstName", "First", Localytics.ProfileScope.APPLICATION) }
        verify { Localytics.setProfileAttribute("lastName", "Last", Localytics.ProfileScope.APPLICATION) }
        verify { Localytics.setProfileAttribute("customKey", "Custom Value", Localytics.ProfileScope.APPLICATION) }
    }

    @Test
    fun `identify with Custom Dimensions fields handled correctly`() {
        updateSettings(sampleLocalyticsSettings)
        val sampleEvent = IdentifyEvent(
            userId = "User-Id-123",
            traits = buildJsonObject {
                put("test1", "test1 Value")
                put("test2", "test2 Value")
            }
        ).apply { context = emptyJsonObject }
        mockedLocalyticsDestination.identify(sampleEvent)

        verify { Localytics.setCustomDimension(1, "test1 Value") }
        verify { Localytics.setCustomDimension(2, "test2 Value") }
        verify { Localytics.setProfileAttribute("test1", "test1 Value", Localytics.ProfileScope.ORGANIZATION) }
        verify { Localytics.setProfileAttribute("test2", "test2 Value", Localytics.ProfileScope.ORGANIZATION) }
    }

    @Test
    fun `flush handled correctly`() {
        mockedLocalyticsDestination.flush()
        verify { Localytics.upload() }
    }

    @Test
    fun `screen handled correctly`() {
        val sampleEvent = ScreenEvent(
            name = "Screen 1",
            category = "Category 1",
            properties = emptyJsonObject
        ).apply {
            context = emptyJsonObject
        }
        mockedLocalyticsDestination.screen(sampleEvent)
        verify { Localytics.tagScreen("Screen 1") }
    }

    @Test
    fun `track handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Track 1",
            properties = buildJsonObject {
            }
        ).apply {
            context = emptyJsonObject
        }

        mockedLocalyticsDestination.track(sampleEvent)
        verify { Localytics.tagEvent("Track 1", mapOf()) }
    }

    @Test
    fun `track with revenue handled correctly`() {
        val sampleEvent = TrackEvent(
            event = "Track 1",
            properties = buildJsonObject {
                put("revenue", 10)
            }
        ).apply {
            context = emptyJsonObject
        }
        mockedLocalyticsDestination.track(sampleEvent)
        val expectedAttributes: MutableMap<String, String> = HashMap()
        expectedAttributes["revenue"] = "10"
        verify { Localytics.tagEvent("Track 1", expectedAttributes, 1000) }
    }

    @Test
    fun `track with custom dimensions handled correctly`() {
        updateSettings(sampleLocalyticsSettings)
        val sampleEvent = TrackEvent(
            event = "Track 1",
            properties = buildJsonObject {
                put("revenue", 10)
                put("test1", "track test1 Value")
                put("test2", "track test2 Value")
            }
        ).apply {
            context = emptyJsonObject
        }
        mockedLocalyticsDestination.track(sampleEvent)
        val expectedAttributes: MutableMap<String, String> = HashMap()
        expectedAttributes["revenue"] = "10"
        expectedAttributes["test1"] = "track test1 Value"
        expectedAttributes["test2"] = "track test2 Value"
        verify { Localytics.tagEvent("Track 1", expectedAttributes, 1000) }
        verify { Localytics.setCustomDimension(1, "track test1 Value") }
        verify { Localytics.setCustomDimension(2, "track test2 Value") }
    }

    private fun updateSettings(sampleLocalyticsSettings: Settings) {
        mockedLocalyticsDestination.update(sampleLocalyticsSettings, Plugin.UpdateType.Initial)
    }
}