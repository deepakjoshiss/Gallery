package org.fossify.gallery.aes

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import org.fossify.commons.aes.AES_IMAGE_EXT
import org.fossify.commons.aes.AES_META_EXT
import org.fossify.commons.aes.AES_THUMB_EXT
import org.fossify.commons.aes.AES_VIDEO_EXT
import org.fossify.commons.helpers.photoExtensions
import org.fossify.commons.helpers.videoExtensions
import org.fossify.gallery.aes.AESActivity
import java.io.File

const val AES_PREFS_KEY = "AesPrefs"
const val AES_TOKEN_KEY = "aes_token"
const val AES_TRUTH_KEY = "aes_truth"
const val AES_PIN_KEY = "aes_pin"
const val AES_VAULT_KEY = "aes_vault"

class AESConfig(val context: Context) {

    protected val prefs = context.getSharedPreferences(AES_PREFS_KEY, Context.MODE_PRIVATE)

    companion object {
        fun newInstance(context: Context) = AESConfig(context)
    }

    var aesTruth: String
        get() = prefs.getString(AES_TRUTH_KEY, "")!!
        set(uri) = prefs.edit().putString(AES_TRUTH_KEY, uri).apply()

    var aesKey: String
        get() = prefs.getString(AES_PIN_KEY, "")!!
        set(uri) = prefs.edit().putString(AES_PIN_KEY, uri).apply()

    var aesVault: String
        get() = prefs.getString(AES_VAULT_KEY, "")!!
        set(uri) = prefs.edit().putString(AES_VAULT_KEY, uri).apply()
}

val gson = Gson()
val Context.aesConfig: AESConfig get() = AESConfig.newInstance(this)

fun String.isExtVideo() = videoExtensions.any { equals(it, true) }
fun String.isExtImage() = photoExtensions.any { equals(it, true) }

fun String.isAESVideo() = this.endsWith(AES_VIDEO_EXT)
fun String.isAESImage() = this.endsWith(AES_IMAGE_EXT)


fun Any.toJsonString(): String {
    return gson.toJson(this)!!
}

fun <T> String.parseJson(classOfT: Class<T>): T? {
    return try {
        gson.fromJson(this, classOfT)
    } catch (e: java.lang.Exception) {
        null
    }
}

fun File.getVaultDirChildrenCount(): Int {
    list()?.let {
        return it.count { path -> !(path.endsWith(AES_THUMB_EXT) || path.endsWith(AES_META_EXT)) }
    }
    return 0
}

fun Activity.startAesActivity(paths: ArrayList<String>) {
    Intent(this, AESActivity::class.java).apply {
        putExtra("paths", paths)
        startActivity(this)
    };
}


