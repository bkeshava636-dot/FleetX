package com.college.bustracker

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Installs Firebase App Check Debug provider for local production-debug builds.
 */
object AppCheckProviderHelper {
    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
