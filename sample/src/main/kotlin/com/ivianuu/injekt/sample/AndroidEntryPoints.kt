/*
 * Copyright 2020 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.injekt.sample

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.fragment.app.Fragment
import com.ivianuu.injekt.android.AndroidEntryPoint
import com.ivianuu.injekt.inject

@AndroidEntryPoint
class MyReceiver : BroadcastReceiver() {
    private val repo: Repo by inject()
    override fun onReceive(context: Context, intent: Intent) {
    }
}

@AndroidEntryPoint
class MyService : Service() {
    private val repo: Repo by inject()
    override fun onBind(intent: Intent?): IBinder? = null
}

@AndroidEntryPoint
class MyFragment : Fragment() {
    private val repo: Repo by inject()
}