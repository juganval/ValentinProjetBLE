package com.jugavalentin.projetble.ui.splash

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.jugavalentin.projetble.R
import com.jugavalentin.projetble.ui.main.MainActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(MainActivity.getStartedIntent(this, "Ecran_chargement"));
            finish()
        }, 2000)
    }
}