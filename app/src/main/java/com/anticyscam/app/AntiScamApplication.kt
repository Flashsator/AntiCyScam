package com.anticyscam.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt-managed.
 *
 * Note: heavy initialization (encryption keystore warm-up, default seed data
 * such as the built-in "臨時用" transfer account) is deferred to dedicated
 * components introduced in Phase 5.
 */
@HiltAndroidApp
class AntiScamApplication : Application()
