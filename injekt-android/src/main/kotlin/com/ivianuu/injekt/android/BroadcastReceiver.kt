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

package com.ivianuu.injekt.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivianuu.injekt.Reader
import com.ivianuu.injekt.runChildReader

inline fun <R> BroadcastReceiver.runReceiverReader(
    context: Context,
    intent: Intent,
    block: @Reader () -> R
): R = (context.applicationContext as Application).runApplicationReader {
    runChildReader(
        context as ReceiverContext,
        intent as ReceiverIntent
    ) { block() }
}

typealias ReceiverContext = Context

typealias ReceiverIntent = Intent
