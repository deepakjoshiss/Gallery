package org.fossify.gallery.aes

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.AboutActivity
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.aes.AES_META_EXT
import org.fossify.commons.aes.AES_THUMB_EXT
import org.fossify.commons.dialogs.FileConflictDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.getConflictResolution
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.aes.AESActivity
import java.io.File
import javax.crypto.Cipher

abstract class BaseActivityOverride : BaseSimpleActivity() {

    val mSentPaths = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle??) {
        super.onCreate(savedInstanceState)
        handleSendIntent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleSendIntent()
    }

    fun handleSendIntent() {
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val clipData = intent.clipData
            clipData?.let { _ ->
                mSentPaths.clear()
                for (i in 0 until clipData.itemCount) {
                    val path = getRealPathFromURI(clipData.getItemAt(i).uri)
                    path?.let { mSentPaths.add(it) }
                }
            }
        }
    }

    fun startAESActivity() {
        Intent(this, AESActivity::class.java).apply {
            if (mSentPaths.isNotEmpty())
                putStringArrayListExtra("paths", ArrayList(mSentPaths))
            mSentPaths.clear()
            startActivity(this)
        };
    }

    override fun startActivity(intent: Intent) {
        if (intent.component != null && AboutActivity::class.java.name == intent.component!!
                .shortClassName
        ) {
            //   intent.setClass(getApplicationContext(), SettingsActivity.class);
        }
        super.startActivity(intent)
    }

    fun deleteFiles(files: ArrayList<AESDirItem>) {

    }

    fun checkConflictsAES(
        files: ArrayList<AESDirItem>, destinationPath: String, index: Int, conflictResolutions: LinkedHashMap<String, Int>,
        callback: (resolutions: LinkedHashMap<String, Int>) -> Unit
    ) {
        if (index == files.size) {
            callback(conflictResolutions)
            return
        }

        val file = files[index]
        val newFileDirItem = FileDirItem("$destinationPath/${file.name}", file.displayName, file.isDirectory)
        if (getDoesFilePathExist(newFileDirItem.path)) {
            FileConflictDialog(this, newFileDirItem, files.size > 1) { resolution, applyForAll ->
                if (applyForAll) {
                    conflictResolutions.clear()
                    conflictResolutions[""] = resolution
                    checkConflictsAES(files, destinationPath, files.size, conflictResolutions, callback)
                } else {
                    conflictResolutions[newFileDirItem.path] = resolution
                    checkConflictsAES(files, destinationPath, index + 1, conflictResolutions, callback)
                }
            }
        } else {
            checkConflictsAES(files, destinationPath, index + 1, conflictResolutions, callback)
        }
    }

    fun moveFiles(
        fileDirItems: ArrayList<AESDirItem>,
        source: String,
        destination: String,
        callback: (destinationPath: String) -> Unit
    ) {
        if (source == destination) {
            toast(org.fossify.commons.R.string.source_and_destination_same)
            return
        }
        checkConflictsAES(fileDirItems, destination, 0, LinkedHashMap()) {
            toast(org.fossify.commons.R.string.moving)
            ensureBackgroundThread {
                var fileCountToCopy = fileDirItems.size
                val updatedPaths = ArrayList<String>(fileDirItems.size)
                val destinationFolder = File(destination)
                val enCipher = AESHelper.encryptionCypher

                for (oldFileDirItem in fileDirItems) {
                    var newFile = File(destinationFolder, oldFileDirItem.name)

                    val oldInfoFile = oldFileDirItem.mInfoFile
                    val oldThumbFile = oldFileDirItem.mThumbFile

                    var newInfoFile = oldInfoFile?.let { File(destinationFolder, oldFileDirItem.mInfoFile!!.name) }
                    var newThumbFile = oldThumbFile?.let { File(destinationFolder, oldFileDirItem.mThumbFile!!.name) }

                    if (newFile.exists()) {
                        when {
                            getConflictResolution(it, newFile.absolutePath) == CONFLICT_SKIP -> fileCountToCopy--
                            getConflictResolution(it, newFile.absolutePath) == CONFLICT_KEEP_BOTH -> {

                                newFile = getAlternativeAESFile(oldFileDirItem.displayName, newFile.extension, destination, enCipher)
                                val nameWe = newFile.nameWithoutExtension
                                newInfoFile?.let { newInfoFile = File(destinationFolder, nameWe + AES_META_EXT) }
                                newThumbFile?.let { newThumbFile = File(destinationFolder, nameWe + AES_THUMB_EXT) }
                            }
                            else -> {
                                // this file is guaranteed to be on the internal storage, so just delete it this way
                                newFile.delete()
                                newInfoFile?.delete()
                                newThumbFile?.delete()
                            }
                        }
                    }

                    if (!newFile.exists() && File(oldFileDirItem.path).renameTo(newFile)) {
                        oldInfoFile?.renameTo(newInfoFile!!)
                        oldThumbFile?.renameTo(newThumbFile!!)
                        updatedPaths.add(newFile.absolutePath)
                        callback(destination)
                    }
                }
            }
        }
    }

    fun getAlternativeAESFile(realName: String, extension: String, destinationPath: String, enCipher: Cipher): File {
        var fileIndex = 1
        val nameWE = realName.substringBeforeLast(".")
        val realExt = realName.getFilenameExtension()
        var newFile: File?
        do {
            val newName = AESHelper.encryptFileName(String.format("%s(%d).%s", nameWE, fileIndex, realExt), enCipher)
            newFile = File(destinationPath, String.format("%s.%s", newName, extension))
            fileIndex++
        } while (getDoesFilePathExist(newFile!!.absolutePath))
        return newFile
    }
}
