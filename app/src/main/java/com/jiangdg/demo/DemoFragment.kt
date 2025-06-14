package com.jiangdg.demo

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.res.Resources
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.demo.databinding.FragmentDemoBinding
import java.io.IOException
import java.util.*

import java.io.File

import java.io.InputStream

import kotlin.concurrent.thread


import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice as AndroidUsbDevice
import android.hardware.usb.UsbManager
import com.jiangdg.usb.USBVendorId
import com.vsh.screens.UsbDevice


import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import android.content.Context


class RadarBeepManager(context: Context) {

    private val soundPool = SoundPool.Builder().setMaxStreams(1).build()
    private val beepSoundId = soundPool.load(context, R.raw.beep, 1)
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

                soundPool.play(beepSoundId, 1f, 1f, 1, 0, 1f)
                delay(interval)
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
            distance > 50 -> 1000L
            else -> 1000L // far away -> slow beeps
        }
    }

    fun release() {
        soundPool.release()
    }
}


object UsbDeviceRepository {
    fun enumerateDevices(usbManager: UsbManager): List<UsbDevice> {
        val usbDevices = usbManager.deviceList
        return usbDevices.values.map { device ->
            val vendorName = USBVendorId.vendorName(device.vendorId)
            val vidPidStr = String.format("%04x:%04x", device.vendorId, device.productId)
            val classesList = mutableSetOf<Int>()
            classesList.add(device.deviceClass)

            if (device.deviceClass == UsbConstants.USB_CLASS_MISC) {
                for (i in 0 until device.interfaceCount) {
                    classesList.add(device.getInterface(i).interfaceClass)
                }
            }

            UsbDevice(
                usbDevcieId = device.deviceId,
                displayName = "$vidPidStr ${device.deviceName}",
                vendorName = if (vendorName.isEmpty()) "${device.vendorId}" else vendorName,
                classesStr = classesList.map {
                    USBVendorId.CLASSES[it] ?: "$it"
                }.joinToString(",\n")
            )
        }
    }
}

class DemoFragment : CameraFragment() {
    private lateinit var binding: FragmentDemoBinding
    private lateinit var progressBars: Map<String, ProgressBar>

    private val TAG = "BluetoothClient"
    private val TAG_CAMERA = "UVC_CAMERA"
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var selectedDevice: BluetoothDevice? = null

    private var bluetoothSocket: BluetoothSocket? = null

    private var inputStream: InputStream? = null
    private var stopWorker = false

    private var isListening = false
    private var shouldReconnect = true
    
    private lateinit var radarBeepManager: RadarBeepManager

    val config_fileName = "bmw_app.cfg"

    override fun initView() {
        super.initView()
        radarBeepManager = RadarBeepManager(requireContext())
        progressBars = mapOf(
            "front" to binding.progressFront,
            "back" to binding.progressBack,
            "left" to binding.progressLeft,
            "right" to binding.progressRight,
            "front_overlay" to binding.progressFrontOverlay,
            "back_overlay" to binding.progressBackOverlay,
            "left_overlay" to binding.progressLeftOverlay,
            "right_overlay" to binding.progressRightOverlay
        )

        binding.buttonPairDevices.setOnClickListener {
            showBluetoothDevicesDialog()
        }

        binding.listcameras.setOnClickListener {
            showUsbCamerasDialog()
        }
        connectToBluetoothDevice()
    }

