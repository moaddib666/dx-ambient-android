package com.dx.ambient.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.dx.ambient.data.database.AmbientDatabase
import com.dx.ambient.data.database.AmbientDatabaseMigrations
import com.dx.ambient.data.database.dao.MediaDao
import com.dx.ambient.data.database.dao.SceneDao
import com.dx.ambient.data.device.AndroidDeviceCapabilityProvider
import com.dx.ambient.data.repository.MediaLibraryRepositoryImpl
import com.dx.ambient.data.repository.SceneRepositoryImpl
import com.dx.ambient.data.repository.SettingsRepositoryImpl
import com.dx.ambient.data.util.SystemTimeProvider
import com.dx.ambient.data.util.TimeProvider
import com.dx.ambient.domain.repository.DeviceCapabilityProvider
import com.dx.ambient.domain.repository.MediaLibraryRepository
import com.dx.ambient.domain.repository.SceneRepository
import com.dx.ambient.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "projector_settings",
)

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AmbientDatabase =
        Room.databaseBuilder(context, AmbientDatabase::class.java, AmbientDatabase.NAME)
            .addMigrations(*AmbientDatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideSceneDao(db: AmbientDatabase): SceneDao = db.sceneDao()

    @Provides
    fun provideMediaDao(db: AmbientDatabase): MediaDao = db.mediaDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {

    @Binds
    @Singleton
    abstract fun bindSceneRepository(impl: SceneRepositoryImpl): SceneRepository

    @Binds
    @Singleton
    abstract fun bindMediaLibraryRepository(impl: MediaLibraryRepositoryImpl): MediaLibraryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDeviceCapabilityProvider(impl: AndroidDeviceCapabilityProvider): DeviceCapabilityProvider

    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
