
package com.jiangdg.demo
import android.hardware.usb.UsbConstants
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
