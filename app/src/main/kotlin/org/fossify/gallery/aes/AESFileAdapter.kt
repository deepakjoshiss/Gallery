package org.fossify.gallery.aes

import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.color.MaterialColors
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.getFilePlaceholderDrawables
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.AesFileItemBinding
import java.util.*

class AESFileAdapter(
    val activityN: AESActivity, val fileDirItems: List<AESDirItem>, recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activityN, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private lateinit var fileDrawable: Drawable
    private lateinit var folderDrawable: Drawable
    private var fileDrawables = HashMap<String, Drawable>()
    private lateinit var colorTintList: ColorStateList
    private val hasOTGConnected = activity.hasOTGConnected()
    private val cornerRadius = resources.getDimension(org.fossify.commons.R.dimen.rounded_corner_radius_small).toInt()
    private val dateFormat = activity.baseConfig.dateFormat
    private val timeFormat = activity.getTimeFormat()
    private var selectableItemCount = 0

    init {
        initDrawables()
        selectableItemCount = fileDirItems.count { !it.isDirectory }
    }

    override fun getActionMenuId() = R.menu.menu_aes_select

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AesFileItemBinding.inflate(layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val fileDirItem = fileDirItems[position]
        if (holder is ViewHolder)
            holder.bindView(fileDirItem)
        bindViewHolder(holder)
        //TODO can remove this
        if (fileDirItem.isDirectory) {
            holder.itemView.setOnLongClickListener(null)
        }
    }

    override fun getItemCount() = fileDirItems.size

    override fun prepareActionMode(menu: Menu) {

    }

    override fun actionItemPressed(id: Int) {
        activityN.onActionItemClick(id)
    }

    override fun getSelectableItemCount() = selectableItemCount

    override fun getIsItemSelectable(position: Int) = !fileDirItems[position].isDirectory

    override fun getItemKeyPosition(key: Int) = fileDirItems.indexOfFirst { it.path.hashCode() == key }

    override fun getItemSelectionKey(position: Int) = fileDirItems[position].path.hashCode()

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    fun getSelectedItems(): ArrayList<AESDirItem> {
        return selectedKeys.mapNotNull { fileDirItems.getOrNull(getItemKeyPosition(it)) } as ArrayList<AESDirItem>
    }

    override fun onViewRecycled(holder: MyRecyclerViewAdapter.ViewHolder) {
        if (holder is ViewHolder)
            holder.viewRecycled()
        super.onViewRecycled(holder)

    }

    inner class ViewHolder(private val binding: AesFileItemBinding) : MyRecyclerViewAdapter.ViewHolder(binding.root) {
        fun bindView(fileDirItem: AESDirItem): View {
            var position = 0
            super.bindView(fileDirItem, true, !fileDirItem.isDirectory) { _, pos -> position = pos }
            val isItemSelected = selectedKeys.contains(getItemSelectionKey(position))
            binding.apply {
                itemView.isActivated = isItemSelected
                listItemDisplayName.text = fileDirItem.displayName.ifEmpty { fileDirItem.name }
                listItemDisplayName.setTextColor(textColor)

                listItemName.text = fileDirItem.name
                listItemName.setTextColor(textColor)

                listItemDetails.setTextColor(textColor)

                if (fileDirItem.fileInfo != null && fileDirItem.fileInfo!!.duration > 0) {
                    listItemDuration.beVisible()
                    listItemDuration.setText("${(fileDirItem.fileInfo!!.duration / 1000).toInt().getFormattedDuration()}")
                } else {
                    listItemDuration.beGone()
                }

                if (fileDirItem.isDirectory) {
                    listItemIcon.setImageDrawable(folderDrawable)
                    listItemDetails.text = getChildrenCnt(fileDirItem)

                } else {
                    listItemDetails.text = "${fileDirItem.size.formatSize()} | ${fileDirItem.fileInfo?.lastMod?.formatDate(activity)}"
                    val path = fileDirItem.path
                    val placeholder = fileDrawables.getOrElse(fileDirItem.name.substringAfterLast(".").lowercase(Locale.getDefault()), { fileDrawable })
                    val options = RequestOptions()
                        .signature(fileDirItem.getKey())
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .centerCrop()
                        .error(placeholder)

                    var itemToLoad = if (fileDirItem.name.endsWith(".apk", true)) {
                        val packageInfo = itemView.context.packageManager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
                        if (packageInfo != null) {
                            val appInfo = packageInfo.applicationInfo
                            appInfo.sourceDir = path
                            appInfo.publicSourceDir = path
                            appInfo.loadIcon(itemView.context.packageManager)
                        } else {
                            path
                        }
                    } else {
                        path
                    }

                    if (!activity.isDestroyed && !activity.isFinishing) {
                        if (fileDirItem.mThumbFile != null) {
                            itemToLoad = AESImageModel(fileDirItem.mThumbFile!!.absolutePath)
                        } else if (activity.isRestrictedSAFOnlyRoot(path)) {
                            itemToLoad = activity.getAndroidSAFUri(path)
                        } else if (hasOTGConnected && itemToLoad is String && activity.isPathOnOTG(itemToLoad)) {
                            itemToLoad = itemToLoad.getOTGPublicPath(activity)
                        }

                        if (itemToLoad.toString().isGif()) {
                            Glide.with(activity).asBitmap().load(itemToLoad).apply(options).into(listItemIcon)
                        } else {
                            Glide.with(activity)
                                .load(itemToLoad)
                                .transition(withCrossFade())
                                .apply(options)
                                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                                .into(listItemIcon)
                        }
                    }
                }
                listItemIcon.imageTintList = if (isItemSelected) colorTintList else null
                listItemSelected.beVisibleIf(isItemSelected)
            }
            return itemView
        }

        fun viewRecycled() {
            if (!activity.isDestroyed && !activity.isFinishing) {
                Glide.with(activity).clear(binding.listItemIcon)
            }
        }
    }

    private fun getChildrenCnt(item: FileDirItem): String {
        val children = item.children
        return activity.resources.getQuantityString(org.fossify.commons.R.plurals.items, children, children)
    }

    private fun initDrawables() {
        folderDrawable = resources.getColoredDrawableWithColor(
            R.drawable.ic_folders_vector,
            MaterialColors.getColor(activity, android.R.attr.colorPrimary, activity.getColor(org.fossify.commons.R.color.md_grey_400))
        )
        folderDrawable.alpha = 180
        fileDrawable = resources.getDrawable(org.fossify.commons.R.drawable.ic_file_generic)
        fileDrawables = getFilePlaceholderDrawables(activity)
        colorTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.selected_tint_color))
    }

    override fun onChange(position: Int) = fileDirItems.getOrNull(position)?.getBubbleText(activity, dateFormat, timeFormat) ?: ""
}
