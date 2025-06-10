/*
 * Copyright 2024-2025 vschryabets@gmail.com
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
package com.vsh.activity

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cupcake.ui.theme.AusbcTheme
import com.jiangdg.demo.MainActivity
import com.vsh.screens.AusbcApp
import com.vsh.screens.DeviceListViewModel
import com.vsh.screens.DeviceListViewModelFactory
import kotlinx.coroutines.launch
import timber.log.Timber
import android.util.Log
import kotlinx.coroutines.delay


import java.io.IOException
import java.io.File

data class UsbDevice(
    val usbDevcieId: Int,
    val displayName: String,
    val vendorName: String,
    val classesStr: String
)

var usbDeviceSelection = UsbDevice(
    usbDevcieId = -1,
    displayName = "",
    vendorName = "",
    classesStr = ""
)

class DevicesActivity : ComponentActivity() {

    lateinit var viewModel: DeviceListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        getWindow().getDecorView().setBackgroundColor(Color.White.toArgb())
        viewModel = ViewModelProvider(
            this, DeviceListViewModelFactory(
                usbManager = applicationContext.getSystemService(USB_SERVICE) as UsbManager,
            )
        ).get(DeviceListViewModel::class.java)
        setContent {
            AusbcTheme {
                AusbcApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.begin()
        val TAG_CAMERA = "UVC_CAMERA_Activity"
        val config_file_path = "easycam_360.cfg"
        val config_file = File(getExternalFilesDir(null), config_file_path)

        if (config_file.exists()) {
            try {
                val content = config_file.readText()
                Log.i("Debug USB CAMERA", "Read from file:\n$content")

                var displayName = ""
                var deviceId = ""
                var vendorName = ""
                val classLines = mutableListOf<String>()

                var foundClasses = false
                val lines = content.lines()
                for (line in lines) {
                    val trimmedLine = line.trim()

                    when {
                        "Display Name:" in trimmedLine -> {
                            displayName = trimmedLine.substringAfter("Display Name:").trim()
                            foundClasses = false
                        }
                        "Device ID:" in trimmedLine -> {
                            deviceId = trimmedLine.substringAfter("Device ID:").trim()
                            foundClasses = false
                        }
                        "Vendor Name:" in trimmedLine -> {
                            vendorName = trimmedLine.substringAfter("Vendor Name:").trim()
                            foundClasses = false
                        }
                        "Classes:" in trimmedLine -> {
                            foundClasses = true
                            classLines.add(trimmedLine.substringAfter("Classes:").trim())
                        }
                        foundClasses -> {
                            classLines.add(trimmedLine)
                        }
                    }
                }

                val classes = classLines.joinToString("\n")

                Log.i(TAG_CAMERA, "parsed Display Name: $displayName")
                Log.i(TAG_CAMERA, "parsed Device ID: $deviceId")
                Log.i(TAG_CAMERA, "parsed Vendor Name: $vendorName")
                Log.i(TAG_CAMERA, "parsed Classes:$classes")

                usbDeviceSelection = UsbDevice(
                                    usbDevcieId = deviceId.toIntOrNull() ?: -1, // fallback if parsing fails
                                    displayName = displayName,
                                    vendorName = vendorName,
                                    classesStr = classes
                )

            } catch (e: IOException) {
                Log.e(TAG_CAMERA, "Error reading file", e)
            }
        } else {
            Log.w(TAG_CAMERA, "File does not exist: ${config_file.absolutePath}")
        }
        lifecycleScope.launch {
            viewModel.state.collect {
                    viewModel.onPreviewOpened()
                    Log.i(TAG_CAMERA, "Selected USB Device ID: ${usbDeviceSelection.usbDevcieId}")
                    Log.i(TAG_CAMERA, "Selected USB Display Name: ${usbDeviceSelection.displayName}")
                    Log.i(TAG_CAMERA, "Selected USB Vendor Name: ${usbDeviceSelection.vendorName}")
                    Log.i(TAG_CAMERA, "Selected USB Classes: ${usbDeviceSelection.classesStr}")
                    val intent =
                        MainActivity.newInstance(applicationContext, usbDeviceSelection.usbDevcieId)
                    startActivity(intent)
           }
        }
    }

    override fun onPause() {
        viewModel.stop()
        super.onPause()
    }

}