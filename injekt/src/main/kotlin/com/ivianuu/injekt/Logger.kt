/*
 * Copyright 2019 Manuel Wrage
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

package com.ivianuu.injekt

interface Logger {

    fun debug(msg: String)

    fun info(msg: String)

    fun warn(msg: String)

    fun error(msg: String)
}

/**
 * Default tag to use by [Logger]s
 */
const val INJEKT_TAG = "[INJEKT]"

/**
 * Logs messages using [println]
 */
class PrintLogger : Logger {

    override fun debug(msg: String) {
        println("[DEBUG] $INJEKT_TAG $msg")
    }

    override fun info(msg: String) {
        println("[INFO] $INJEKT_TAG $msg")
    }

    override fun warn(msg: String) {
        println("[WARN] $INJEKT_TAG $msg")
    }

    override fun error(msg: String) {
        println("[ERROR] $INJEKT_TAG $msg")
    }
}