package org.fossify.gallery.aes


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Base64
import android.util.Size
import org.fossify.commons.aes.AES_THUMB_SIZE
import java.io.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream

object AESFileUtils {

    @Throws(FileNotFoundException::class)
    fun getOutputStream(file: File?, context: Context, z: Boolean): OutputStream? {
        if (file != null) {
            return FileOutputStream(file)
        }
        return null;
    }

    fun bitmapToByteArray(bmp: Bitmap): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray: ByteArray = byteArrayOutputStream.toByteArray()
        bmp.recycle()
        return byteArray
    }

    @SuppressLint("NewApi")
    fun getImageThumbnail(file: File): ByteArray? {
        val thumb: Bitmap = ThumbnailUtils.createImageThumbnail(file, Size(AES_THUMB_SIZE, AES_THUMB_SIZE), null)
        return bitmapToByteArray(thumb)
    }

    @SuppressLint("NewApi")
    fun getVideoThumbnail(file: File): ByteArray? {
        val thumbnailVideoFull: Bitmap? = ThumbnailUtils.createVideoThumbnail(file, Size(AES_THUMB_SIZE, AES_THUMB_SIZE), null);
        if (thumbnailVideoFull != null) {
            return bitmapToByteArray(thumbnailVideoFull)
        }
        println(">>>>>> null thumb, ${file.name}")
        return null
    }

    fun getDuration(str: String): String? {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(str)
        return mediaMetadataRetriever.extractMetadata(METADATA_KEY_DURATION)
    }

    fun getMediaFileData(file: File, type: AESFileTypes): ByteArray {
        val fileData = AESFileInfo(
            duration = if (type == AESFileTypes.Video) getDuration(file.absolutePath)?.toInt() ?: 0 else 0,
            lastMod = file.lastModified()
        )
        return fileData.toJsonString().encodeToByteArray()
    }

    fun decodeFileData(context: Context, file: File, cipher: Cipher? = null): ByteArray? {
        try {
            val inputStream = with(context.contentResolver.openInputStream(Uri.fromFile(file))) {
                if (cipher != null) CipherInputStream(this, cipher) else this
            }
            if (inputStream != null) {
                val outputStream = ByteArrayOutputStream()
                //val aesInputStream = helper.getCipherInputStream(inputStream)
                val buffer = ByteArray(256)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                inputStream.close()
                //aesInputStream.close();
                return outputStream.toByteArray()
            }
        } catch (e: Exception) {
            println(">>> file decode error ${file.absolutePath}")
            e.printStackTrace()
        }
        return null
    }

    fun writeByteArrayToFile(context: Context, file: File?, bArr: ByteArray?) {
        try {
            val outputStream: OutputStream? = getOutputStream(file, context, true)
            if (outputStream != null) {
                outputStream.write(bArr)
                outputStream.close()
            } else {
                throw IllegalStateException("FileOutputStream could not be opened")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun encodeBase64Name(arr: ByteArray): String {
        return Base64.encodeToString(arr, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    }

    fun decodeBase64Name(str: String): ByteArray {
        return Base64.decode(str, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun getEncryptedFileName(cipher: Cipher, name: String): String {
        return encodeBase64Name(cipher.doFinal(name.encodeToByteArray()))
    }

    fun createAlbum(cipher: Cipher, path: String, name: String): Boolean {
        if (path.isNotEmpty()) {
            var file = File(path)
            if (file.isDirectory) {
                file = File(path, getEncryptedFileName(cipher, name))
                return file.mkdir();
            }
        }
        return false;
    }
}
