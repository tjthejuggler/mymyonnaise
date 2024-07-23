package it.ncorti.emgvisualizer.dagger

import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class AppModule {
    @Provides
    @Singleton
    fun provideDeviceManager(): DeviceManager = DeviceManager()
}