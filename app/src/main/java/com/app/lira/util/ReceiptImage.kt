package com.app.lira.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val MAX_DIMENSION = 1500
private const val JPEG_QUALITY = 75

/** A fresh content:// Uri (via FileProvider) the camera app can write a full-res photo into. */
fun createReceiptImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Loads the photo at [uri], corrects for EXIF rotation (camera photos are usually stored
 * upright with a rotation *tag* rather than physically rotated pixels), downsamples it (a
 * receipt is flat text — full camera resolution buys nothing and only slows the upload/AI
 * call), and returns it as a base64-encoded JPEG.
 */
fun compressReceiptImage(context: Context, uri: Uri): String {
    val resolver = context.contentResolver

    val orientation = resolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL

    val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: throw IOException("Couldn't decode image")

    val rotated = rotateForExif(original, orientation)

    val scale = MAX_DIMENSION.toFloat() / maxOf(rotated.width, rotated.height)
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
    } else {
        rotated
    }

    val output = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun rotateForExif(bitmap: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
