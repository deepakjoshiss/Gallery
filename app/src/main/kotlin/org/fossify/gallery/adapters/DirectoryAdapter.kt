package org.fossify.gallery.adapters

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.text.TextUtils
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FolderLockingNoticeDialog
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.dialogs.RenameItemDialog
import org.fossify.commons.dialogs.RenameItemsDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.containsNoMedia
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.doesThisOrParentHaveNoMedia
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.databinding.DirectoryItemGridRoundedCornersBinding
import org.fossify.gallery.databinding.DirectoryItemGridSquareBinding
import org.fossify.gallery.databinding.DirectoryItemListBinding
import org.fossify.gallery.dialogs.ConfirmDeleteFolderDialog
import org.fossify.gallery.dialogs.ExcludeFolderDialog
import org.fossify.gallery.dialogs.PickMediumDialog
import org.fossify.gallery.extensions.addNoMedia
import org.fossify.gallery.extensions.checkAppendingHidden
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.emptyAndDisableTheRecycleBin
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.fixDateTaken
import org.fossify.gallery.extensions.getShortcutImage
import org.fossify.gallery.extensions.isThisOrParentFolderHidden
import org.fossify.gallery.extensions.loadImage
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.removeNoMedia
import org.fossify.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossify.gallery.extensions.tryCopyMoveFilesTo
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.FOLDER_MEDIA_CNT_BRACKETS
import org.fossify.gallery.helpers.FOLDER_MEDIA_CNT_LINE
import org.fossify.gallery.helpers.FOLDER_STYLE_ROUNDED_CORNERS
import org.fossify.gallery.helpers.FOLDER_STYLE_SQUARE
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.LOCATION_SD
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ROUNDED_CORNERS_BIG
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.ROUNDED_CORNERS_SMALL
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.interfaces.DirectoryOperationsListener
import org.fossify.gallery.models.AlbumCover
import org.fossify.gallery.models.Directory
import java.io.File
import java.util.Collections

