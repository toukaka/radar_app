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

data class UsbDevice(
    val usbDevcieId: Int,
    val displayName: String,
    val vendorName: String,
    val classesStr: String
)

val usbDevice_FIXED = UsbDevice(
    usbDevcieId = 1002,
    displayName = "1902:8301 /dev/bus/usb/001/002",
    vendorName = "6402",
    classesStr = "USB_CLASS_MISC, USB_CLASS_VIDEO"
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

        lifecycleScope.launch {
            viewModel.state.collect { 
                viewModel.onPreviewOpened()
                val intent =
                    MainActivity.newInstance(applicationContext, usbDevice_FIXED.usbDevcieId)
                startActivity(intent)
            }
        }
    }

    override fun onPause() {
        viewModel.stop()
        super.onPause()
    }

}