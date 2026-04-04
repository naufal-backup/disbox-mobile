package com.disbox.mobile.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.disbox.mobile.*
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
    fun provideDatabase(@ApplicationContext context: Context): DisboxDatabase {
        return Room.databaseBuilder(
            context,
            DisboxDatabase::class.java,
            "disbox_database"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .fallbackToDestructiveMigration() // For development, can use migrations later
        .build()
    }

    @Provides
    fun provideFileDao(db: DisboxDatabase): FileDao = db.fileDao()

    @Provides
    fun provideMetadataSyncDao(db: DisboxDatabase): MetadataSyncDao = db.metadataSyncDao()

    @Provides
    fun provideSettingsDao(db: DisboxDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideShareSettingsDao(db: DisboxDatabase): ShareSettingsDao = db.shareSettingsDao()

    @Provides
    fun provideShareLinkDao(db: DisboxDatabase): ShareLinkDao = db.shareLinkDao()
}
