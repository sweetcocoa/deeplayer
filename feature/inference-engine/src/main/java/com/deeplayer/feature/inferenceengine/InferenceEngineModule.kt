package com.deeplayer.feature.inferenceengine

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceEngineModule {

  @Provides
  @Singleton
  fun provideDeviceCapabilityDetector(): DeviceCapabilityDetector = DeviceCapabilityDetector()

  @Provides
  @Singleton
  fun provideInferenceEngineFactory(detector: DeviceCapabilityDetector): InferenceEngineFactory =
    InferenceEngineFactory(detector)
}
