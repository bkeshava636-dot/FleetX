package com.college.bustracker

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Installs Firebase App Check Play Integrity provider for production release builds.
 */
object AppCheckProviderHelper {
    fun install(appCheck: FirebaseAppCheck) {
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
