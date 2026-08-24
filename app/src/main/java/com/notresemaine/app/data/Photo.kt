package com.notresemaine.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * La photo d'un repas, du déclencheur à la requête — et rien de plus.
 *
 * Trois règles, dans cet ordre :
 *
 *  1. **On réduit.** Une photo de Galaxy S23 pèse une dizaine de mégaoctets. Envoyée
 *     telle quelle, elle coûterait une minute d'attente sur un réseau moyen pour un
 *     résultat identique : au-delà de ~1 000 pixels de large, le modèle ne reconnaît
 *     pas mieux une assiette. On redimensionne donc avant tout.
 *  2. **On redresse.** Les appareils photo n'enregistrent pas l'image tournée : ils
 *     notent l'orientation à côté. Sans ce redressement, une photo prise à la verticale
 *     part couchée, et le modèle décrit une assiette de travers.
 *  3. **On efface.** Le fichier temporaire est supprimé dès que l'octet est parti.
 *     Rien de ce qui a été photographié ne reste sur le téléphone, ni ne part vers
 *     la synchronisation.
 */
object Photo {

    /** Au-delà, on ne gagne plus en reconnaissance — seulement en temps d'attente. */
    private const val MAX_SIDE = 1024

    /** Assez pour distinguer les aliments, assez léger pour partir en quelques secondes. */
    private const val JPEG_QUALITY = 80

    const val MIME = "image/jpeg"

    /**
     * Prépare le fichier que l'appareil photo va remplir, et l'adresse à lui donner.
     *
     * Android n'autorise plus une application à passer un chemin de fichier brut à
     * une autre : il faut une adresse « content:// » délivrée par le FileProvider
     * déclaré dans le manifeste. C'est ce que renvoie cette fonction.
     */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "repas").apply { mkdirs() }
        // Un nom unique : deux photos prises dans la même minute ne doivent pas
        // s'écraser l'une l'autre.
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        return file to uri
    }

    /**
     * Lit l'image, la réduit, la redresse, et la rend prête à partir.
     * Renvoie null si l'image est illisible — jamais d'exception à gérer côté écran.
     */
    fun toBase64(context: Context, uri: Uri): String? {
        return runCatching {
            val bitmap = decodeScaled(context, uri) ?: return@runCatching null
            val upright = rotateIfNeeded(context, uri, bitmap)
            val out = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (upright !== bitmap) bitmap.recycle()
            upright.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Efface le fichier temporaire, et tous ceux qu'un plantage aurait pu laisser. */
    fun cleanUp(context: Context) {
        runCatching { File(context.cacheDir, "repas").listFiles()?.forEach { it.delete() } }
    }

    // ----- Détail technique -----

    /**
     * Décode en deux passes : d'abord les dimensions seules (sans charger l'image),
     * puis l'image à la taille voulue. Charger 12 mégapixels pour les jeter ensuite
     * ferait planter les téléphones les moins garnis en mémoire.
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / sample > MAX_SIDE * 2) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val side = maxOf(decoded.width, decoded.height)
        if (side <= MAX_SIDE) return decoded
        val ratio = MAX_SIDE.toFloat() / side
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun rotateIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }
}
