package com.jugavalentin.projetble.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.jugavalentin.projetble.ui.scan.ScanActivity
import com.jugavalentin.projetble.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.eseoLogo.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
        }

        binding.buttonScannerPeripheriques.setOnClickListener {
            startActivity(ScanActivity.getStartedIntent(this));
        }

        binding.prof.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=1dm-EznlpMI")))
        }
    }


    companion object{
        private const val IDENTIFIANT_ID = "IDENTIFIANT_ID"

        fun getStartedIntent(context: Context, identifiant: String?): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(IDENTIFIANT_ID, identifiant)
            }
        }

    }

    private fun getIdentifiant(): String? {
        return intent.extras?.getString(IDENTIFIANT_ID, null)
    }
}