    override fun initData() {
        super.initData()
        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this) { ready ->
            if (!ready) return@observe
        }
    }

    override fun onCameraState(self: MultiCameraClient.ICamera, code: ICameraStateCallBack.State, msg: String?) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        binding.uvcLogoIv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Camera error: $msg", Toast.LENGTH_LONG).show()
    }

    private fun handleCameraClosed() {
        binding.uvcLogoIv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Camera closed", Toast.LENGTH_LONG).show()
    }

    private fun handleCameraOpened() {
        binding.uvcLogoIv.visibility = View.GONE
        Toast.makeText(requireContext(), "Camera opened", Toast.LENGTH_LONG).show()
    }

    override fun getCameraView(): IAspectRatio = AspectRatioTextureView(requireContext())

    override fun getCameraViewContainer(): ViewGroup = binding.cameraViewContainer

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        binding = FragmentDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getGravity(): Int = Gravity.BOTTOM

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
        radarBeepManager.startBeeping { value.toFloat() }
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
                        Log.e("BluetoothClient", "Connection closed by remote device.")
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
    
                    Log.i("BluetoothClient", "Received latest message: $latestData")
                    val parts = latestData.split(",")
                    if (parts.size == 4) {
                        val values = parts.mapNotNull { it.trim().toIntOrNull() }
                        if (values.size == 4) {
                            requireActivity().runOnUiThread {
                                listOf("front", "back", "left", "right").forEachIndexed { index, key ->
                                    updateProgress(progressBars[key]!!, values[index])
                                    updateProgress(progressBars["${key}_overlay"]!!, values[index])
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Connection lost: ${e.message}")
                if (shouldReconnect) {
                    attemptReconnection()
                }
            } finally {
                closeConnection()
            }
        }.start()
    }
    
    @SuppressLint("MissingPermission")
    private fun connectToBluetoothDevice() {
        checkOrPromptBluetoothSensor()
        val device = selectedDevice
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    
        if (device == null) {
            Log.e(TAG, "No Bluetooth device selected")
            Toast.makeText(requireContext(), "No Bluetooth device selected", Toast.LENGTH_SHORT).show()
            return
        }
    
        shouldReconnect = true
    
        Thread {
            var connected = false
            while (!connected && shouldReconnect) {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                    Log.i("BluetoothClient", "Socket created, attempting to connect...")
                    bluetoothAdapter.cancelDiscovery()
                    bluetoothSocket?.connect()
                    Log.i("BluetoothClient", "Connection successful")
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                    }
                    connected = true
                    listenForData(bluetoothSocket!!)
                } catch (e: IOException) {
                    Log.e("BluetoothClient", "Connection failed: ${e.message}")
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Connection failed, retrying...", Toast.LENGTH_SHORT).show()
                    }
                    closeConnection()
                    Thread.sleep(3000) // Wait before retrying
                }
            }
        }.start()
    }
    
    private fun attemptReconnection() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Connection lost, attempting to reconnect...", Toast.LENGTH_SHORT).show()
        }
        connectToBluetoothDevice()
    }
    
    fun stopBluetoothConnection() {
        shouldReconnect = false
        isListening = false
        closeConnection()
    }
    
    private fun closeConnection() {
        try {
            bluetoothSocket?.close()
            Log.i("BluetoothClient", "Socket closed")
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error closing socket: ${e.message}")
        }
        bluetoothSocket = null
    }

    // func to parse the sensor for config file
    private fun checkOrPromptBluetoothSensor() {
        val file = File(requireContext().getExternalFilesDir(null), config_fileName)
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
            AlertDialog.Builder(requireContext())
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
    private fun showBluetoothDevicesDialog() {
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
    AlertDialog.Builder(requireContext())
        .setTitle("Select Bluetooth Device Reboot application after selection")
        .setItems(deviceNames.toTypedArray()) { _, which ->
            selectedDevice = bondedDevices[which]
            // Log selected Bluetooth device in file
            val file = File(requireContext().getExternalFilesDir(null), config_fileName)

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


    @SuppressLint("ServiceCast")
private fun showUsbCamerasDialog() {
    val usbManager = requireContext().getSystemService(android.content.Context.USB_SERVICE) as UsbManager
    val devices = UsbDeviceRepository.enumerateDevices(usbManager)

    if (devices.isEmpty()) {
        Toast.makeText(context, "No USB cameras found", Toast.LENGTH_SHORT).show()
        return
    }

    val deviceNames = devices.map { it.displayName }
    AlertDialog.Builder(requireContext())
        .setTitle("Select USB Camera")
        .setItems(deviceNames.toTypedArray()) { _, which ->
            val selectedUsb = devices[which]
            Toast.makeText(requireContext(), "Selected: ${selectedUsb.displayName}", Toast.LENGTH_SHORT).show()

            val usbInfo = """
                Display Name: ${selectedUsb.displayName}
                Device ID: ${selectedUsb.usbDevcieId}
                Vendor Name: ${selectedUsb.vendorName}
                Classes: ${selectedUsb.classesStr}
            """.trimIndent()

            // Save to file
            val file = File(requireContext().getExternalFilesDir(null), config_fileName)

            try {
                file.writeText(usbInfo)
                Log.i(TAG_CAMERA, "Saved info to ${file.absolutePath}")
            } catch (e: IOException) {
                Log.e("Debug USB CAMERA", "Failed to write file", e)
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

    override fun getSelectedDeviceId(): Int = requireArguments().getInt(MainActivity.KEY_USB_DEVICE)

    companion object {
        fun newInstance(usbDeviceId: Int): DemoFragment {
            return DemoFragment().apply {
                arguments = Bundle().apply {
                    putInt(MainActivity.KEY_USB_DEVICE, usbDeviceId)
                }
            }
        }
    }
}
