package com.godraw.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.launch

class GoDrawApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            // Clean up after interrupted saves and keep the project index consistent.
            runCatching { container.projects.recover() }
        }
        container.billing.start()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GoDrawApplication).container
