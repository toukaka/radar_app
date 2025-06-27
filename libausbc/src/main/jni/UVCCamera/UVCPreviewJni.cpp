/*
 * UVCPreviewJni
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 * Copyright (c) 2024 vschryabets@gmail.com
 *
 * File name: UVCPreviewJni.cpp
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

#include <stdlib.h>
#include "UVCPreviewJni.h"
#include <jpeglib.h>
#include <jni.h>
#include <string>
#include <android/log.h> 
#include <fstream>
#include <ctime>
#include <sstream>
#include "RawVideoRecorder.h"

#define LOG_TAG "NativeDemo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static bool gCaptureNextFrame = false;
static bool isRecording_ = false;

RawVideoRecorder rawRecorder;

extern "C"
JNIEXPORT void JNICALL
Java_com_jiangdg_demo_DemoFragment_takeCapture(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "NativeDemo", "Button 'Capture-jpeg' clicked from JNI");
    gCaptureNextFrame = true;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_jiangdg_demo_DemoFragment_StartRecord(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "NativeDemo", "Button 'start Record video' clicked from JNI");
    isRecording_ = true;
    rawRecorder.start("/storage/emulated/0/Android/data/com.jiangdg.ausbc/files/raw_video.rgb");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_jiangdg_demo_DemoFragment_StopRecord(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "NativeDemo", "Button 'stop Record video' clicked from JNI");
    isRecording_ = false;
    rawRecorder.stop();
}


// Save RGB frame to JPEG
bool UVCPreviewJni::saveRgbToJpeg(uint8_t* rgbData, int width, int height, const char* filename) {
    FILE* outfile = fopen(filename, "wb");
    if (!outfile) {
        LOGI("Failed to open file %s", filename);
        return false;
    }

    jpeg_compress_struct cinfo;
    jpeg_error_mgr jerr;

    cinfo.err = jpeg_std_error(&jerr);
    jpeg_create_compress(&cinfo);
    jpeg_stdio_dest(&cinfo, outfile);

    cinfo.image_width = width;
    cinfo.image_height = height;
    cinfo.input_components = 3; // RGB
    cinfo.in_color_space = JCS_RGB;

    jpeg_set_defaults(&cinfo);
    jpeg_set_quality(&cinfo, 90, TRUE);
    jpeg_start_compress(&cinfo, TRUE);

    JSAMPROW row_pointer;
    int row_stride = width * 3;
    while (cinfo.next_scanline < cinfo.image_height) {
        row_pointer = &rgbData[cinfo.next_scanline * row_stride];
        jpeg_write_scanlines(&cinfo, &row_pointer, 1);
    }

    jpeg_finish_compress(&cinfo);
    fclose(outfile);
    jpeg_destroy_compress(&cinfo);

    LOGI("Saved frame to JPEG: %s", filename);
    return true;
}
std::string UVCPreviewJni::generateTimestampedFilename() {
    time_t now = time(nullptr);
    struct tm timeinfo;
    localtime_r(&now, &timeinfo);

    char buffer[64];
    strftime(buffer, sizeof(buffer), "%Y-%m-%d_%H-%M-%S", &timeinfo);

    std::ostringstream oss;
    oss << "/storage/emulated/0/DCIM/easycam360/capture/easycam_cap_"
        << buffer << ".jpeg";

    return oss.str();
}

int UVCPreviewJni::setPreviewDisplay(ANativeWindow *preview_window) {
    pthread_mutex_lock(&preview_mutex);
    {
        if (mPreviewWindow != preview_window) {
            if (mPreviewWindow)
                ANativeWindow_release(mPreviewWindow);
            mPreviewWindow = preview_window;
            if (LIKELY(mPreviewWindow)) {
                ANativeWindow_setBuffersGeometry(mPreviewWindow,
                                                 frameWidth, frameHeight, WINDOW_FORMAT_RGBA_8888);
            }
        }
    }
    pthread_mutex_unlock(&preview_mutex);
    RETURN(0, int);
}

int UVCPreviewJni::setCaptureDisplay(ANativeWindow *capture_window) {

    return 0;
}

UVCPreviewJni::UVCPreviewJni(uvc_device_handle_t *devh)
        : UVCCaptureBase(devh, 1, this, 8, 4),
          mPreviewWindow(NULL),
          mCaptureWindow(NULL),
          mFrameCallbackObj(NULL) {

}

UVCPreviewJni::~UVCPreviewJni() {
    if (mPreviewWindow)
        ANativeWindow_release(mPreviewWindow);
    mPreviewWindow = NULL;
    if (mCaptureWindow)
        ANativeWindow_release(mCaptureWindow);
    mCaptureWindow = NULL;
}

int UVCPreviewJni::stopCapture() {
    auto res = UVCCaptureBase::stopCapture();
    clearDisplay();
    // check preview mutex available
    if (pthread_mutex_lock(&preview_mutex) == 0) {
        if (mPreviewWindow) {
            ANativeWindow_release(mPreviewWindow);
            mPreviewWindow = NULL;
        }
        pthread_mutex_unlock(&preview_mutex);
    }

    return res;
}

int UVCPreviewJni::setFrameCallback(JNIEnv *env, jobject frame_callback_obj, int pixel_format) {

    return 0;
}

void UVCPreviewJni::clearDisplay() {
    ANativeWindow_Buffer buffer;
    pthread_mutex_lock(&preview_mutex);
    {
        if (LIKELY(mPreviewWindow)) {
            if (LIKELY(ANativeWindow_lock(mPreviewWindow, &buffer, NULL) == 0)) {
                uint8_t *dest = (uint8_t *) buffer.bits;
                const size_t bytes = buffer.width * PREVIEW_PIXEL_BYTES;
                const int stride = buffer.stride * PREVIEW_PIXEL_BYTES;
                for (int i = 0; i < buffer.height; i++) {
                    memset(dest, 0, bytes);
                    dest += stride;
                }
                ANativeWindow_unlockAndPost(mPreviewWindow);
            }
        }
    }
    pthread_mutex_unlock(&preview_mutex);
}

void UVCPreviewJni::handleFrame(uint16_t deviceId,
                                const UvcPreviewFrame &frame) {
    uvc_error_t result;
    if (LIKELY(frame.mFrame)) {
        auto rgbFrame = uvc_allocate_frame(frame.mFrame->width * frame.mFrame->height * 3);
        result = uvc_any2rgb(frame.mFrame, rgbFrame);
        if (LIKELY(!result)) {
            draw_preview_rgb(rgbFrame);
            if (isRecording_) {
                rawRecorder.writeFrame((uint8_t*)rgbFrame->data, rgbFrame->width * rgbFrame->height * 3);
            }
            if (gCaptureNextFrame) {
                gCaptureNextFrame = false;
                std::string filename = generateTimestampedFilename();
                bool ok = saveRgbToJpeg((uint8_t*)rgbFrame->data, frame.mFrame->width, frame.mFrame->height, filename.c_str());
                LOGI("Capture saved to %s: %s", filename.c_str(), ok ? "Success" : "Failure");
            }
        }
        uvc_free_frame(rgbFrame);
    }
}

void UVCPreviewJni::onPrepared(uint16_t deviceId,
                                      uint16_t frameWidth,
                                      uint16_t frameHeight) {
    LOGI("onPreviewPrepared %d %dx%d", deviceId, frameWidth, frameHeight);
    pthread_mutex_lock(&preview_mutex);
    if (LIKELY(mPreviewWindow)) {
        ANativeWindow_setBuffersGeometry(mPreviewWindow,
                                         frameWidth, frameHeight, WINDOW_FORMAT_RGBA_8888);
    }
    pthread_mutex_unlock(&preview_mutex);

}

// changed to return original frame instead of returning converted frame even if convert_func is not null.
void UVCPreviewJni::draw_preview_rgb(
        uvc_frame_t *frame) {
    ANativeWindow_Buffer buffer;
    // source = frame data
    pthread_mutex_lock(&preview_mutex);
    if (mPreviewWindow != nullptr) {
        if (LIKELY(ANativeWindow_lock(mPreviewWindow, &buffer, NULL) == 0)) {
            // use lower transfer bytes
            const int w = frame->width < buffer.width ? frame->width : buffer.width;
            // use lower height
            const int h = frame->height < buffer.height ? frame->height : buffer.height;
            // transfer from frame data to the Surface
            uint8_t *srcBuffer = (uint8_t*)frame->data;
            uint32_t *dest = (uint32_t *) buffer.bits;
            uint32_t srcOffset = 0;
            uint32_t dstOffset = 0;
            // TODO optimize next code
            for (uint16_t sy = 0; sy < h; sy++) {
                for (uint16_t sx = 0; sx < w; sx++) {
                    uint32_t rgb = srcBuffer[srcOffset] << 0 |
                            srcBuffer[srcOffset+1] << 8 |
                            srcBuffer[srcOffset+2] << 16;
                    srcBuffer += 3;
                    dest[dstOffset + sx] = 0xFF000000 | rgb;
                }
                dstOffset += buffer.stride;
            }
            ANativeWindow_unlockAndPost(mPreviewWindow);
        }
    }
    pthread_mutex_unlock(&preview_mutex);
}

void UVCPreviewJni::onFinished(uint16_t deviceId) {
    LOGD("onPreviewFinished");
}

void UVCPreviewJni::onFrameLost(uint16_t deviceId, std::chrono::steady_clock::time_point timestamp, uint8_t reason) {
    LOGD("onFrameDropped %lld reason = %d", timestamp.time_since_epoch(), reason);
}

void UVCPreviewJni::onFailed(uint16_t deviceId, UvcPreviewFailed error) {
    LOGE("onPreviewFailed %d %s", deviceId, error.what());
}
