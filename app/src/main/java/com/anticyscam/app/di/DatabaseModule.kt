package com.anticyscam.app.di

import android.content.Context
import androidx.room.Room
import com.anticyscam.app.data.local.AntiScamDatabase
import com.anticyscam.app.data.local.dao.BoundAppDao
import com.anticyscam.app.data.local.dao.TransferAccountDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AntiScamDatabase =
        Room.databaseBuilder(
            context,
            AntiScamDatabase::class.java,
            AntiScamDatabase.DB_NAME
        )
            // Real migration path for v2 → v3 (edits_remaining column).
            // fallbackToDestructiveMigration() stays as a last-resort net
            // for the v0.1.0 pre-release; remove once we ship a 1.x build.
            .addMigrations(AntiScamDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransferAccountDao(db: AntiScamDatabase): TransferAccountDao =
        db.transferAccountDao()

    @Provides
    fun provideBoundAppDao(db: AntiScamDatabase): BoundAppDao = db.boundAppDao()
}
