package com.jiangdg.demo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.jiangdg.demo.RadarBeepManager
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.concurrent.thread
import android.content.res.Resources
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import android.app.Activity

class BluetoothSensorManager(
    private val activity: Activity,
    private val context: Context,
    private val Progressbars: Map<String, ProgressBar>,
    private val RadarBeepManager: RadarBeepManager,
) {
    val config_fileName_bt = "easycam_sensor.cfg"
    private val TAG = "BluetoothManager"
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    var selectedDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var isListening = false
    private var shouldReconnect = true

    private fun updateProgress(bar: ProgressBar, value: Int) {
        val color = when {
            value <= 75 -> 0x804CAF50.toInt()
            value <= 90 -> 0x80FFA500.toInt()
            else -> 0x80FF0000.toInt()
        }
        val drawable = ClipDrawable(ColorDrawable(color), Gravity.LEFT, ClipDrawable.HORIZONTAL)
        bar.progress = 0
        bar.progressDrawable = drawable
        bar.progress = value

        val maxHeight = Resources.getSystem().displayMetrics.heightPixels / 3f
        val newHeight = (maxHeight * (value / 100f)).toInt().coerceAtLeast(1)
        bar.layoutParams = bar.layoutParams.apply { height = newHeight }
    }

    private fun listenForData(bluetoothSocket: BluetoothSocket) {
        val inputStream = bluetoothSocket.inputStream ?: return
        val byteBuffer = ByteArray(1024)
    
        isListening = true
    
        Thread {
            try {
                while (isListening) {
                    byteBuffer.fill(0)
    
                    // Initial read (blocking)
                    val bytes = inputStream.read(byteBuffer)
    
                    if (bytes == -1) {
                        Log.e(TAG, "Connection closed by remote device.")
                        connectToBluetoothDevice()
                        return@Thread
                    }
    
                    var latestData = String(byteBuffer, 0, bytes)
    
                    // Drain any remaining available bytes
                    while (inputStream.available() > 0) {
                        byteBuffer.fill(0)
                        val moreBytes = inputStream.read(byteBuffer)
                        if (moreBytes > 0) {
                            latestData = String(byteBuffer, 0, moreBytes)
                        }
                    }
    
                    //Log.i("BluetoothClient", "Received latest message: $latestData")
                    val parts = latestData.split(",")
                    if (parts.size == 4) {
                        val values = parts.mapNotNull { it.trim().toIntOrNull() }
                        if (values.size == 4) {
                            activity.runOnUiThread {
                                listOf("front", "back", "left", "right").forEachIndexed { index, key ->
                                    updateProgress(Progressbars[key]!!, values[index])
                                    updateProgress(Progressbars["${key}_overlay"]!!, values[index])
                                    val maxValue = values.maxOrNull() ?: 0f
                                    //Log.i("BluetoothClient", "++ Received latest message: $latestData")
                                    RadarBeepManager.startBeeping { maxValue.toFloat() }
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection lost: ${e.message}")
                if (shouldReconnect) {
                    attemptReconnection()
                }
            } finally {
                closeConnection()
            }
        }.start()
    }
    
    @SuppressLint("MissingPermission")
    fun connectToBluetoothDevice() {
        checkOrPromptBluetoothSensor()
        val device = selectedDevice
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    
        if (device == null) {
            Log.e(TAG, "No Bluetooth device selected")
            Toast.makeText(context, "No Bluetooth device selected", Toast.LENGTH_SHORT).show()
            return
        }
    
        shouldReconnect = true
    
        Thread {
            var connected = false
            while (!connected && shouldReconnect) {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                    Log.i(TAG, "Socket created, attempting to connect...")
                    bluetoothAdapter.cancelDiscovery()
                    bluetoothSocket?.connect()
                    Log.i(TAG, "Connection successful")
                    activity.runOnUiThread {
                        Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                    }
                    connected = true
                    listenForData(bluetoothSocket!!)
                } catch (e: IOException) {
                    Log.e(TAG, "Connection failed: ${e.message}")
                    closeConnection()
                    Thread.sleep(500) // Wait before retrying
                }
            }
        }.start()
    }
    
    private fun attemptReconnection() {
        connectToBluetoothDevice()
        RadarBeepManager.stopBeeping()
        
    }
    
    fun stopBluetoothConnection() {
        shouldReconnect = false
        isListening = false
        closeConnection()
        RadarBeepManager.stopBeeping()
    }
    
    private fun closeConnection() {
        try {
            bluetoothSocket?.close()
            Log.i(TAG, "Socket closed")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        bluetoothSocket = null
    }

    private fun checkOrPromptBluetoothSensor() {
        val file = File(context.getExternalFilesDir(null), config_fileName_bt)
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val content = if (file.exists()) file.readText() else ""

        val regex = Regex("""^bluetooth_sensor:\s*(.*)$""", RegexOption.MULTILINE)
        val match = regex.find(content)

        if (match != null) {
            val selectedDevice_name = match.groupValues[1].trim()
            Log.i(TAG, "Bluetooth sensor configured from config: $selectedDevice_name")
            val pairedDevices = bluetoothAdapter.bondedDevices
            for (dev in pairedDevices) {
                if (dev.name == selectedDevice_name) {
                    selectedDevice = bluetoothAdapter.getRemoteDevice(dev.address)
                    Log.i(TAG, "Bluetooth sensor mac adress from paired devices: $selectedDevice.addr")
                    break  // Stop looping once found
                }
            }
        } else {
            // Prompt user to update
            AlertDialog.Builder(context)
                .setTitle("Bluetooth Sensor Not Configured")
                .setMessage("Please update the Bluetooth sensor settings.")
                .setPositiveButton("Select Bluetooth Device") { _, _ ->
                    showBluetoothDevicesDialog()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @SuppressLint("MissingPermission")
    fun showBluetoothDevicesDialog() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Bluetooth not available or disabled", Toast.LENGTH_SHORT).show()
            return
        }
        val bondedDevices = bluetoothAdapter.bondedDevices.toList()
        if (bondedDevices.isEmpty()) {
            Toast.makeText(context, "No paired devices found", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = bondedDevices.map { it.name }
        AlertDialog.Builder(context)
            .setTitle("Select Bluetooth Device Reboot application after selection")
            .setItems(deviceNames.toTypedArray()) { _, which ->
                selectedDevice = bondedDevices[which]
                // Log selected Bluetooth device in file
                val file = File(context.getExternalFilesDir(null), config_fileName_bt)

                try {
                    var content = if (file.exists()) file.readText() else ""

                    val updatedLine = "bluetooth_sensor: ${selectedDevice?.name}"
                    val regex = Regex("""(?m)^bluetooth_sensor:.*$""")

                    content = if (regex.containsMatchIn(content)) {
                        content.replace(regex, updatedLine)
                    } else {
                        if (content.isNotBlank()) "$content\n$updatedLine" else updatedLine
                    }

                    file.writeText(content)
                    Log.i(TAG, "Updated Bluetooth device to file:\n$updatedLine")
                    // Now connect
                    connectToBluetoothDevice()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to update file", e)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
