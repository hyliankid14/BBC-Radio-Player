package com.example.androidautoradioplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ListView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val stationsList: ListView = findViewById(R.id.stations_list)
        val btnStop: Button = findViewById(R.id.btn_stop)
        
        val stations = StationRepository.getStations()
        
        val adapter = StationAdapter(this, stations) { stationId ->
            playStation(stationId)
        }
        
        stationsList.adapter = adapter
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
