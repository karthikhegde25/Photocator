package com.Photocator

import android.app.Application

class PhotocatorApp : Application() {
    lateinit var session: Session
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
    }
}