class DirectoryAdapter(
    activity: BaseSimpleActivity,
    var dirs: ArrayList<Directory>,
    val listener: DirectoryOperationsListener?,
    recyclerView: MyRecyclerView,
    val isPickIntent: Boolean,
    val swipeRefreshLayout: SwipeRefreshLayout? = null,
    itemClick: (Any) -> Unit
) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract,
    RecyclerViewFastScroller.OnPopupTextUpdate {

    private val config = activity.config
    private val isListViewType = config.viewTypeFolders == VIEW_TYPE_LIST
    private var pinnedFolders = config.pinnedFolders
    private var scrollHorizontally = config.scrollHorizontally
    private var animateGifs = config.animateGifs
    private var cropThumbnails = config.cropThumbnails
    private var groupDirectSubfolders = config.groupDirectSubfolders
    private var currentDirectoriesHash = dirs.hashCode()
    private var lockedFolderPaths = ArrayList<String>()
    private var isDragAndDropping = false
    private var startReorderDragListener: StartReorderDragListener? = null

    private var showMediaCount = config.showFolderMediaCount
    private var folderStyle = config.folderStyle
    private var limitFolderTitle = config.limitFolderTitle
    var directorySorting = config.directorySorting
    var dateFormat = config.dateFormat
    var timeFormat = activity.getTimeFormat()

    init {
        setupDragListener(true)
        fillLockedFolders()
    }

    override fun getActionMenuId() = R.menu.cab_directories

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when {
            isListViewType -> DirectoryItemListBinding.inflate(layoutInflater, parent, false)
            folderStyle == FOLDER_STYLE_SQUARE -> DirectoryItemGridSquareBinding.inflate(layoutInflater, parent, false)
            else -> DirectoryItemGridRoundedCornersBinding.inflate(layoutInflater, parent, false)
        }

        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val dir = dirs.getOrNull(position) ?: return
        holder.bindView(dir, true, !isPickIntent) { itemView, adapterPosition ->
            setupView(itemView, dir, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = dirs.size

    override fun prepareActionMode(menu: Menu) {
        val selectedPaths = getSelectedPaths()
        if (selectedPaths.isEmpty()) {
            return
        }

        val isOneItemSelected = isOneItemSelected()
        menu.apply {
            findItem(R.id.cab_move_to_top).isVisible = isDragAndDropping
            findItem(R.id.cab_move_to_bottom).isVisible = isDragAndDropping

            findItem(R.id.cab_rename).isVisible = !selectedPaths.contains(FAVORITES) && !selectedPaths.contains(RECYCLE_BIN)
            findItem(R.id.cab_change_cover_image).isVisible = isOneItemSelected

            findItem(R.id.cab_lock).isVisible = selectedPaths.any { !config.isFolderProtected(it) }
            findItem(R.id.cab_unlock).isVisible = selectedPaths.any { config.isFolderProtected(it) }

            findItem(R.id.cab_empty_recycle_bin).isVisible = isOneItemSelected && selectedPaths.first() == RECYCLE_BIN
            findItem(R.id.cab_empty_disable_recycle_bin).isVisible = isOneItemSelected && selectedPaths.first() == RECYCLE_BIN

            findItem(R.id.cab_create_shortcut).isVisible = isOneItemSelected

            checkHideBtnVisibility(this, selectedPaths)
            checkPinBtnVisibility(this, selectedPaths)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_move_to_top -> moveSelectedItemsToTop()
            R.id.cab_move_to_bottom -> moveSelectedItemsToBottom()
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> renameDir()
            R.id.cab_pin -> pinFolders(true)
            R.id.cab_unpin -> pinFolders(false)
            R.id.cab_change_order -> changeOrder()
            R.id.cab_empty_recycle_bin -> tryEmptyRecycleBin(true)
            R.id.cab_empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
            R.id.cab_hide -> toggleFoldersVisibility(true)
            R.id.cab_unhide -> toggleFoldersVisibility(false)
            R.id.cab_exclude -> tryExcludeFolder()
            R.id.cab_lock -> tryLockFolder()
            R.id.cab_unlock -> unlockFolder()
            R.id.cab_copy_to -> copyFilesTo()
            R.id.cab_move_to -> moveFilesTo()
            R.id.cab_select_all -> selectAll()
            R.id.cab_create_shortcut -> tryCreateShortcut()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_photo -> tryChangeAlbumCover(false)
            R.id.cab_use_default -> tryChangeAlbumCover(true)
        }
    }

    override fun getSelectableItemCount() = dirs.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = dirs.getOrNull(position)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = dirs.indexOfFirst { it.path.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {
        if (isDragAndDropping) {
            notifyDataSetChanged()

            val reorderedFoldersList = dirs.map { it.path }
            config.customFoldersOrder = TextUtils.join("|||", reorderedFoldersList)
            config.directorySorting = SORT_BY_CUSTOM
        }

        isDragAndDropping = false
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed) {
            Glide.with(activity).clear(bindItem(holder.itemView).dirThumbnail)
        }
    }

    private fun checkHideBtnVisibility(menu: Menu, selectedPaths: ArrayList<String>) {
        menu.findItem(R.id.cab_hide).isVisible =
            (!isRPlus() || isExternalStorageManager()) && selectedPaths.any { !it.doesThisOrParentHaveNoMedia(HashMap(), null) }

        menu.findItem(R.id.cab_unhide).isVisible =
            (!isRPlus() || isExternalStorageManager()) && selectedPaths.any { it.doesThisOrParentHaveNoMedia(HashMap(), null) }
    }

    private fun checkPinBtnVisibility(menu: Menu, selectedPaths: ArrayList<String>) {
        val pinnedFolders = config.pinnedFolders
        menu.findItem(R.id.cab_pin).isVisible = selectedPaths.any { !pinnedFolders.contains(it) }
        menu.findItem(R.id.cab_unpin).isVisible = selectedPaths.any { pinnedFolders.contains(it) }
    }

    private fun moveSelectedItemsToTop() {
        selectedKeys.reversed().forEach { key ->
            val position = dirs.indexOfFirst { it.path.hashCode() == key }
            val tempItem = dirs[position]
            dirs.removeAt(position)
            dirs.add(0, tempItem)
        }

        notifyDataSetChanged()
    }

    private fun moveSelectedItemsToBottom() {
        selectedKeys.forEach { key ->
            val position = dirs.indexOfFirst { it.path.hashCode() == key }
            val tempItem = dirs[position]
            dirs.removeAt(position)
            dirs.add(dirs.size, tempItem)
        }

        notifyDataSetChanged()
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            val path = getFirstSelectedItemPath() ?: return
            if (path != FAVORITES && path != RECYCLE_BIN) {
                activity.handleLockedFolderOpening(path) { success ->
                    if (success) {
                        PropertiesDialog(activity, path, config.shouldShowHidden)
                    }
                }
            }
        } else {
            PropertiesDialog(activity, getSelectedPaths().filter {
                it != FAVORITES && it != RECYCLE_BIN && !config.isFolderProtected(it)
            }.toMutableList(), config.shouldShowHidden)
        }
    }

    private fun renameDir() {
        if (selectedKeys.size == 1) {
            val firstDir = getFirstSelectedItem() ?: return
            val sourcePath = firstDir.path
            val dir = File(sourcePath)
            if (activity.isAStorageRootFolder(dir.absolutePath)) {
                activity.toast(org.fossify.commons.R.string.rename_folder_root)
                return
            }

            activity.handleLockedFolderOpening(sourcePath) { success ->
                if (success) {
                    RenameItemDialog(activity, dir.absolutePath) {
                        activity.runOnUiThread {
                            firstDir.apply {
                                path = it
                                name = it.getFilenameFromPath()
                                tmb = File(it, tmb.getFilenameFromPath()).absolutePath
                            }
                            updateDirs(dirs)
                            ensureBackgroundThread {
                                try {
                                    activity.directoryDB.updateDirectoryAfterRename(firstDir.tmb, firstDir.name, firstDir.path, sourcePath)
                                    listener?.refreshItems()
                                } catch (e: Exception) {
                                    activity.showErrorToast(e)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val paths = getSelectedPaths().filter { !activity.isAStorageRootFolder(it) && !config.isFolderProtected(it) } as ArrayList<String>
            RenameItemsDialog(activity, paths) {
                listener?.refreshItems()
            }
        }
    }

    private fun toggleFoldersVisibility(hide: Boolean) {
        val selectedPaths = getSelectedPaths()
        if (hide && selectedPaths.contains(RECYCLE_BIN)) {
            config.showRecycleBinAtFolders = false
            if (selectedPaths.size == 1) {
                listener?.refreshItems()
                finishActMode()
            }
        }

        if (hide) {
            if (config.wasHideFolderTooltipShown) {
                hideFolders(selectedPaths)
            } else {
                config.wasHideFolderTooltipShown = true
                ConfirmationDialog(activity, activity.getString(R.string.hide_folder_description)) {
                    hideFolders(selectedPaths)
                }
            }
        } else {
            if (selectedPaths.any { it.isThisOrParentFolderHidden() }) {
                ConfirmationDialog(activity, "", R.string.cant_unhide_folder, org.fossify.commons.R.string.ok, 0) {}
                return
            }

            selectedPaths.filter { it != FAVORITES && it != RECYCLE_BIN && (selectedPaths.size == 1 || !config.isFolderProtected(it)) }.forEach {
                val path = it
                activity.handleLockedFolderOpening(path) { success ->
                    if (success) {
                        if (path.containsNoMedia()) {
                            activity.removeNoMedia(path) {
                                if (config.shouldShowHidden) {
                                    updateFolderNames()
                                } else {
                                    activity.runOnUiThread {
                                        listener?.refreshItems()
                                        finishActMode()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hideFolders(paths: ArrayList<String>) {
        for (path in paths) {
            activity.handleLockedFolderOpening(path) { success ->
                if (success) {
                    hideFolder(path)
                }
            }
        }
    }

    private fun tryEmptyRecycleBin(askConfirmation: Boolean) {
        if (askConfirmation) {
            activity.showRecycleBinEmptyingDialog {
                emptyRecycleBin()
            }
        } else {
            emptyRecycleBin()
        }
    }

    private fun emptyRecycleBin() {
        activity.handleLockedFolderOpening(RECYCLE_BIN) { success ->
            if (success) {
                activity.emptyTheRecycleBin {
                    listener?.refreshItems()
                }
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        activity.handleLockedFolderOpening(RECYCLE_BIN) { success ->
            if (success) {
                activity.showRecycleBinEmptyingDialog {
                    activity.emptyAndDisableTheRecycleBin {
                        listener?.refreshItems()
                    }
                }
            }
        }
    }

    private fun updateFolderNames() {
        val includedFolders = config.includedFolders
        val hidden = activity.getString(R.string.hidden)
        dirs.forEach {
            it.name = activity.checkAppendingHidden(it.path, hidden, includedFolders, ArrayList())
        }
        listener?.updateDirectories(dirs.toMutableList() as ArrayList)
        activity.runOnUiThread {
            updateDirs(dirs)
        }
    }

    private fun hideFolder(path: String) {
        activity.addNoMedia(path) {
            if (config.shouldShowHidden) {
                updateFolderNames()
            } else {
                val affectedPositions = ArrayList<Int>()
                val includedFolders = config.includedFolders
                val newDirs = dirs.filterIndexed { index, directory ->
                    val removeDir = directory.path.doesThisOrParentHaveNoMedia(HashMap(), null) && !includedFolders.contains(directory.path)
                    if (removeDir) {
                        affectedPositions.add(index)
                    }
                    !removeDir
                } as ArrayList<Directory>

                activity.runOnUiThread {
                    affectedPositions.sortedDescending().forEach {
                        notifyItemRemoved(it)
                    }

                    currentDirectoriesHash = newDirs.hashCode()
                    dirs = newDirs

                    finishActMode()
                    listener?.updateDirectories(newDirs)
                }
            }
        }
    }

    private fun tryExcludeFolder() {
        val selectedPaths = getSelectedPaths()
        val paths = selectedPaths.filter { it != PATH && it != RECYCLE_BIN && it != FAVORITES }.toSet()
        if (selectedPaths.contains(RECYCLE_BIN)) {
            config.showRecycleBinAtFolders = false
            if (selectedPaths.size == 1) {
                listener?.refreshItems()
                finishActMode()
            }
        }

        if (paths.size == 1) {
            ExcludeFolderDialog(activity, paths.toMutableList()) {
                listener?.refreshItems()
                finishActMode()
            }
        } else if (paths.size > 1) {
            config.addExcludedFolders(paths)
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun tryLockFolder() {
        if (config.wasFolderLockingNoticeShown) {
            lockFolder()
        } else {
            FolderLockingNoticeDialog(activity) {
                lockFolder()
            }
        }
    }

    private fun lockFolder() {
        SecurityDialog(activity, "", SHOW_ALL_TABS) { hash, type, success ->
            if (success) {
                getSelectedPaths().filter { !config.isFolderProtected(it) }.forEach {
                    config.addFolderProtection(it, hash, type)
                    lockedFolderPaths.add(it)
                }

                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun unlockFolder() {
        val paths = getSelectedPaths()
        val firstPath = paths.first()
        val tabToShow = config.getFolderProtectionType(firstPath)
        val hashToCheck = config.getFolderProtectionHash(firstPath)
        SecurityDialog(activity, hashToCheck, tabToShow) { hash, type, success ->
            if (success) {
                paths.filter { config.isFolderProtected(it) && config.getFolderProtectionType(it) == tabToShow && config.getFolderProtectionHash(it) == hashToCheck }
                    .forEach {
                        config.removeFolderProtection(it)
                        lockedFolderPaths.remove(it)
                    }

                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun pinFolders(pin: Boolean) {
        if (pin) {
            config.addPinnedFolders(getSelectedPaths().toHashSet())
        } else {
            config.removePinnedFolders(getSelectedPaths().toHashSet())
        }

        currentDirectoriesHash = 0
        pinnedFolders = config.pinnedFolders
        listener?.recheckPinnedFolders()
    }

    private fun changeOrder() {
        isDragAndDropping = true
        notifyDataSetChanged()
        actMode?.invalidate()

        if (startReorderDragListener == null) {
            val touchHelper = ItemTouchHelper(ItemMoveCallback(this, true))
            touchHelper.attachToRecyclerView(recyclerView)

            startReorderDragListener = object : StartReorderDragListener {
                override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                    touchHelper.startDrag(viewHolder)
                }
            }
        }
    }

    private fun copyFilesTo() {
        handleLockedFolderOpeningForFolders(getSelectedPaths()) {
            copyMoveTo(it, true)
        }
    }

    private fun moveFilesTo() {
        activity.handleDeletePasswordProtection {
            handleLockedFolderOpeningForFolders(getSelectedPaths()) {
                copyMoveTo(it, false)
            }
        }
    }

    private fun copyMoveTo(selectedPaths: Collection<String>, isCopyOperation: Boolean) {
        val paths = ArrayList<String>()
        val showHidden = config.shouldShowHidden
        selectedPaths.forEach {
            val filter = config.filterMedia
            File(it).listFiles()?.filter {
                !File(it.absolutePath).isDirectory &&
                    it.absolutePath.isMediaFile() && (showHidden || !it.name.startsWith('.')) &&
                    ((it.isImageFast() && filter and TYPE_IMAGES != 0) ||
                        (it.isVideoFast() && filter and TYPE_VIDEOS != 0) ||
                        (it.isGif() && filter and TYPE_GIFS != 0) ||
                        (it.isRawFast() && filter and TYPE_RAWS != 0) ||
                        (it.isSvg() && filter and TYPE_SVGS != 0))
            }?.mapTo(paths) { it.absolutePath }
        }

        val fileDirItems = paths.map { FileDirItem(it, it.getFilenameFromPath()) } as ArrayList<FileDirItem>
        activity.tryCopyMoveFilesTo(fileDirItems, isCopyOperation) {
            val destinationPath = it
            val newPaths = fileDirItems.map { "$destinationPath/${it.name}" }.toMutableList() as ArrayList<String>
            activity.rescanPaths(newPaths) {
                activity.fixDateTaken(newPaths, false)
            }

            config.tempFolderPath = ""
            listener?.refreshItems()
            finishActMode()
        }
    }

    private fun tryCreateShortcut() {
        activity.handleLockedFolderOpening(getFirstSelectedItemPath() ?: "") { success ->
            if (success) {
                createShortcut()
            }
        }
    }

    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val dir = getFirstSelectedItem() ?: return
            val path = dir.path
            val drawable = resources.getDrawable(R.drawable.shortcut_image).mutate()
            val coverThumbnail = config.parseAlbumCovers().firstOrNull { it.tmb == dir.path }?.tmb ?: dir.tmb
            activity.getShortcutImage(coverThumbnail, drawable) {
                val intent = Intent(activity, MediaActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                intent.putExtra(DIRECTORY, path)

                val shortcut = ShortcutInfo.Builder(activity, path)
                    .setShortLabel(dir.name)
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun askConfirmDelete() {
        when {
            config.isDeletePasswordProtectionOn -> activity.handleDeletePasswordProtection {
                deleteFolders()
            }

            config.skipDeleteConfirmation -> deleteFolders()
            else -> {
                val itemsCnt = selectedKeys.size
                if (itemsCnt == 1 && getSelectedItems().first().isRecycleBin()) {
                    ConfirmationDialog(
                        activity,
                        "",
                        org.fossify.commons.R.string.empty_recycle_bin_confirmation,
                        org.fossify.commons.R.string.yes,
                        org.fossify.commons.R.string.no
                    ) {
                        deleteFolders()
                    }
                    return
                }

                val items = if (itemsCnt == 1) {
                    val folder = getSelectedPaths().first().getFilenameFromPath()
                    "\"$folder\""
                } else {
                    resources.getQuantityString(org.fossify.commons.R.plurals.delete_items, itemsCnt, itemsCnt)
                }

                val fileDirItem = getFirstSelectedItem() ?: return
                val baseString = if (!config.useRecycleBin || config.tempSkipRecycleBin || (isOneItemSelected() && fileDirItem.areFavorites())) {
                    org.fossify.commons.R.string.deletion_confirmation
                } else {
                    org.fossify.commons.R.string.move_to_recycle_bin_confirmation
                }

                val question = String.format(resources.getString(baseString), items)
                val warning = resources.getQuantityString(org.fossify.commons.R.plurals.delete_warning, itemsCnt, itemsCnt)
                ConfirmDeleteFolderDialog(activity, question, warning) {
                    deleteFolders()
                }
            }
        }
    }

    private fun deleteFolders() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val SAFPath = getFirstSelectedItemPath() ?: return
        val selectedDirs = getSelectedItems()
        activity.handleSAFDialog(SAFPath) {
            if (!it) {
                return@handleSAFDialog
            }

            activity.handleSAFDialogSdk30(SAFPath) {
                if (!it) {
                    return@handleSAFDialogSdk30
                }

                var foldersToDelete = ArrayList<File>(selectedKeys.size)
                selectedDirs.forEach {
                    if (it.areFavorites() || it.isRecycleBin()) {
                        if (it.isRecycleBin()) {
                            tryEmptyRecycleBin(false)
                        } else {
                            ensureBackgroundThread {
                                activity.mediaDB.clearFavorites()
                                activity.favoritesDB.clearFavorites()
                                listener?.refreshItems()
                            }
                        }

                        if (selectedKeys.size == 1) {
                            finishActMode()
                        }
                    } else {
                        foldersToDelete.add(File(it.path))
                    }
                }

                handleLockedFolderOpeningForFolders(foldersToDelete.map { it.absolutePath }) {
                    listener?.deleteFolders(it.map { File(it) }.toMutableList() as ArrayList<File>)
                }
            }
        }
    }

    private fun handleLockedFolderOpeningForFolders(folders: Collection<String>, callback: (Collection<String>) -> Unit) {
        if (folders.size == 1) {
            activity.handleLockedFolderOpening(folders.first()) { success ->
                if (success) {
                    callback(folders)
                }
            }
        } else {
            val filtered = folders.filter { !config.isFolderProtected(it) }
            callback(filtered)
        }
    }

    private fun tryChangeAlbumCover(useDefault: Boolean) {
        activity.handleLockedFolderOpening(getFirstSelectedItemPath() ?: "") { success ->
            if (success) {
                changeAlbumCover(useDefault)
            }
        }
    }

    private fun changeAlbumCover(useDefault: Boolean) {
        if (selectedKeys.size != 1)
            return

        val path = getFirstSelectedItemPath() ?: return

        if (useDefault) {
            val albumCovers = getAlbumCoversWithout(path)
            storeCovers(albumCovers)
        } else {
            pickMediumFrom(path, path)
        }
    }

    private fun pickMediumFrom(targetFolder: String, path: String) {
        PickMediumDialog(activity, path) {
            if (File(it).isDirectory) {
                pickMediumFrom(targetFolder, it)
            } else {
                val albumCovers = getAlbumCoversWithout(path)
                val cover = AlbumCover(targetFolder, it)
                albumCovers.add(cover)
                storeCovers(albumCovers)
            }
        }
    }

    private fun getAlbumCoversWithout(path: String) = config.parseAlbumCovers().filterNot { it.path == path } as ArrayList

    private fun storeCovers(albumCovers: ArrayList<AlbumCover>) {
        config.albumCovers = Gson().toJson(albumCovers)
        finishActMode()
        listener?.refreshItems()
    }

    private fun getSelectedItems() = selectedKeys.mapNotNull { getItemWithKey(it) } as ArrayList<Directory>

    private fun getSelectedPaths() = getSelectedItems().map { it.path } as ArrayList<String>

    private fun getFirstSelectedItem() = getItemWithKey(selectedKeys.first())

    private fun getFirstSelectedItemPath() = getFirstSelectedItem()?.path

    private fun getItemWithKey(key: Int): Directory? = dirs.firstOrNull { it.path.hashCode() == key }

    private fun fillLockedFolders() {
        lockedFolderPaths.clear()
        dirs.map { it.path }.filter { config.isFolderProtected(it) }.forEach {
            lockedFolderPaths.add(it)
        }
    }

    fun updateDirs(newDirs: ArrayList<Directory>) {
        val directories = newDirs.clone() as ArrayList<Directory>
        if (directories.hashCode() != currentDirectoriesHash) {
            currentDirectoriesHash = directories.hashCode()
            dirs = directories
            fillLockedFolders()
            notifyDataSetChanged()
            finishActMode()
        }
    }

    fun updateAnimateGifs(animateGifs: Boolean) {
        this.animateGifs = animateGifs
        notifyDataSetChanged()
    }

    fun updateCropThumbnails(cropThumbnails: Boolean) {
        this.cropThumbnails = cropThumbnails
        notifyDataSetChanged()
    }

    private fun setupView(view: View, directory: Directory, holder: ViewHolder) {
        val isSelected = selectedKeys.contains(directory.path.hashCode())
        bindItem(view).apply {
            dirPath?.text = "${directory.path.substringBeforeLast("/")}/"
            val thumbnailType = when {
                directory.tmb.isVideoFast() -> TYPE_VIDEOS
                directory.tmb.isGif() -> TYPE_GIFS
                directory.tmb.isRawFast() -> TYPE_RAWS
                directory.tmb.isSvg() -> TYPE_SVGS
                else -> TYPE_IMAGES
            }

            dirCheck.beVisibleIf(isSelected)
            if (isSelected) {
                dirCheck.background?.applyColorFilter(properPrimaryColor)
                dirCheck.applyColorFilter(contrastColor)
            }

            if (isListViewType) {
                dirHolder.isSelected = isSelected
            }

            if (scrollHorizontally && !isListViewType && folderStyle == FOLDER_STYLE_ROUNDED_CORNERS) {
                (dirThumbnail.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ABOVE, dirName.id)

                val photoCntParams = (photoCnt.layoutParams as RelativeLayout.LayoutParams)
                val nameParams = (dirName.layoutParams as RelativeLayout.LayoutParams)
                nameParams.removeRule(RelativeLayout.BELOW)

                if (config.showFolderMediaCount == FOLDER_MEDIA_CNT_LINE) {
                    nameParams.addRule(RelativeLayout.ABOVE, photoCnt.id)
                    nameParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)

                    photoCntParams.removeRule(RelativeLayout.BELOW)
                    photoCntParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                } else {
                    nameParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
            }

            if (lockedFolderPaths.contains(directory.path)) {
                dirLock.beVisible()
                dirLock.background = ColorDrawable(root.context.getProperBackgroundColor())
                dirLock.applyColorFilter(root.context.getProperBackgroundColor().getContrastColor())
            } else {
                dirLock.beGone()
                val roundedCorners = when {
                    isListViewType -> ROUNDED_CORNERS_SMALL
                    folderStyle == FOLDER_STYLE_SQUARE -> ROUNDED_CORNERS_NONE
                    else -> ROUNDED_CORNERS_BIG
                }

                dirThumbnail.setBackgroundResource(
                    when (roundedCorners) {
                        ROUNDED_CORNERS_SMALL -> R.drawable.placeholder_rounded_small
                        ROUNDED_CORNERS_BIG -> R.drawable.placeholder_rounded_big
                        else -> R.drawable.placeholder_square
                    }
                )

                activity.loadImage(
                    type = thumbnailType,
                    path = directory.tmb,
                    target = dirThumbnail,
                    horizontalScroll = scrollHorizontally,
                    animateGifs = animateGifs,
                    cropThumbnails = cropThumbnails,
                    roundCorners = roundedCorners,
                    signature = directory.getKey(),
                    onError = {
                        dirThumbnail.scaleType = ImageView.ScaleType.CENTER
                        dirThumbnail.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_vector_warning_colored))
                    }
                )
            }

            dirPin.beVisibleIf(pinnedFolders.contains(directory.path))
            dirLocation.beVisibleIf(directory.location != LOCATION_INTERNAL)
            if (dirLocation.isVisible()) {
                dirLocation.setImageResource(if (directory.location == LOCATION_SD) org.fossify.commons.R.drawable.ic_sd_card_vector else org.fossify.commons.R.drawable.ic_usb_vector)
            }

            photoCnt.text = directory.subfoldersMediaCount.toString() + " items"
            photoCnt.beVisibleIf(showMediaCount == FOLDER_MEDIA_CNT_LINE)

            if (limitFolderTitle) {
                dirName.setSingleLine()
//                dirName.ellipsize = TextUtils.TruncateAt.MIDDLE
            }

            var nameCount = directory.name
            if (showMediaCount == FOLDER_MEDIA_CNT_BRACKETS) {
                nameCount += " (${directory.subfoldersMediaCount})"
            }

            if (groupDirectSubfolders) {
                if (directory.subfoldersCount > 1) {
                    nameCount += " [${directory.subfoldersCount}]"
                }
            }

            dirName.text = nameCount

            if (isListViewType || folderStyle == FOLDER_STYLE_ROUNDED_CORNERS) {
                photoCnt.setTextColor(textColor)
                dirName.setTextColor(textColor)
                dirLocation.applyColorFilter(textColor)
            }

            if (isListViewType) {
                dirPath?.setTextColor(textColor)
                dirPin.applyColorFilter(textColor)
                dirLocation.applyColorFilter(textColor)
                dirDragHandle.beVisibleIf(isDragAndDropping)
            } else {
                dirDragHandleWrapper?.beVisibleIf(isDragAndDropping)
            }

            if (isDragAndDropping) {
                dirDragHandle.applyColorFilter(textColor)
                dirDragHandle.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        startReorderDragListener?.requestDrag(holder)
                    }
                    false
                }
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(dirs, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(dirs, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {
        swipeRefreshLayout?.isEnabled = false
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {
        swipeRefreshLayout?.isEnabled = activity.config.enablePullToRefresh
    }

    override fun onChange(position: Int) = dirs.getOrNull(position)?.getBubbleText(directorySorting, activity, dateFormat, timeFormat) ?: ""

    private fun bindItem(view: View): DirectoryItemBinding {
        return when {
            isListViewType -> DirectoryItemListBinding.bind(view).toItemBinding()
            folderStyle == FOLDER_STYLE_SQUARE -> DirectoryItemGridSquareBinding.bind(view).toItemBinding()
            else -> DirectoryItemGridRoundedCornersBinding.bind(view).toItemBinding()
        }
    }
}
