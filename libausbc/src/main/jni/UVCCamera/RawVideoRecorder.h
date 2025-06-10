#ifndef RAW_VIDEO_RECORDER_H
#define RAW_VIDEO_RECORDER_H
#include <chrono>

#include <stdio.h>
#include <string>

class RawVideoRecorder {
public:
    bool start(const std::string& filePath);
    void stop();
    bool isRecording() const { return recording; }

    bool writeFrame(uint8_t* data, size_t size);

private:
    FILE* file = nullptr;
    bool recording = false;
    std::chrono::steady_clock::time_point lastFrameTime;
    const std::chrono::milliseconds frameInterval = std::chrono::milliseconds(500); // 100 ms = 10 fps
};

#endif // RAW_VIDEO_RECORDER_H
