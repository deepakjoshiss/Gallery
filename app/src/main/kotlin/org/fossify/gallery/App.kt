package org.fossify.gallery

import android.app.Application
import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.extensions.checkUseEnglish

class App : Application() {

    companion object Factory {
        @JvmStatic lateinit var instance: App
        @JvmStatic fun get(): App {
            return instance;
        }
    }

    override fun onCreate() {
        instance = this;
        super.onCreate()
        checkUseEnglish()
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
