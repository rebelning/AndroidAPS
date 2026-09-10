package app.aaps.core.interfaces.source

// import app.aaps.database.entities.GlucoseValue

import app.aaps.core.data.model.GV

interface WTClientSource {

    fun isEnabled(): Boolean
    fun detectSource(glucoseValue: GV)
}