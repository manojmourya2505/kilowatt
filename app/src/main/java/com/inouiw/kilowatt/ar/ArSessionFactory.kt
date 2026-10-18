package com.inouiw.kilowatt.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

/** Creates an ARCore session for horizontal plane tracking. Null when ARCore is not supported or cannot start. */
object ArSessionFactory {

    fun create(context: Context): Session? = try {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isUnsupported) {
            null
        } else {
            Session(context).also { session ->
                val config = Config(session).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                session.configure(config)
            }
        }
    } catch (e: Exception) {
        Log.e("ArSessionFactory", "ARCore session could not start", e)
        null
    }
}
