package com.ss.tvcompose

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.ss.tvcompose.di.appModule
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Application entry point: starts Koin and configures Coil.
 *
 * The image loader is worth tuning explicitly on TV, more so than on a phone:
 *
 *  - A TV grid keeps a *lot* of bitmaps alive at once (a full screen of 220 dp
 *    posters, plus everything just off-screen that the lazy row is prefetching).
 *    Coil's default memory cache is a percentage of available heap; TV boxes
 *    routinely have far less heap than a modern phone, so we cap it explicitly
 *    rather than discovering the OOM on a customer's device.
 *
 *  - The disk cache matters more too. TV users scroll the same rows repeatedly
 *    and TV network stacks are often on cheap Wi-Fi. Re-downloading posters on
 *    every launch is the difference between a snappy home screen and a slideshow.
 *
 *  - `crossfade` is kept short. Long fades look elegant in a design mock and
 *    feel sluggish when you are flicking a D-pad across twelve cards.
 */
class TvApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@TvApplication)
            modules(appModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Coil 3 does not bundle a network stack; you pick one. OkHttp
                // gives us connection reuse across the whole poster grid.
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("poster_cache").toOkioPath())
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .build()
}
