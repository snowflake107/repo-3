package com.segment.analytics.kotlin.destinations.localytics

import android.app.Activity
import android.content.Context
import android.location.Location
import androidx.fragment.app.FragmentActivity
import com.localytics.androidx.Localytics
import com.segment.analytics.kotlin.android.plugins.AndroidLifecycle
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.platform.plugins.logger.log
import com.segment.analytics.kotlin.core.utilities.getString
import com.segment.analytics.kotlin.core.utilities.toContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class LocalyticsDestination: DestinationPlugin(), AndroidLifecycle {
    private var hasSupportLibOnClassPath: Boolean = false
    private val LOCALYTICS_FULL_KEY = "Localytics"
    internal var localyticsSettings: LocalyticsSettings? = null
    internal var attributeScope: Localytics.ProfileScope? = null
    override val key: String = LOCALYTICS_FULL_KEY

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
        this.localyticsSettings =
            settings.destinationSettings(key, LocalyticsSettings.serializer())
        if (type == Plugin.UpdateType.Initial) {
            Localytics.setLoggingEnabled(Analytics.debugLogsEnabled)
            analytics.log("Localytics.setLoggingEnabled(${Analytics.debugLogsEnabled})")
            Localytics.integrate(analytics.configuration.application as Context)
            analytics.log("Localytics.integrate(context)")
            Localytics.setOption("ll_app_key", localyticsSettings?.appKey)
            analytics.log("Localytics.setOption(${localyticsSettings?.appKey})")

            hasSupportLibOnClassPath = isOnClassPath("androidx.fragment.app.FragmentActivity")
            attributeScope = if (localyticsSettings?.setOrganizationScope == true) {
                Localytics.ProfileScope.ORGANIZATION
            } else {
                Localytics.ProfileScope.APPLICATION
            }
        }
    }

    override fun identify(payload: IdentifyEvent): BaseEvent {
        setContext(payload.context)
        val traits: Traits = payload.traits

        if (payload.userId.isNotEmpty()) {
            Localytics.setCustomerId(payload.userId)
            analytics.log("Localytics.setCustomerId(${payload.userId})")
        }

        val email: String = traits.getString("email")?: ""
        if (email.isNotEmpty()) {
            Localytics.setIdentifier("email", email)
            Localytics.setCustomerEmail(email)
            analytics.log("Localytics.setIdentifier(\"email\", $email)")
            analytics.log("Localytics.setCustomerEmail($email)")
        }

        val name: String = traits.getString("name")?: ""
        if (name.isNotEmpty()) {
            Localytics.setIdentifier("customer_name", name)
            Localytics.setCustomerFullName(name)
            analytics.log("Localytics.setIdentifier(\"customer_name\", $name)")
            analytics.log("Localytics.setFullName($name)")
        }

        val firstName: String = traits.getString("firstName")?: ""
        if (firstName.isNotEmpty()) {
            Localytics.setCustomerFirstName(firstName)
            analytics.log("Localytics.setCustomerFirstName($firstName)")
        }

        val lastName: String = traits.getString("lastName")?: ""
        if (lastName.isNotEmpty()) {
            Localytics.setCustomerLastName(lastName)
            analytics.log("Localytics.setCustomerLastName($lastName)")
        }

        val traitsMap = traits.asStringMap()
        setCustomDimensions(traitsMap)
        for(keySet in traitsMap) {
            Localytics.setProfileAttribute(keySet.key, keySet.value, attributeScope)
            analytics.log("Localytics.setProfileAttribute(${keySet.key}, ${keySet.value}, ${attributeScope})")
        }
        return payload
    }

    override fun screen(payload: ScreenEvent): BaseEvent {
        setContext(payload.context)

        val screen: String = payload.name
        Localytics.tagScreen(screen)
        analytics.log("Localytics.tagScreen($screen)")
        return payload
    }

    override fun track(payload: TrackEvent): BaseEvent {
        setContext(payload.context)
        val event: String = payload.event

        val propertiesMap = payload.properties.asStringMap()

        // Convert revenue to cents.
        val revenue = ((propertiesMap["revenue"]?.toInt() ?:0) * 100).toLong()

        if (revenue != 0L) {
            Localytics.tagEvent(event, propertiesMap, revenue)
            analytics.log("Localytics.tagEvent($event, $propertiesMap, $revenue)")
        } else {
            Localytics.tagEvent(event, propertiesMap)
            analytics.log("Localytics.tagEvent($event, $propertiesMap)")
        }
        setCustomDimensions(propertiesMap)
        return payload
    }

    override fun flush() {
        super.flush()
        Localytics.upload()
        analytics.log("Localytics.upload()")
    }

    /**
     * AndroidActivity Lifecycle Methods
     */
    override fun onActivityResumed(activity: Activity?) {
        super.onActivityResumed(activity)

        Localytics.openSession()
        analytics.log("Localytics.openSession()")

        Localytics.upload()
        analytics.log("Localytics.upload()")

        if (hasSupportLibOnClassPath) {
            if (activity is FragmentActivity) {
                Localytics.setInAppMessageDisplayActivity(
                    activity as FragmentActivity?
                )
                analytics.log("Localytics.setInAppMessageDisplayActivity(activity)")
            }
        }

        val intent = activity!!.intent
        if (intent != null) {
            Localytics.handleTestMode(intent)
            analytics.log("Localytics.handleTestMode($intent)")
        }
    }

    override fun onActivityPaused(activity: Activity?) {
        super.onActivityPaused(activity)
        if (hasSupportLibOnClassPath) {
            if (activity is FragmentActivity) {
                Localytics.dismissCurrentInAppMessage()
                analytics.log("Localytics.dismissCurrentInAppMessage();")
                Localytics.clearInAppMessageDisplayActivity()
                analytics.log("Localytics.clearInAppMessageDisplayActivity();")
            }
        }

        Localytics.closeSession()
        analytics.log("Localytics.closeSession()")
        Localytics.upload()
        analytics.log("Localytics.upload()")
    }

    private fun isOnClassPath(className: String?): Boolean {
        return try {
            Class.forName(className)
            true
        } catch (e: ClassNotFoundException) {
            // ignored
            false
        }
    }

    private fun setContext(context: AnalyticsContext) {
        if (context.isEmpty()) {
            return
        }
        val analyticsContextMap = context.asStringMap()
        if (analyticsContextMap.isNotEmpty()) {
            val androidLocation = Location("Segment")
            androidLocation.longitude = analyticsContextMap["longitude"]?.toDouble() ?: 0.0
            androidLocation.latitude = analyticsContextMap["latitude"]?.toDouble() ?: 0.0
            androidLocation.speed = analyticsContextMap["speed"]?.toFloat() ?: 0f
            Localytics.setLocation(androidLocation)
            analytics.log("Localytics.setLocation($androidLocation)")
        }
    }

    private fun setCustomDimensions(dimensions: Map<String, String>) {
        for(keySet in dimensions) {
            if(localyticsSettings?.dimensions?.containsKey(keySet.key) == true) {
                val dimension = localyticsSettings?.dimensions!![keySet.key]?.toInt() ?: 0
                val value = keySet.value
                Localytics.setCustomDimension(dimension, value)
                analytics.log("Localytics.setCustomDimension($dimension, $value)")
            }
        }
    }
}

/**
 * Localytics Settings data class.
 */
@Serializable
data class LocalyticsSettings(
//    Localytics APP key
    var appKey: String,
//    Session Timeout Interval
//    If an App stays in the background for more than this many seconds, start a new session when it returns to foreground.
    var sessionTimeoutInterval: String,
//    Custom dimension accepted by Localytics
    var dimensions: Map<String, String> = mapOf(),
//    Use Organization Scope for Attributes
    var setOrganizationScope: Boolean = false
)

private fun JsonObject.asStringMap(): Map<String, String> = this.mapValues { (_, value) ->
    value.toContent().toString()
}