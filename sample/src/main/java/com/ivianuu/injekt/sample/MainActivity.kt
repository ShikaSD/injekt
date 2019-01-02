package com.ivianuu.injekt.sample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.ivianuu.injekt.*
import com.ivianuu.injekt.codegen.Single
import com.ivianuu.injekt.sample.multibinding.MultiBindingMap
import com.ivianuu.injekt.sample.multibinding.MultiBindingSet
import com.ivianuu.injekt.sample.multibinding.bindIntoMap
import com.ivianuu.injekt.sample.multibinding.bindIntoSet
import kotlin.reflect.KClass

class MainActivity : AppCompatActivity(), ComponentHolder {

    override val component by lazy {
        component("MainActivityComponent") {
            dependencies((application as ComponentHolder).component)
            modules(mainActivityModule())
        }
    }

    private val servicesMap by inject<MultiBindingMap<KClass<out Service>, Service>>(SERVICES_MAP)
    private val servicesSet by inject<MultiBindingSet<Service>>(SERVICES_SET)

    private val mainActivityDependency by inject<MainActivityDependency>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivityDependency

        Log.d("App", "services set $servicesSet \n\n services map $servicesMap")
    }

}

fun MainActivity.mainActivityModule() = module("mainActivityModule") {
    single { this@mainActivityModule }

    bind<FragmentActivity, MainActivity>()
    bind<Activity, FragmentActivity>()

    bindIntoSet<Service, MyServiceThree>(SERVICES_SET)
    bindIntoMap<KClass<out Service>, Service, MyServiceThree>(SERVICES_MAP, MyServiceThree::class)
}

@Single
class MainActivityDependency(
    val app: App,
    val mainActivity: MainActivity
)