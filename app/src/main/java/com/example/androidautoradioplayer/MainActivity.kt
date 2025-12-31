package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playRadio2: Button = findViewById(R.id.btn_radio2)
        val playRadio4: Button = findViewById(R.id.btn_radio4)
        val playRadio6: Button = findViewById(R.id.btn_radio6)
        val btnStop: Button = findViewById(R.id.btn_stop)
        
        val imgRadio2: ImageView = findViewById(R.id.img_radio2)
        val imgRadio4: ImageView = findViewById(R.id.img_radio4)
        val imgRadio6: ImageView = findViewById(R.id.img_radio6)
        
        val stations = StationRepository.getStations()
        
        // Load logos using Glide
        stations.find { it.id == "radio2" }?.let { 
            Glide.with(this).load(it.logoUrl).into(imgRadio2)
        }
        stations.find { it.id == "radio4" }?.let { 
            Glide.with(this).load(it.logoUrl).into(imgRadio4)
        }
        stations.find { it.id == "radio6" }?.let { 
            Glide.with(this).load(it.logoUrl).into(imgRadio6)
        }

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
