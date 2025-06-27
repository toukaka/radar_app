package com.jiangdg.demo

import android.annotation.SuppressLint

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
import android.hardware.usb.UsbManager
import kotlinx.coroutines.*
import android.content.Intent


class DemoFragment : CameraFragment() {
    private lateinit var binding: FragmentDemoBinding
    private lateinit var progressBars: Map<String, ProgressBar>
    private val TAG = "DemoFragment"
    private var inputStream: InputStream? = null
    private var stopWorker = false

    private var isListening = false
    private var shouldReconnect = true
    
    private lateinit var radarBeepManager: RadarBeepManager
    private lateinit var bluetoothSensorManager: BluetoothSensorManager
    val config_fileName = "easycam_360.cfg"

    val encoder = RawToMp4Encoder(width = 640, height = 480, fps = 30)
    external fun takeCapture()
    external fun StartRecord()
    external fun StopRecord()
    private var isRecording = false

    override fun initView() {
        val ArtifactsdirPath = "/storage/emulated/0/DCIM/easycam360/"
        val videoDirPath = ArtifactsdirPath + "video/"
        val photoDirPath = ArtifactsdirPath + "capture/"
        val Artifactsdir = File(ArtifactsdirPath)
        val Artifactsdir_videos = File(videoDirPath)
        val Artifactsdir_captures = File(photoDirPath)
        if (!Artifactsdir.exists()) {
            val created = Artifactsdir.mkdirs()
            Artifactsdir_videos.mkdirs()
            Artifactsdir_captures.mkdirs()
            if (created) {
                Log.d(TAG, "Artifactsdir created at $Artifactsdir")
            } else {
                Log.e(TAG, "Failed to create Artifactsdir at $Artifactsdir")
            }
        } else {
            Log.d(TAG, "ArtifactsdirPath already exists at $ArtifactsdirPath")
        }
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
        bluetoothSensorManager = BluetoothSensorManager(
            activity = requireActivity(),
            context = requireContext(),
            Progressbars = progressBars,
            RadarBeepManager = radarBeepManager
        )

        binding.buttonPairDevices.setOnClickListener {
            bluetoothSensorManager.showBluetoothDevicesDialog()
        }

        binding.information.setOnClickListener {
            showCreditsDialog()
        }
        binding.listcameras.setOnClickListener {
            showUsbCamerasDialog()
        }
        binding.takecap.setOnClickListener {
            takeCapture()
            Toast.makeText(requireContext(), "capture has been taken", Toast.LENGTH_SHORT).show()
        }
        binding.videoRecord.setOnClickListener {
            if (isRecording) {
                StopRecord()
                Toast.makeText(requireContext(), "stop Recording", Toast.LENGTH_SHORT).show()
                val rawFilePath = "/storage/emulated/0/Android/data/com.jiangdg.ausbc/files/raw_video.rgb"
                val rawFile = File(rawFilePath)

                if (rawFile.exists()) {
                    encoder.encode()
                } else {
                    Toast.makeText(requireContext(), "No raw video file found!", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Raw video file not found: $rawFilePath")
                }
            } else {
                StartRecord()
                Toast.makeText(requireContext(), "Start Recording", Toast.LENGTH_SHORT).show()
            }
            isRecording = !isRecording
        }
        bluetoothSensorManager.connectToBluetoothDevice()
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

    private fun showCreditsDialog(){
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Credits")
        builder.setMessage(
            "Name: TOUMI mohamed\n" +
            "Contact: toumi.mednour@gmail.com\n" +
            "Phone: +33 7 73 11 12 70 \n" +
            "Company: Nextsys-solutions"
        )
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
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
                    Log.i(TAG, "Saved info to ${file.absolutePath}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to write file", e)
                }

                val context = requireContext()
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra(KEY_USB_DEVICE, selectedUsb.usbDevcieId)
                }
                context.startActivity(intent)
                requireActivity().finish() // optional: close current activity to prevent stacking

            }
            .setNegativeButton("Cancel", null)
            .show()    
    }

    override fun getSelectedDeviceId(): Int = requireArguments().getInt(MainActivity.KEY_USB_DEVICE)

    companion object {  
        const val KEY_USB_DEVICE = "usbDeviceId" 
        fun newInstance(usbDeviceId: Int): DemoFragment {
            return DemoFragment().apply {
                arguments = Bundle().apply {
                    putInt(MainActivity.KEY_USB_DEVICE, usbDeviceId)
                }
            }
        }
    }
}
