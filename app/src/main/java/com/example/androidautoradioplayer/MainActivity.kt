package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playRadio2: Button = findViewById(R.id.btn_radio2)
        val playRadio4: Button = findViewById(R.id.btn_radio4)
        val playRadio6: Button = findViewById(R.id.btn_radio6)
        val btnStop: Button = findViewById(R.id.btn_stop)

        playRadio2.setOnClickListener { playStation("radio2") }
        playRadio4.setOnClickListener { playStation("radio4") }
        playRadio6.setOnClickListener { playStation("radio6") }
        btnStop.setOnClickListener { stopPlayback() }
    }

    private fun playStation(id: String) {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_PLAY_STATION
            putExtra(RadioService.EXTRA_STATION_ID, id)
        }
        startService(intent)
    }

    private fun stopPlayback() {
        val intent = Intent(this, RadioService::class.java).apply {
            action = RadioService.ACTION_STOP
        }
        startService(intent)
    }
}
