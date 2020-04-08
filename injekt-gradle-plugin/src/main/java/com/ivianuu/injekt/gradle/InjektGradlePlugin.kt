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

package com.ivianuu.injekt.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

open class InjektGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        /*if (project.plugins.none { it is KotlinBasePluginWrapper }) {
            error("Kotlin plugin must be applied first")
        }

        if (!project.plugins.hasPlugin("org.jetbrains.kotlin.kapt")) {
            project.plugins.apply("org.jetbrains.kotlin.kapt")
        }

        project.dependencies.add("kapt",
            "${BuildConfig.GROUP_ID}:${BuildConfig.ANNOTATION_PROCESSOR_ARTIFACT}:${BuildConfig.VERSION}")*/
    }
}
