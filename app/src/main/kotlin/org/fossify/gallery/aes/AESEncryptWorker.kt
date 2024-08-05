package org.fossify.gallery.aes

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.fossify.commons.aes.AES_IMAGE_EXT
import org.fossify.commons.aes.AES_META_EXT
import org.fossify.commons.aes.AES_THUMB_EXT
import org.fossify.commons.aes.AES_VIDEO_EXT
import org.fossify.commons.extensions.isImageSlow
import org.fossify.commons.extensions.isVideoSlow
import org.fossify.gallery.aes.AESFileUtils.decodeBase64Name
import org.fossify.gallery.aes.AESFileUtils.getEncryptedFileName
import org.fossify.gallery.aes.AESFileUtils.getImageThumbnail
import org.fossify.gallery.aes.AESFileUtils.getMediaFileData
import org.fossify.gallery.aes.AESFileUtils.getVideoThumbnail
import org.fossify.gallery.aes.AESFileUtils.writeByteArrayToFile
import org.fossify.gallery.aes.AESHelper.decryptText
import org.fossify.gallery.aes.AESHelper.encryptionCypher
import java.io.*
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import kotlin.math.max
import kotlin.math.min

class AESEncryptWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private lateinit var mFrom: File
    private lateinit var mToPath: String
    private lateinit var mCipher: Cipher
    private lateinit var mTask: AESTaskInfo
    private val tasker = AESHelper.tasker

    override fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: ""
        tasker.getTask(taskId)?.let {
            mTask = it
            mFrom = File(mTask.meta.fromPath)
            mToPath = mTask.meta.toPath
            it.meta.displayName = mFrom.nameWithoutExtension
            tasker.setProgress(it.id, 0)
            try {
                mCipher = encryptionCypher
                encryptFile()
                tasker.setProgress(it.id, 100)
                return Result.success()
            } catch (e: java.lang.Exception) {
                linePrint(e.message.toString())
                e.printStackTrace()
            }
        }
        tasker.setProgress(taskId, -1)
        return Result.failure()
    }

    @Throws(Exception::class)
    private fun encryptFile() {
        val encName: String = getEncryptedFileName(mCipher, mFrom.name)
        val dec: ByteArray = decodeBase64Name(encName)
        linePrint(" encrypting file  ${mFrom.name} ->  ${decryptText(dec)}")
        if (mFrom.isVideoSlow()) {
            encryptVideoFile(encName)
            return
        }

        if (mFrom.isImageSlow()) {
            encryptImageFile(encName)
        }

    }

    @Throws(Exception::class)
    private fun encryptToFile(toFile: File) {
        val inputStream: InputStream? = applicationContext.contentResolver.openInputStream(Uri.fromFile(mFrom))
        if (inputStream != null) {
            val fileOutputStream = FileOutputStream(toFile)
            val cipherOutputStream = CipherOutputStream(BufferedOutputStream(fileOutputStream), mCipher)
            val totalBytes = mFrom.length()
            val buffer = ByteArray(if (totalBytes > 1024 * 1024) 1024 * 256 else 1024 * 8)
            val pThr = max(1024 * 8, min((totalBytes * 2) / 100, 1024 * 1024))
            var totalBytesRead = 0L
            var lastReported = 0L
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                cipherOutputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalBytesRead - lastReported >= pThr) {
                    val percent = ((totalBytesRead * 100L) / totalBytes).toInt()
                    tasker.setProgress(mTask.id, if (percent == 100) 99 else percent)
                    lastReported = totalBytesRead
                    //   linePrint("Reading from $mFrom $totalBytes $totalBytesRead ${percent}")
                }
            }
            inputStream.close()
            cipherOutputStream.close()
        } else {
            throw FileNotFoundException("$PRINT_TAG File Not found $mFrom")
        }
    }


    @Throws(Exception::class)
    private fun encryptVideoFile(encName: String) {
        val toFile = File(mToPath, encName + AES_VIDEO_EXT)

        encryptToFile(toFile)
        createMediaFileMeta(mToPath, encName, AESFileTypes.Video)
        createThumb(mToPath, encName, getVideoThumbnail(mFrom))
    }

    @Throws(Exception::class)
    private fun encryptImageFile(encName: String) {
        val toFile = File(mToPath, encName + AES_IMAGE_EXT)
        encryptToFile(toFile)
        createThumb(mToPath, encName, getImageThumbnail(mFrom))
        createMediaFileMeta(mToPath, encName, AESFileTypes.Image)

    }

    private fun createMediaFileMeta(fileParentPath: String, nameWE: String, type: AESFileTypes): File {
        val fileInfo: ByteArray = mCipher.doFinal(getMediaFileData(mFrom, type))
        return File(fileParentPath, nameWE + AES_META_EXT).also { writeByteArrayToFile(applicationContext, it, fileInfo) }
    }

    private fun createThumb(fileParentPath: String, nameWE: String, thumbData: ByteArray?): File? {
        thumbData?.let {
            return File(fileParentPath, nameWE + AES_THUMB_EXT).also { file ->
                writeByteArrayToFile(applicationContext, file, mCipher.doFinal(it))

            }
        }
        return null
    }
}
