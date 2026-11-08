package com.windroid.nexus.core.di

import com.windroid.nexus.core.util.DefaultDispatcherProvider
import com.windroid.nexus.core.util.DispatcherProvider
import com.windroid.nexus.data.engine.EngineBridge
import com.windroid.nexus.data.repository.AppRepositoryImpl
import com.windroid.nexus.data.system.SystemMonitor
import com.windroid.nexus.data.vfs.VfsManager
import com.windroid.nexus.domain.engine.ExecutionEngine
import com.windroid.nexus.domain.repository.AppRepository
import com.windroid.nexus.domain.repository.SystemRepository
import com.windroid.nexus.domain.repository.VfsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings that wire interfaces (domain) to implementations (data). The
 * presentation layer only ever injects the interfaces, preserving the
 * dependency rule of Clean Architecture (UI → domain ← data).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds @Singleton
    abstract fun bindDispatchers(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds @Singleton
    abstract fun bindExecutionEngine(impl: EngineBridge): ExecutionEngine

    @Binds @Singleton
    abstract fun bindSystemRepository(impl: SystemMonitor): SystemRepository

    @Binds @Singleton
    abstract fun bindVfsRepository(impl: VfsManager): VfsRepository

    @Binds @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository
}
