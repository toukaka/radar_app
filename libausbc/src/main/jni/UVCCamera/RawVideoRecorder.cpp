#include "RawVideoRecorder.h"
#include <android/log.h>

#define TAG "RawVideoRecorder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

bool RawVideoRecorder::start(const std::string& filePath) {
    file = fopen(filePath.c_str(), "wb");
    if (!file) {
        LOGE("Failed to open file for raw recording: %s", filePath.c_str());
        return false;
    }
    recording = true;
    LOGI("Started raw recording to %s", filePath.c_str());
    return true;
}

void RawVideoRecorder::stop() {
    if (file) {
        fclose(file);
        file = nullptr;
    }
    recording = false;
    LOGI("Stopped raw recording");
}

bool RawVideoRecorder::writeFrame(uint8_t* data, size_t size) {
    if (!recording || !file) return false;

    auto now = std::chrono::steady_clock::now();
    auto elapsed = now - lastFrameTime;
    if (elapsed < frameInterval) {
        // Not enough time elapsed, skip writing frame
        return false;
    }

    size_t written = fwrite(data, 1, size, file);
    if (written == size) {
        lastFrameTime = now;
        return true;
    }
    return false;
}