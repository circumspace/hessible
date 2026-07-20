package com.circumspace.contactstr

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.circumspace.contactstr.data.blob.EncryptedBlobFetcher
import okio.Path.Companion.toOkioPath

/**
 * App entry point. Provides a tuned Coil [ImageLoader] with explicit in-memory and on-disk caches
 * so contact/profile avatars are cached across launches (and don't re-hit the network), plus a
 * crossfade for smoother appearance.
 *
 * Registers [EncryptedBlobFetcher] so an `AsyncImage(model = contact.photo)` transparently
 * downloads + decrypts the Blossom-hosted encrypted avatar. Its decoded result is memory-cached
 * only (never written to the disk cache), so no plaintext photo touches disk.
 */
class HessibleApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(EncryptedBlobFetcher.PhotoKeyer)
                add(EncryptedBlobFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
