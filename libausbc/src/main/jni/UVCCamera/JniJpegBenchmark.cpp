/*
 * Copyright 2025 vschryabets@gmail.com
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
#include "JniJpegBenchmark.h"
#include <jni.h>

extern "C" {

JNIEXPORT jint JNICALL
Java_com_vsh_uvc_JpegBenchmark_nativeStartBenchmark(JNIEnv *env,
                                                    jobject thiz,
                                                    jobject args) {

}

JNIEXPORT void JNICALL
Java_com_vsh_uvc_JpegBenchmark_nativeCancelBenchmark(JNIEnv *env,
                                                     jobject thiz,
                                                     jint id) {
    // TODO: implement nativeCancelBenchmark()
}

JNIEXPORT jobject JNICALL
Java_com_vsh_uvc_JpegBenchmark_nativeGetBenchamrkResults(JNIEnv *env, jobject thiz, jint id) {
    // TODO: implement nativeGetBenchamrkResults()
}
}

