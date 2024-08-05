import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.fossify.gallery.aes.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.crypto.CipherInputStream
import kotlin.math.max
import kotlin.math.min

class AESDecryptWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private lateinit var mFrom: File
    private lateinit var mToPath: String
    private lateinit var mTask: AESTaskInfo
    private val tasker = AESHelper.tasker

    override fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: ""
        tasker.getTask(taskId)?.let {
            mTask = it
            mFrom = File(mTask.meta.fromPath)
            mToPath = mTask.meta.toPath
            tasker.setProgress(it.id, 0)
            try {
                decryptFile()
                tasker.setProgress(mTask.id, 100)
                return Result.success()
            } catch (e: java.lang.Exception) {
                linePrint(e.message.toString())
                e.printStackTrace()
            }
        }

        tasker.setProgress(taskId, -1)
        return Result.failure()
    }

    @SuppressLint("NewApi")
    @Throws(Exception::class)
    private fun decryptFile() {
        val fileName: String = AESHelper.decryptFileName(mFrom.nameWithoutExtension)
        val toFile = File(mToPath, fileName)
        val fileInfo = mTask.meta.fileData?.fileInfo
        linePrint(" decrypting to file  ${toFile.path}")

        val inputStream: CipherInputStream? =
            applicationContext.contentResolver.openInputStream(Uri.fromFile(mFrom))?.let { AESHelper.getDecipherInputStream(it) }
        if (inputStream != null) {
            val fileOutputStream = FileOutputStream(toFile)
            val totalBytes = mFrom.length()
            val buffer = ByteArray(if (totalBytes > 1024 * 1024) 1024 * 256 else 1024 * 8)
            val pThr = max(1024 * 8, min((totalBytes * 2) / 100, 1024 * 1024))
            var totalBytesRead = 0L
            var lastReported = 0L
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fileOutputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (totalBytesRead - lastReported >= pThr) {
                    val percent = ((totalBytesRead * 100L) / totalBytes).toInt()
                    tasker.setProgress(mTask.id, if (percent == 100) 99 else percent)
                    lastReported = totalBytesRead
                }
            }
            inputStream.close()
            fileOutputStream.close()
            fileInfo?.let {
                linePrint("Modified the time ${toFile.setLastModified(it.lastMod)}")
            }

        } else {
            throw FileNotFoundException("$PRINT_TAG File Not found $mFrom")
        }
    }
}
