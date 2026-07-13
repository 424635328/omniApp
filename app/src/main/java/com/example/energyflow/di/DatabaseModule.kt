package com.example.energyflow.di

import android.content.Context
import androidx.room.Room
import com.example.energyflow.data.AppDatabase
import com.example.energyflow.data.MeterRecordDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "energy_flow_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMeterRecordDao(database: AppDatabase): MeterRecordDao {
        return database.meterRecordDao()
    }
}
