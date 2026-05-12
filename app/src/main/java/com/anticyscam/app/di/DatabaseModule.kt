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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransferAccountDao(db: AntiScamDatabase): TransferAccountDao =
        db.transferAccountDao()

    @Provides
    fun provideBoundAppDao(db: AntiScamDatabase): BoundAppDao = db.boundAppDao()
}
