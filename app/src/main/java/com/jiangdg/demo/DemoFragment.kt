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
import android.animation.AnimatorListenerAdapter
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
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import com.jiangdg.demo.databinding.FragmentDemoBinding

import android.os.Handler
import android.os.Looper

import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */

class DemoFragment : CameraFragment() {
    private var mMoreMenu: PopupWindow? = null
    private lateinit var mViewBinding: FragmentDemoBinding
    private lateinit var progressFront: ProgressBar
    private lateinit var progressBack: ProgressBar
    private lateinit var progressLeft: ProgressBar
    private lateinit var progressRight: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private val updateTask = object : Runnable {
        override fun run() {
            updateRandomProgress(progressFront)
            updateRandomProgress(progressBack)
            updateRandomProgress(progressLeft)
            updateRandomProgress(progressRight)
            handler.postDelayed(this, 1000L)
        }
    }
    override fun initView() {
        super.initView()
        progressFront = mViewBinding.root.findViewById(R.id.progress_front)
        progressBack = mViewBinding.root.findViewById(R.id.progress_back)
        progressLeft = mViewBinding.root.findViewById(R.id.progress_left)
        progressRight = mViewBinding.root.findViewById(R.id.progress_right)
    }

    private fun updateRandomProgress(bar: ProgressBar) {
        val value = Random.nextInt(0, 101)
        updateBarColor(bar, value)
    }

    private fun updateBarColor(bar: ProgressBar, value: Int) {
        val color = when {
            value <= 50 -> 0x804CAF50.toInt() // green (50% transparent)
            value <= 75 -> 0x80FFA500.toInt() // orange
            else -> 0x80FF0000.toInt()        // red
        }
        val drawable = ColorDrawable(color)
        val clip = ClipDrawable(drawable, Gravity.LEFT, ClipDrawable.HORIZONTAL)
        bar.progressDrawable = clip
        bar.progress = value
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

    override fun getGravity(): Int = Gravity.CENTER
    override fun onResume() {
        super.onResume()
        handler.post(updateTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTask)
    }

    @SuppressLint("CheckResult")
    private fun showResolutionDialog() {
        mMoreMenu?.dismiss()
        getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Get camera preview size failed", Toast.LENGTH_LONG).show()
                return
            }
            val list = arrayListOf<String>()
            var selectedIndex: Int = -1
            for (index in (0 until previewSizes.size)) {
                val w = previewSizes[index].width
                val h = previewSizes[index].height
                getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }
            MaterialDialog(requireContext()).show {
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { dialog, index, text ->
                    if (selectedIndex == index) {
                        return@listItemsSingleChoice
                    }
                    updateResolution(previewSizes[index].width, previewSizes[index].height)
                }
            }
        }
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
