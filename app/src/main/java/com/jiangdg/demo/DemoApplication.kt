/*
 * Copyright 2017-2022 Jiangdg
 *           2025 vschryabets@gmail.com
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

import android.app.Application
import android.content.res.AssetManager
import timber.log.Timber

/**
 *
 * @author Created by jiangdg on 2022/2/28
 */
class DemoApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        System.loadLibrary("native")
        initDI(this.assets)
    }

    external fun initDI(assetManager: AssetManager)
}