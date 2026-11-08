package com.windroid.nexus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers Hilt's code generation and
 * creates the singleton dependency graph used throughout the app.
 */
@HiltAndroidApp
class WinDroidApp : Application()
