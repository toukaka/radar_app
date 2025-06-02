/*
 * Copyright 2017-2022 Jiangdg
 * Copyright 2024 vshcryabets@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.demo

import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast

import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.demo.databinding.FragmentDemoBinding


import android.widget.ProgressBar

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket

import android.util.Log
import java.io.IOException
import java.util.*


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import android.content.res.Resources

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */

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
                    if (line != null) {
                        onDataReceived(line)  // Send data back to UI
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        readerThread.start()
    }
}


class DemoFragment : CameraFragment() {
    private var mMoreMenu: PopupWindow? = null
    private lateinit var mViewBinding: FragmentDemoBinding
    private lateinit var progressFront: ProgressBar
    private lateinit var progressBack: ProgressBar
    private lateinit var progressLeft: ProgressBar
    private lateinit var progressRight: ProgressBar

    private val TAG = "BluetoothClient"
    private val DEVICE_NAME = "sah1lpt671" // ou le nom exact de ton module
    val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")  // Standard SPP UUID


    @SuppressLint("MissingPermission")
    private fun connectToBluetoothDevice() {

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
             Log.e(TAG, "Bluetooth non supporté")
             return
        }
        Log.i("BluetoothClient", "App started successfully")
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth désactivé")
            return
        }

        if (bluetoothAdapter == null) {
            Log.e("BluetoothClient", "Bluetooth not supported on this device")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e("BluetoothClient", "Bluetooth is not enabled")
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices

        var device: BluetoothDevice? = null  // Declare once outside

        if (pairedDevices.isEmpty()) {
            Log.w("BluetoothClient", "No paired devices found")
        } else {
            for (dev in pairedDevices) {
                if (dev.name == DEVICE_NAME) {
                    device = bluetoothAdapter.getRemoteDevice(dev.address)
                    Log.i("BluetoothClient", "Matched device found: ${device.name}")
                    break  // Stop looping once found
                }
            }
        }

        if (device == null) {
            Log.e("BluetoothClient", "Périphérique '$DEVICE_NAME' non appairé")
            return
        } else {
            Log.i("BluetoothClient", "**Paired device: ${device.name}")
        }

        val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("MyAppSPP", MY_UUID)
        Log.i("BluetoothClient", "**socket has been created")
        Thread {
            val socket = serverSocket.accept()  // Blocks until connection or error
            Log.i("BluetoothClient", "**socket has been accepted")
            socket?.let {
                val reader = BluetoothReader(socket)
                reader.startReading { data ->
                    requireActivity().runOnUiThread {
                        Log.i("BluetoothClient", "Received: $data")
                        // Update UI with data here (e.g., setText(), update progress bar)
                        val parts = data.split(",")
                        if (parts.size == 4) {
                            val value_front = parts[0].trim().toIntOrNull()
                            val value_back = parts[1].trim().toIntOrNull()
                            val value_left = parts[2].trim().toIntOrNull()
                            val value_right = parts[3].trim().toIntOrNull()
                            if (value_front != null && value_back != null && value_left != null && value_right != null) {
                                Log.i("BluetoothClient", "Parsed: front=$value_front, back=$value_back, left=$value_left, right=$value_right")
                                //update the screen
                                updateProgress(progressFront, value_front)
                                updateProgress(progressBack, value_back)
                                updateProgress(progressLeft, value_left)
                                updateProgress(progressRight, value_right)
                            } else {
                                Log.w("BluetoothClient", "Invalid numeric data received.")
                            }
                        }
                    }
                }
            }
        }.start()
    }


    override fun initView() {
        super.initView()
        progressFront = mViewBinding.root.findViewById(R.id.progress_front)
        progressBack = mViewBinding.root.findViewById(R.id.progress_back)
        progressLeft = mViewBinding.root.findViewById(R.id.progress_left)
        progressRight = mViewBinding.root.findViewById(R.id.progress_right)
    }

    private fun updateProgress(bar: ProgressBar, value: Int) {
        updateBar(bar, value)
        Log.i("BluetoothClient", "${bar.id} --- $value")
    }

    private fun updateBar(bar: ProgressBar, value: Int) {
        // Déterminer la couleur selon la valeur
        val color = when {
            value <= 50 -> 0x804CAF50.toInt() // Vert (semi-transparent)
            value <= 75 -> 0x80FFA500.toInt() // Orange
            else -> 0x80FF0000.toInt()        // Rouge
        }
    
        // Appliquer le fond coloré
        val drawable = ColorDrawable(color)
        val clip = ClipDrawable(drawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
    
        // Forcer le reset du drawable
        bar.progress = 0
        bar.progressDrawable = clip
    
        // Appliquer la valeur après le drawable
        bar.progress = value
    
        // Calcul dynamique de la hauteur (1/3 de l'écran)
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val maxBarHeight = screenHeight / 3f
        val newHeight = (maxBarHeight * (value / 100f)).toInt().coerceAtLeast(1) // éviter height=0
    
        // Appliquer la hauteur dynamique
        val params = bar.layoutParams
        params.height = newHeight
        bar.layoutParams = params
    }
    
    override fun initData() {
        super.initData()
        /* Here main Frame of the camera */
        EventBus.with<Boolean>(BusKey.KEY_RENDER_READY).observe(this, { ready ->
            if (! ready) return@observe
        })
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "camera opened error: $msg", Toast.LENGTH_LONG).show()
    }

    private fun handleCameraClosed() {        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "camera closed success", Toast.LENGTH_LONG).show()
    }

    private fun handleCameraOpened() {
        mViewBinding.uvcLogoIv.visibility = View.GONE
        Toast.makeText(requireContext(), "camera opened success", Toast.LENGTH_LONG).show()
    }

    override fun getCameraView(): IAspectRatio {
        return AspectRatioTextureView(requireContext())
    }

    override fun getCameraViewContainer(): ViewGroup {
        return mViewBinding.cameraViewContainer
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun getGravity(): Int = Gravity.BOTTOM
    override fun onResume() {
        super.onResume()
        connectToBluetoothDevice()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun clickAnimation(v: View, listener: Animator.AnimatorListener) {
        val scaleXAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.4f, 1.0f)
        val scaleYAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.4f, 1.0f)
        val alphaAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.4f, 1.0f)
        val animatorSet = AnimatorSet()
        animatorSet.duration = 150
        animatorSet.addListener(listener)
        animatorSet.playTogether(scaleXAnim, scaleYAnim, alphaAnim)
        animatorSet.start()
    }

    override fun getSelectedDeviceId(): Int = requireArguments().getInt(MainActivity.KEY_USB_DEVICE)

    companion object {
        fun newInstance(usbDeviceId: Int): DemoFragment {
            val fragment = DemoFragment()
            fragment.arguments = Bundle().apply {
                putInt(MainActivity.KEY_USB_DEVICE, usbDeviceId)
            }
            return fragment
        }
    }
}
