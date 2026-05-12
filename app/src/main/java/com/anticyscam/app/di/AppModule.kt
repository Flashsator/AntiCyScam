package com.anticyscam.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-wide Hilt module. Currently empty — `AppPreferences`, repositories,
 * etc. are bound directly via `@Inject` constructors. Module is kept as a
 * stable insertion point for future cross-cutting bindings (e.g. exposing
 * interface types from concrete repository classes).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
