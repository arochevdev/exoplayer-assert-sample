package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called with: savedInstanceState = $savedInstanceState")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        playerController = PlayerController(this)
    }

    override fun onResume() {
        Log.d(TAG, "onResume() called")
        super.onResume()
        playerController.start(findViewById(R.id.surface_view))
    }

    override fun onPause() {
        Log.d(TAG, "onPause() called")
        super.onPause()
        playerController.stop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        super.onDestroy()
    }
}
