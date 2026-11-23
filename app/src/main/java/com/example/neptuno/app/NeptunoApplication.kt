package com.example.neptuno.app

import android.app.Application

class NeptunoApplication : Application() {

    lateinit var appContainer: NeptunoAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = NeptunoAppContainer(this)
    }
}
