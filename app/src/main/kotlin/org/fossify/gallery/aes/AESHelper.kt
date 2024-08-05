package org.fossify.gallery.aes

import android.content.Context
import androidx.media3.datasource.TransferListener
import org.fossify.commons.helpers.isOnMainThread
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object AESHelper {
    private val mFolderPath: String? = null
    private var mSecretKeySpec: SecretKeySpec? = null
    private var mIvParameterSpec: IvParameterSpec? = null
    private lateinit var mCipher: Cipher
    private lateinit var mDataCipher: Cipher
    val tasker: AESTasker
        get() = field

    val mFileInfoCache: HashMap<String, AESFileInfo> = HashMap()

    val decipher: Cipher
        get() {
            if (isOnMainThread()) return mCipher
            val decipher = Cipher.getInstance(AES_TRANSFORMATION)
            decipher.init(Cipher.DECRYPT_MODE, mSecretKeySpec, mIvParameterSpec)
            return decipher
        }

    val encryptionCypher: Cipher
        get() {
            val encryptionCipher = Cipher.getInstance(AES_TRANSFORMATION)
            encryptionCipher.init(Cipher.ENCRYPT_MODE, mSecretKeySpec, mIvParameterSpec)
            return encryptionCipher
        }

    init {
        mIvParameterSpec = AESUtils.createDataIVSpec()
        tasker = AESTasker(object : ProgressCallback {
            override fun onProgress(name: String, progress: Int) {

            }
        })
    }

    fun setToken(token: ByteArray) {
        mSecretKeySpec = AESUtils.createDataKeySpec(token)
        try {
            mCipher = Cipher.getInstance(AES_TRANSFORMATION)
            mCipher.init(Cipher.DECRYPT_MODE, mSecretKeySpec, mIvParameterSpec)

            mDataCipher = Cipher.getInstance(AES_TRANSFORMATION)
            mDataCipher.init(Cipher.DECRYPT_MODE, mSecretKeySpec, mIvParameterSpec)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onDestroy() {
        mSecretKeySpec = null
        mCipher = Cipher.getInstance(AES_TRANSFORMATION)
        mDataCipher = mCipher
        mFileInfoCache.clear()
    }

    fun hasToken(): Boolean {
        return mSecretKeySpec != null
    }

    fun startEncryption(path: String, toPath: String) {
        val type = AESTaskType.ENCRYPT
        tasker.enqueueTask(AESUtils.createTaskInfo(path, type, AESTaskMeta(path, toPath, null)))
    }

    fun startDecryption(paths: List<AESDirItem>, toPath: String) {
        val type = AESTaskType.DECRYPT
        paths.forEach {
            tasker.enqueueTask(AESUtils.createTaskInfo(it.path, type, AESTaskMeta(it.path, toPath, it, it.displayName)))
        }
    }

    fun getDecipherInputStream(inputS: InputStream): CipherInputStream {
        return CipherInputStream(inputS, decipher)
    }

    fun createDataSourceFactory(listener: TransferListener?): EncryptedFileDataSourceFactory {
        return EncryptedFileDataSourceFactory(mDataCipher, mSecretKeySpec, mIvParameterSpec, listener)
    }

    fun decryptText(arr: ByteArray): String {
        return decipher.doFinal(arr).decodeToString()
    }

    fun decrypt(arr: ByteArray): ByteArray {
        return decipher.doFinal(arr)
    }

    fun decryptFileName(encName: String, decipher: Cipher = this.decipher): String {
        return decipher.doFinal(AESFileUtils.decodeBase64Name(encName)).decodeToString()
    }

    fun encryptFileName(name: String, encipher: Cipher = encryptionCypher): String {
        return AESFileUtils.getEncryptedFileName(encipher, name)
    }

    fun decryptAlbumData(fileData: AESDirItem): AESDirItem {
        try {
            fileData.displayName = decryptFileName(fileData.encodedName)
        } catch (e: Exception) {
            linePrint("error in decrypt Album data ${fileData.path}")
            e.printStackTrace()
        }

        return fileData
    }

//    fun decryptImageFileData(context: Context, fileData: AESDirItem): AESDirItem {
//        try {
//            val fileName = decipher.doFinal(AESFileUtils.decodeBase64Name(fileData.encodedName)).decodeToString()
//            fileData.displayName = fileName
//
//        } catch (e: Exception) {
//            println(">>>> error in decrypt image file data ${fileData.path}")
//            e.printStackTrace()
//        }
//
//        return fileData
//    }

//    fun decyrptFileInfo(context: Context, file: File,  decipher: Cipher = this.decipher) {
//
//    }

    fun decryptMediaFileData(context: Context, fileData: AESDirItem, decipher: Cipher = this.decipher): AESDirItem {
        try {
            val fileName = decipher.doFinal(AESFileUtils.decodeBase64Name(fileData.encodedName)).decodeToString()
            fileData.displayName = fileName
            mFileInfoCache[fileData.path]?.also {
                fileData.fileInfo = it
            } ?: run {
                fileData.mInfoFile?.let { sit ->
                    val fileInfo = AESFileUtils.decodeFileData(context, sit, decipher)?.let {
                        it.decodeToString().parseJson(AESFileInfo::class.java)
                    }
                    fileInfo?.let { mFileInfoCache.put(fileData.path, it) }
                    fileData.fileInfo = fileInfo
                }
            }

        } catch (e: Exception) {
            println(">>>> error in decrypt video file data ${fileData.path}")
            e.printStackTrace()
        }

        return fileData
    }

}
