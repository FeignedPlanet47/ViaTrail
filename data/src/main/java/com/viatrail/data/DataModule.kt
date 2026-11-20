package com.viatrail.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trips ADD COLUMN routeJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trips ADD COLUMN settingsJson TEXT NOT NULL DEFAULT ''")
        }
    }
    @Provides @Singleton fun database(@ApplicationContext context: Context): TravelDatabase =
        Room.databaseBuilder(context, TravelDatabase::class.java, "viatrail.db")
            .addMigrations(MIGRATION_1_2).build()
    @Provides fun dao(db: TravelDatabase): TripsDao = db.tripsDao()
    @Provides @Singleton fun client(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS).build()
    @Provides @Singleton fun repository(dao: TripsDao, client: OkHttpClient): TravelRepository =
        GeoapifyRepository(dao, client)
}
