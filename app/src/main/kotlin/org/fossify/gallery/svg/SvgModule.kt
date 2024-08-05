package org.fossify.gallery.svg

import android.content.Context
import android.graphics.drawable.PictureDrawable

import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import org.fossify.gallery.aes.AESImageModel
import org.fossify.gallery.aes.AESModelLoaderFactory

import java.io.InputStream
import java.nio.ByteBuffer

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder())
            .prepend(AESImageModel::class.java, ByteBuffer::class.java, AESModelLoaderFactory(context))
            .append(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    override fun isManifestParsingEnabled() = false
}
