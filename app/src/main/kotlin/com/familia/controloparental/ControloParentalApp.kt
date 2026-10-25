package com.familia.controloparental

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Classe Application: inicializa o Firebase ao arrancar o processo.
 */
class ControloParentalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
