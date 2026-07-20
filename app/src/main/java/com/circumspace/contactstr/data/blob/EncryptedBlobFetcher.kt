package com.circumspace.contactstr.data.blob

import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.circumspace.contactstr.crypto.ImageCrypto
import com.circumspace.contactstr.domain.ContactPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Coil fetcher for [ContactPhoto]: downloads the encrypted blob (trying each mirror URL in turn),
 * decrypts it with the descriptor's key/nonce, decodes the plaintext JPEG, and hands Coil a decoded
 * [coil3.Image]. Because it returns a decoded image (not a byte source), Coil never writes the
 * plaintext to its disk cache — only the in-memory bitmap cache holds it, matching the app's
 * "no plaintext at rest" model.
 *
 * `AsyncImage(model = contact.photo)` routes here; strings/URIs still use Coil's default fetchers.
 */
class EncryptedBlobFetcher(
    private val model: ContactPhoto,
    private val client: OkHttpClient,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        for (url in model.urls) {
            val cipher = runCatching { download(url) }.getOrNull() ?: continue
            val plain = runCatching { ImageCrypto.decrypt(cipher, model.key, model.nonce) }.getOrNull() ?: continue
            val bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size) ?: continue
            return@withContext ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = false,
                dataSource = DataSource.NETWORK,
            )
        }
        error("Could not fetch/decrypt contact photo ${model.sha256} from ${model.urls.size} mirror(s)")
    }

    private fun download(url: String): ByteArray? =
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }

    class Factory(private val client: OkHttpClient = OkHttpClient()) : Fetcher.Factory<ContactPhoto> {
        override fun create(data: ContactPhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedBlobFetcher(data, client)
    }

    /** Content-address the memory-cache entry by sha256 (stable, and no secret in the key). */
    object PhotoKeyer : Keyer<ContactPhoto> {
        override fun key(data: ContactPhoto, options: Options): String = "blossom:${data.sha256}"
    }
}
