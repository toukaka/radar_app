/*
 * UVCPreviewJni
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 * Copyright (c) 2024 vshcryabets@gmail.com
 *
 * File name: UVCPreviewJni.h
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc folder may have a different license, see the respective files.
*/
#pragma once

#include "UVCPreviewBase.h"
#include <android/native_window.h>

class UVCPreviewJni: public UVCCaptureBase, UvcCaptureListener {
private:
    ANativeWindow *mPreviewWindow;
    ANativeWindow *mCaptureWindow;
    jobject mFrameCallbackObj;
private:
    bool saveRgbToJpeg(uint8_t* rgbData, int width, int height, const char* filename);
    static std::string  generateTimestampedFilename();
    void clearDisplay();
    void draw_preview_rgb(uvc_frame_t *frame);
    bool saveRgbFrameToFile(uint8_t* data, int width, int height, int index);
   
protected:
    void handleFrame(uint16_t deviceId, const UvcPreviewFrame &frame) override;
    void onPrepared(uint16_t deviceId, uint16_t frameWidth, uint16_t  frameHeight) override;
    void onFinished(uint16_t deviceId) override;
    void onFrameLost(uint16_t deviceId, std::chrono::steady_clock::time_point timestamp, uint8_t reason) override;
    void onFailed(uint16_t deviceId, UvcPreviewFailed error) override;
    void startRawRecording_();
    void stopRawRecording();
public:
    UVCPreviewJni(uvc_device_handle_t *devh);
    ~UVCPreviewJni();
    
    int setPreviewDisplay(ANativeWindow *preview_window);
    int setCaptureDisplay(ANativeWindow *capture_window);
    virtual int stopCapture() override;
    int setFrameCallback(JNIEnv *env, jobject frame_callback_obj, int pixel_format);
};