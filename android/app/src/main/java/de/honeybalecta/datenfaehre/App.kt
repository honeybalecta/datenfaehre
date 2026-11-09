package de.honeybalecta.datenfaehre

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Werk.start(this)
    }
}
