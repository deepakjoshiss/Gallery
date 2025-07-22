package com.bumptech.glide

import android.content.Context
import com.bumptech.glide.integration.avif.AvifGlideModule
import com.bumptech.glide.integration.webp.WebpGlideLibraryModule
import kotlin.Boolean
import kotlin.Suppress
import org.fossify.gallery.svg.SvgModule

internal class GeneratedAppGlideModuleImpl(
  @Suppress("UNUSED_PARAMETER")
  context: Context,
) : GeneratedAppGlideModule() {
  private val appGlideModule: SvgModule
  init {
    appGlideModule = SvgModule()
  }

  public override fun registerComponents(
    context: Context,
    glide: Glide,
    registry: Registry,
  ) {
    AvifGlideModule().registerComponents(context, glide, registry)
    WebpGlideLibraryModule().registerComponents(context, glide, registry)
    appGlideModule.registerComponents(context, glide, registry)
  }

  public override fun applyOptions(context: Context, builder: GlideBuilder) {
    appGlideModule.applyOptions(context, builder)
  }

  public override fun isManifestParsingEnabled(): Boolean = false
}
