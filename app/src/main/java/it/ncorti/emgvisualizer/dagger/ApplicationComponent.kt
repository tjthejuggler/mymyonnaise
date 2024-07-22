package it.ncorti.emgvisualizer.dagger

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import it.ncorti.emgvisualizer.MyoApplication
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidSupportInjectionModule::class,
        ContextModule::class,
        BuildersModule::class,
        MyonnaiseModule::class,
        DeviceManagerModule::class,
        ActivityModule::class,
        ScanDeviceModule::class
    ]
)
interface ApplicationComponent : AndroidInjector<MyoApplication> {
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder
        fun contextModule(contextModule: ContextModule): Builder
        fun build(): ApplicationComponent
    }
}