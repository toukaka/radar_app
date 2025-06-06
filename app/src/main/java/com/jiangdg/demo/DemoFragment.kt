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
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var selectedDevice: BluetoothDevice? = null

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
            showcamerasDevicesDialog()
        }
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
            .setTitle("Select Bluetooth Device")
            .setItems(deviceNames.toTypedArray()) { _, which ->
                selectedDevice = bondedDevices[which]
                connectToBluetoothDevice()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showcamerasDevicesDialog() {
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
            .setTitle("Select Bluetooth Device")
            .setItems(deviceNames.toTypedArray()) { _, which ->
                selectedDevice = bondedDevices[which]
                connectToBluetoothDevice()
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
