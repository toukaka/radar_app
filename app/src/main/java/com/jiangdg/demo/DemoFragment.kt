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


import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice as AndroidUsbDevice
import android.hardware.usb.UsbManager
import com.jiangdg.usb.USBVendorId
import com.vsh.screens.UsbDevice

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


class BluetoothReader(private val socket: BluetoothSocket) {
    private var running = false
    private lateinit var readerThread: Thread

    fun startReading(onDataReceived: (String) -> Unit) {
        running = true
        readerThread = Thread {
            try {
                val input = socket.inputStream.bufferedReader()
                while (running && !Thread.currentThread().isInterrupted) {
                    val line = input.readLine()
                    if (line != null) onDataReceived(line)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        readerThread.start()
    }
}

class DemoFragment : CameraFragment() {
    private lateinit var binding: FragmentDemoBinding
    private lateinit var progressBars: Map<String, ProgressBar>

    private val TAG = "BluetoothClient"
    private val TAG_CAMERA = "UVC_CAMERA"
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var selectedDevice: BluetoothDevice? = null

    val config_fileName = "bmw_app.cfg"

    override fun initView() {
        super.initView()
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
            value <= 50 -> 0x804CAF50.toInt()
            value <= 75 -> 0x80FFA500.toInt()
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

    @SuppressLint("MissingPermission")
    private fun connectToBluetoothDevice() {
        checkOrPromptBluetoothSensor()
        val device = selectedDevice
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        Log.i(TAG, "${selectedDevice?.name}")
        if (device == null) {
            Log.e(TAG, "No Bluetooth device selected")
            Toast.makeText(requireContext(), "No Bluetooth device selected", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("MyAppSPP", MY_UUID)
                Log.i("BluetoothClient", "**socket has been created")
                val socket = serverSocket.accept()  // Blocks until connection or error
                Log.i("BluetoothClient", "**socket has been accepted")
                val reader = BluetoothReader(socket)
                reader.startReading { data ->
                    requireActivity().runOnUiThread {
                        val parts = data.split(",")
                        if (parts.size == 4) {
                            val values = parts.mapNotNull { it.trim().toIntOrNull() }
                            if (values.size == 4) {
                                listOf("front", "back", "left", "right").forEachIndexed { i, key ->
                                    updateProgress(progressBars[key]!!, values[i])
                                    updateProgress(progressBars["${key}_overlay"]!!, values[i])
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to connect to device", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
