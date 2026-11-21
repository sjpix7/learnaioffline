package com.aiengineer.academy

import android.app.Application
import com.aiengineer.academy.data.AppGraph

class AcademyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
