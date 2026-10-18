package com.inouiw.kilowatt

import android.app.Application
import com.inouiw.kilowatt.di.AppContainer

class KiloWattApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
