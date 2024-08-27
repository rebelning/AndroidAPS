package app.aaps.core.interfaces.source

import app.aaps.database.entities.GlucoseValue

interface WTClientSource {

    fun isEnabled(): Boolean
    fun detectSource(glucoseValue: GlucoseValue)
}