package org.fossify.gallery.aes

import android.content.Context
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.File
import java.nio.ByteBuffer


class AESImageModelLoader(val context: Context) : ModelLoader<AESImageModel, ByteBuffer> {

    override fun handles(model: AESImageModel): Boolean {
        return true
    }

    override fun buildLoadData(model: AESImageModel, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer>? {
        return ModelLoader.LoadData(ObjectKey(model), AESImageFetcher(context, model))
    }

}

public class AESImageFetcher(val context: Context, val model: AESImageModel) : DataFetcher<ByteBuffer> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
        AESFileUtils.decodeFileData(context, File(model.path), AESHelper.decipher)?.let {
            callback.onDataReady(ByteBuffer.wrap(it))
            return
        }
        callback.onLoadFailed(Exception(">>> FilePath not found ${model.path}"))
    }

    override fun cleanup() {
    }

    override fun cancel() {
    }

    override fun getDataClass(): Class<ByteBuffer> {
        return ByteBuffer::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

}

public class AESModelLoaderFactory(val context: Context) : ModelLoaderFactory<AESImageModel, ByteBuffer> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AESImageModel, ByteBuffer> {
        return AESImageModelLoader(context)
    }

    override fun teardown() {
        TODO("Not yet implemented")
    }

}
