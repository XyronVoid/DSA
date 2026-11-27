package com.spacebachao.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacebachao.app.data.Kind
import com.spacebachao.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbCache {
    val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

@Composable
fun Thumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = uri.toString()
    val bitmap by produceState<Bitmap?>(initialValue = ThumbCache.cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
                        .also { ThumbCache.cache.put(key, it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(LocalPalette.current.track))
    }
}

fun kindIcon(kind: Kind): ImageVector = when (kind) {
    Kind.IMAGE -> Icons.Filled.Photo
    Kind.VIDEO -> Icons.Filled.Videocam
    Kind.PDF -> Icons.Filled.PictureAsPdf
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = p.label) },
        text = { Text(text, color = p.secondary) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, color = p.red, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.accent) } },
    )
}

/** "100% secure" trust line. */
@Composable
fun SecureLine(modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp), tint = p.green)
        Spacer(Modifier.width(6.dp))
        Text(
            "100% secure · Files never leave your phone",
            style = MaterialTheme.typography.labelMedium,
            color = color ?: p.secondary,
        )
    }
}

@Composable
fun Bullet(text: String, iconTint: Color? = null) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = iconTint ?: p.accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = p.label)
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AppLogo(84.dp)
        Spacer(Modifier.height(28.dp))
        Text("Free up space.\nKeep every memory.", style = MaterialTheme.typography.displaySmall, color = p.label)
        Spacer(Modifier.height(12.dp))
        Text(
            "Space Bachao shrinks your photos, videos and scanned PDFs right on your phone. " +
                "They look the same, they just take less space.",
            style = MaterialTheme.typography.bodyLarge,
            color = p.secondary,
        )
        Spacer(Modifier.height(24.dp))
        GlassCard {
            Bullet("100% secure. Your files never leave your phone.", p.green)
            Bullet("Originals stay in the Bin so you can restore them.")
            Bullet("Dates and locations are kept, your gallery stays in order.")
        }
        Spacer(Modifier.height(32.dp))
        GlossyButton("Allow access to photos & videos", onGrant, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            "Location access is only used so photo locations aren't lost.",
            style = MaterialTheme.typography.bodySmall,
            color = p.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
