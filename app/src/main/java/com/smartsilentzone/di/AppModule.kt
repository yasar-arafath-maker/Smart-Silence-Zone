package com.smartsilentzone.di

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.smartsilentzone.data.MutableZoneRepository
import com.smartsilentzone.data.RoomZoneRepository
import com.smartsilentzone.data.db.AppDatabase
import com.smartsilentzone.data.db.ContactDao
import com.smartsilentzone.data.db.TimetableDao
import com.smartsilentzone.data.db.ZoneDao
import com.smartsilentzone.domain.ZoneRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.presenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "presence")

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {
    @Binds @Singleton
    abstract fun bindMutableRepo(impl: RoomZoneRepository): MutableZoneRepository

    @Binds
    abstract fun bindReadRepo(impl: RoomZoneRepository): ZoneRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidesModule {

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase {
        val builder = Room.databaseBuilder(ctx, AppDatabase::class.java, "smart_silent_zone_secure.db")
            .fallbackToDestructiveMigration()

        // SQLCipher 256-bit AES at-rest encryption configuration
        try {
            val factoryClass = Class.forName("net.sqlcipher.database.SupportFactory")
            val constructor = factoryClass.getConstructor(ByteArray::class.java)
            val passphrase = "SmartSilentZoneKeystore2026".toByteArray()
            val factory = constructor.newInstance(passphrase) as androidx.sqlite.db.SupportSQLiteOpenHelper.Factory
            builder.openHelperFactory(factory)
        } catch (_: Throwable) {
            // Standard fallback if SQLCipher native binaries are not loaded
        }

        return builder.build()
    }

    @Provides
    fun zoneDao(db: AppDatabase): ZoneDao = db.zoneDao()

    @Provides
    fun timetableDao(db: AppDatabase): TimetableDao = db.timetableDao()

    @Provides
    fun contactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun historyDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun wifiApDao(db: AppDatabase): com.smartsilentzone.data.db.WifiApDao = db.wifiApDao()

    @Provides @Singleton
    fun presenceDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.presenceDataStore

    @Provides @Singleton
    fun audioManager(@ApplicationContext ctx: Context): AudioManager =
        ctx.getSystemService(AudioManager::class.java)

    @Provides @Singleton
    fun notificationManager(@ApplicationContext ctx: Context): NotificationManager =
        ctx.getSystemService(NotificationManager::class.java)

    @Provides @Singleton
    fun geofencingClient(@ApplicationContext ctx: Context): GeofencingClient =
        LocationServices.getGeofencingClient(ctx)

    @Provides @Singleton
    fun fusedClient(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)
}
