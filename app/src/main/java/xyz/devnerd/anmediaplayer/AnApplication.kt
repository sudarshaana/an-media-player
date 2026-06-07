package xyz.devnerd.anmediaplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.DownloadsStore

class AnApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AppRepo.init(this)
        DownloadsStore.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
