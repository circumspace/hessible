package com.circumspace.contactstr.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.circumspace.contactstr.domain.Contact
import kotlin.math.abs

/**
 * A circular letter avatar with a color derived deterministically from [seed]. Used both for the
 * signed-in identity (seed = npub) and for each contact (seed = name) — simple, consistent, and
 * no image assets required.
 */
@Composable
fun LetterAvatar(
    seed: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Int = 36,
) {
    val hue = (abs(seed.hashCode()) % 360).toFloat()
    val bg = Color.hsl(hue, 0.5f, 0.55f)
    Surface(color = bg, shape = CircleShape, modifier = modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Avatar for the signed-in Nostr identity. Shows the fetched profile picture when [pictureUrl] is
 * available, otherwise a deterministic letter avatar.
 */
@Composable
fun IdentityAvatar(
    npub: String?,
    modifier: Modifier = Modifier,
    size: Int = 36,
    pictureUrl: String? = null,
) {
    if (pictureUrl != null) {
        AsyncImage(
            model = pictureUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        val label = npub?.removePrefix("npub1")?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        LetterAvatar(seed = npub ?: "", label = label, modifier = modifier, size = size)
    }
}

/**
 * Avatar for a contact: the contact's own photo wins, else the linked Nostr profile's avatar
 * ([profilePicture]), else a deterministic letter avatar.
 *
 * When [highlightNostr] is set, the avatar is wrapped in an inward-pointing halo (a themed ring
 * plus a soft radial glow fading toward the center) to mark contacts linked to a Nostr identity.
 */
@Composable
fun ContactAvatar(
    contact: Contact,
    profilePicture: String?,
    modifier: Modifier = Modifier,
    size: Int = 36,
    highlightNostr: Boolean = false,
) {
    // Precedence: the encrypted Blossom photo (canonical/cross-device) → a local preview URI →
    // the linked Nostr profile's avatar → a deterministic letter avatar. Coil routes the
    // ContactPhoto to the decrypting fetcher; strings use its default loaders.
    val model: Any? = contact.photo ?: contact.photoUri ?: profilePicture
    val avatar: @Composable (Modifier) -> Unit = { m ->
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = m.size(size.dp).clip(CircleShape),
            )
        } else {
            LetterAvatar(seed = contact.displayName, label = contact.initials, modifier = m, size = size)
        }
    }

    if (!highlightNostr) {
        avatar(modifier)
        return
    }

    val halo = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        avatar(Modifier)
        // Inward halo drawn in a single cached pass (no clip layer, no per-frame Brush alloc):
        // a soft radial glow transparent at the center → theme color at the rim, plus a crisp ring.
        Box(
            Modifier.matchParentSize().drawWithCache {
                val glow = Brush.radialGradient(
                    0.65f to Color.Transparent,
                    1.0f to halo.copy(alpha = 0.45f),
                )
                val strokeWidth = 1.5.dp.toPx()
                val ringRadius = this.size.minDimension / 2f - strokeWidth / 2f
                onDrawBehind {
                    drawCircle(glow)
                    drawCircle(halo, radius = ringRadius, style = Stroke(strokeWidth))
                }
            },
        )
    }
}
