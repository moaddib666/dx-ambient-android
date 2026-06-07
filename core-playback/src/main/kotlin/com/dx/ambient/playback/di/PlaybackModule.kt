package com.dx.ambient.playback.di

import com.dx.ambient.domain.playback.PlaybackController
import com.dx.ambient.playback.AmbientPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: AmbientPlayer): PlaybackController
}
