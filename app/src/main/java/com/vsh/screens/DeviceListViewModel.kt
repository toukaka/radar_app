/*
 * Copyright 2024 vschryabets@gmail.com
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

package com.vsh.screens

import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiangdg.demo.BuildConfig
import com.jiangdg.usb.USBVendorId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import android.util.Log




import android.hardware.usb.UsbDevice as AndroidUsbDevice
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


data class UsbDevice(
    val usbDevcieId: Int,
    val displayName: String,
    val vendorName: String,
    val classesStr: String
)

data class DeviceListViewState(
    val devices: List<UsbDevice> = emptyList(),
    val openPreviewDeviceId: Int? = null
)

class DeviceListViewModelFactory(
    private val usbManager: UsbManager,
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DeviceListViewModel(
            usbManager = usbManager,
        ) as T
}

class DeviceListViewModel(
    private val usbManager: UsbManager,
) : ViewModel() {
    private val _state = MutableStateFlow(
        DeviceListViewState()
    )
    private var loadDevicesJob : Job? = null
    private var isActive = false

    val state: StateFlow<DeviceListViewState> = _state

    /**
     * Starts a periodic update loop to refresh the list of USB devices.
     * The loop runs every second while `isActive` is true, calling `loadDevices()`
     * to fetch the latest device information. This ensures the UI remains up-to-date
     * with the current state of connected USB devices.
     */
    fun begin() {
        if (!isActive) {
            loadDevicesJob?.cancel()
            loadDevicesJob = viewModelScope.launch {
                isActive = true
                while (isActive) {
                    loadDevices()
                    delay(1000L)
                }
            }
        }
    }

    fun stop() {
        isActive = false
        loadDevicesJob?.cancel()
        loadDevicesJob = null
    }

    fun onEnumerate() {
        loadDevices()
    }

    fun loadDevices() {
    val devices = UsbDeviceRepository.enumerateDevices(usbManager)
    _state.update {
        it.copy(devices = devices)
    }
}


    fun onClick(device: UsbDevice) {
        Log.d("UsbDeviceDebug", "Handling device ===> : $device")
        _state.update {
            it.copy(openPreviewDeviceId = device.usbDevcieId)
        }
    }

    fun onPreviewOpened() {
        _state.update {
            it.copy(openPreviewDeviceId = null)
        }
    }
}