package com.viatrail.data

import android.content.Context
import androidx.room.Room
import com.viatrail.domain.TravelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): TravelDatabase =
        Room.databaseBuilder(context, TravelDatabase::class.java, "viatrail.db").build()
    @Provides fun dao(database: TravelDatabase): TripsDao = database.tripsDao()
    @Provides @Singleton fun client(): OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    @Provides @Singleton fun repository(dao: TripsDao, client: OkHttpClient): TravelRepository = GeoapifyRepository(dao,client)
}
