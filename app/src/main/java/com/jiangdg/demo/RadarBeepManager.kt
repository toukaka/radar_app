package com.jiangdg.demo

import android.content.Context
import android.media.SoundPool
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class RadarBeepManager(context: Context) {

    private val soundPool = SoundPool.Builder().setMaxStreams(1).build()
    private val beepSoundId = soundPool.load(context, R.raw.beep, 1)
    private val beepSoundId2 = soundPool.load(context, R.raw.beep2, 1)
    private var isBeeping = false
    private var beepJob: Job? = null

    private var currentDistance = 100f

    fun startBeeping(proximityProvider: () -> Float) {
        currentDistance = proximityProvider()

        if (isBeeping) return
        isBeeping = true

        beepJob = CoroutineScope(Dispatchers.Main).launch {
            while (isBeeping) {
                val interval = calculateInterval(currentDistance)
                // Log.e("BluetoothClient", "currentDistance : $currentDistance")
                // Log.e("BluetoothClient", "interval :$interval")
                if (currentDistance > 99 ){
                    //Log.e("BluetoothClient", "long Beep")
                    soundPool.play(beepSoundId2, 1f, 1f, 1, 0, 1f)
                    delay(500)
                }else if (currentDistance > 44){
                    //Log.e("BluetoothClient", "short Beep")
                    soundPool.play(beepSoundId, 1f, 1f, 1, 0, 1f)
                    delay(interval)
                } else {
                    //do nothing don't beep
                    delay(100L)
                    stopBeeping()
                }
            }
        }
    }

    fun updateDistance(newDistance: Float) {
        currentDistance = newDistance
    }

    fun stopBeeping() {
        isBeeping = false
        beepJob?.cancel()
    }

    private fun calculateInterval(distance: Float): Long {
        return when {
            distance > 90 -> 100L  // very close -> fast beeps
            distance > 75 -> 500L
            distance > 60 -> 800L
            distance > 45 -> 1000L
            distance > 30 -> 1200L
            else -> 1000L // far away -> slow beeps
        }
    }

    fun release() {
        soundPool.release()
    }
}

