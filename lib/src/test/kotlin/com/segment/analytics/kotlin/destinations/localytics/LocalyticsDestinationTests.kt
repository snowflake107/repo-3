package com.segment.analytics.kotlin.destinations.localytics

import com.segment.analytics.kotlin.core.platform.DestinationPlugin

class LocalyticsDestinationTests : DestinationPlugin() {
    private val LOCALYTICS_FULL_KEY = "Localytics"

    override val key: String = LOCALYTICS_FULL_KEY

}