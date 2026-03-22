package com.example.fixd

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import com.google.firebase.storage.FirebaseStorage
import kotlin.math.max
import kotlin.math.min

data class AppBackgroundSettings(
    val uri: String = "",
    val brightness: Int = AppBackgroundManager.DEFAULT_BRIGHTNESS,
    val blur: Int = AppBackgroundManager.DEFAULT_BLUR
) {
    fun hasImage(): Boolean = uri.isNotBlank()
}

object AppBackgroundManager {
    const val DEFAULT_BRIGHTNESS = 100
    const val DEFAULT_BLUR = 0

    private const val BACKGROUND_TAG = "fixd_app_background_image"
    private var currentSettings = UserAppearanceSettings()
    private var cachedStoragePath: String? = null
    private var cachedBitmap: Bitmap? = null

    fun updateSettings(settings: UserAppearanceSettings) {
        currentSettings = settings
        if (cachedStoragePath != settings.backgroundStoragePath) {
            cachedStoragePath = null
            cachedBitmap = null
        }
    }

    fun clearCurrentSettings() {
        currentSettings = UserAppearanceSettings()
        cachedStoragePath = null
        cachedBitmap = null
    }

    fun currentSettings(): UserAppearanceSettings = currentSettings

    fun applyToActivity(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (!currentSettings.hasBackground()) {
            removeExistingBackground(content)
            return
        }

        val imageView = ensureBackgroundView(activity, content)
        content.post {
            renderRemoteInto(activity, imageView, content.width, content.height)
        }
    }

    fun renderPreview(context: Context, imageView: ImageView, settings: AppBackgroundSettings) {
        if (!settings.hasImage()) {
            imageView.setImageDrawable(null)
            return
        }

        imageView.post {
            renderLocalInto(
                context = context,
                imageView = imageView,
                settings = settings,
                targetWidth = imageView.width,
                targetHeight = imageView.height
            )
        }
    }

    private fun ensureBackgroundView(context: Context, parent: ViewGroup): ImageView {
        val existing = parent.findViewWithTag<ImageView>(BACKGROUND_TAG)
        if (existing != null) return existing

        return ImageView(context).apply {
            tag = BACKGROUND_TAG
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            parent.addView(this, 0)
        }
    }

    private fun removeExistingBackground(parent: ViewGroup) {
        parent.findViewWithTag<ImageView>(BACKGROUND_TAG)?.let(parent::removeView)
    }

    private fun renderRemoteInto(
        context: Context,
        imageView: ImageView,
        targetWidth: Int,
        targetHeight: Int
    ) {
        val storagePath = currentSettings.backgroundStoragePath
        if (storagePath.isBlank()) {
            imageView.setImageDrawable(null)
            return
        }

        val cached = cachedBitmap
        if (cached != null && cachedStoragePath == storagePath) {
            imageView.setImageBitmap(scaleAndBlurBitmap(cached, targetWidth, targetHeight, currentSettings.backgroundBlur))
            imageView.colorFilter = createBrightnessFilter(currentSettings.backgroundBrightness)
            return
        }

        FirebaseStorage.getInstance().reference.child(storagePath)
            .getBytes(5L * 1024L * 1024L)
            .addOnSuccessListener { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@addOnSuccessListener
                cachedStoragePath = storagePath
                cachedBitmap = bitmap
                imageView.setImageBitmap(scaleAndBlurBitmap(bitmap, targetWidth, targetHeight, currentSettings.backgroundBlur))
                imageView.colorFilter = createBrightnessFilter(currentSettings.backgroundBrightness)
            }
            .addOnFailureListener {
                imageView.setImageDrawable(null)
            }
    }

    private fun renderLocalInto(
        context: Context,
        imageView: ImageView,
        settings: AppBackgroundSettings,
        targetWidth: Int,
        targetHeight: Int
    ) {
        val bitmap = decodeScaledBitmap(
            context = context,
            uri = settings.uri.toUri(),
            reqWidth = max(targetWidth, 1),
            reqHeight = max(targetHeight, 1)
        ) ?: run {
            imageView.setImageDrawable(null)
            return
        }

        imageView.setImageBitmap(scaleAndBlurBitmap(bitmap, targetWidth, targetHeight, settings.blur))
        imageView.colorFilter = createBrightnessFilter(settings.brightness)
    }

    private fun createBrightnessFilter(brightness: Int): ColorMatrixColorFilter {
        return ColorMatrixColorFilter(
            ColorMatrix().apply {
                val scale = brightness.coerceIn(35, 180) / 100f
                setScale(scale, scale, scale, 1f)
            }
        )
    }

    private fun decodeScaledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return Bitmap.createScaledBitmap(decoded, max(reqWidth, 1), max(reqHeight, 1), true)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
                halfHeight /= 2
                halfWidth /= 2
            }
        }
        return max(inSampleSize, 1)
    }

    private fun scaleAndBlurBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int, blur: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(source, max(targetWidth, 1), max(targetHeight, 1), true)
        return if (blur > 0) stackBlur(scaled, blur) else scaled
    }

    private fun stackBlur(source: Bitmap, radius: Int): Bitmap {
        val safeRadius = radius.coerceIn(1, 25)
        val scaledWidth = max(1, source.width / 4)
        val scaledHeight = max(1, source.height / 4)
        val input = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val bitmap = input.copy(Bitmap.Config.ARGB_8888, true)

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val div = safeRadius + safeRadius + 1
        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))
        val divsum = (div + 1) shr 1
        val divsumSquare = divsum * divsum
        val dv = IntArray(256 * divsumSquare)
        for (index in dv.indices) {
            dv[index] = index / divsumSquare
        }

        yi = 0
        yw = 0
        val stack = Array(div) { IntArray(3) }
        var stackPointer: Int
        var stackStart: Int
        lateinit var sir: IntArray
        var rbs: Int
        val r1 = safeRadius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (yy in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (ii in -safeRadius..safeRadius) {
                p = pix[yi + min(wm, max(ii, 0))]
                sir = stack[ii + safeRadius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(ii)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (ii > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackPointer = safeRadius

            for (xx in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackStart = stackPointer - safeRadius + div
                sir = stack[stackStart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (yy == 0) {
                    vmin[xx] = min(xx + safeRadius + 1, wm)
                }
                p = pix[yw + vmin[xx]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackPointer = (stackPointer + 1) % div
                sir = stack[stackPointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (xx in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -safeRadius * w
            for (ii in -safeRadius..safeRadius) {
                yi = max(0, yp) + xx
                sir = stack[ii + safeRadius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(ii)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (ii > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (ii < hm) {
                    yp += w
                }
            }
            yi = xx
            stackPointer = safeRadius
            for (yy in 0 until h) {
                pix[yi] = (pix[yi] and -0x1000000) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackStart = stackPointer - safeRadius + div
                sir = stack[stackStart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (xx == 0) {
                    vmin[yy] = min(yy + r1, hm) * w
                }
                p = xx + vmin[yy]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackPointer = (stackPointer + 1) % div
                sir = stack[stackPointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return Bitmap.createScaledBitmap(bitmap, source.width, source.height, true)
    }
}
