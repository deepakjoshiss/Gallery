package org.fossify.gallery.aes

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R

class AESBreadcrumbs(context: Context, attrs: AttributeSet) : HorizontalScrollView(context, attrs) {
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val itemsLayout: LinearLayout
    private var textColor = context.getProperTextColor()
    private var fontSize = resources.getDimension(org.fossify.commons.R.dimen.bigger_text_size)
    private var lastPath = ""
    private var isLayoutDirty = true
    private var isScrollToSelectedItemPending = false
    private var isFirstScroll = true
    private var stickyRootInitialLeft = 0
    private var rootStartPadding = 0
    private var mBasePath: String = ""

    private val textColorStateList: ColorStateList
        get() = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(
                textColor,
                textColor.adjustAlpha(0.6f)
            )
        )

    var listener: BreadcrumbsListener? = null
    var isShownInDialog = false

    init {
        isHorizontalScrollBarEnabled = false
        itemsLayout = LinearLayout(context)
        itemsLayout.orientation = LinearLayout.HORIZONTAL
        rootStartPadding = paddingStart
        itemsLayout.setPaddingRelative(0, paddingTop, paddingEnd, paddingBottom)
        setPaddingRelative(0, 0, 0, 0)
        itemsLayout.gravity = Gravity.CENTER_VERTICAL
        addView(itemsLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        onGlobalLayout {
            stickyRootInitialLeft = if (itemsLayout.childCount > 0) {
                itemsLayout.getChildAt(0).left
            } else {
                0
            }
        }
    }

    private fun recomputeStickyRootLocation(left: Int) {
        stickyRootInitialLeft = left
        handleRootStickiness(scrollX)
    }

    private fun handleRootStickiness(scrollX: Int) {
        if (scrollX > stickyRootInitialLeft) {
            stickRoot(scrollX - stickyRootInitialLeft)
        } else {
            freeRoot()
        }
    }

    private fun freeRoot() {
        if (itemsLayout.childCount > 0) {
            itemsLayout.getChildAt(0).translationX = 0f
        }
    }

    private fun stickRoot(translationX: Int) {
//        if (itemsLayout.childCount > 0) {
//            val root = itemsLayout.getChildAt(0)
//            root.translationX = translationX.toFloat()
//            ViewCompat.setTranslationZ(root, translationZ)
//        }
    }

    override fun onScrollChanged(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
        handleRootStickiness(scrollX)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        isLayoutDirty = false
        if (isScrollToSelectedItemPending) {
            scrollToSelectedItem()
            isScrollToSelectedItemPending = false
        }

        recomputeStickyRootLocation(left)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        var heightMeasureSpec = heightMeasureSpec
        if (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST) {
            var height = context.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.breadcrumbs_layout_height)
            if (heightMode == MeasureSpec.AT_MOST) {
                height = height.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            }
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun scrollToSelectedItem() {
        if (isLayoutDirty) {
            isScrollToSelectedItemPending = true
            return
        }

        var selectedIndex = itemsLayout.childCount - 1
        val cnt = itemsLayout.childCount
        for (i in 0 until cnt) {
            val child = itemsLayout.getChildAt(i)
            if ((child.tag as? FileDirItem)?.path?.trimEnd('/') == lastPath.trimEnd('/')) {
                selectedIndex = i
                break
            }
        }

        val selectedItemView = itemsLayout.getChildAt(selectedIndex)
        val scrollX = if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            selectedItemView.left - itemsLayout.paddingStart
        } else {
            selectedItemView.right - width + itemsLayout.paddingStart
        }

        if (!isFirstScroll && isShown) {
            smoothScrollTo(scrollX, 0)
        } else {
            scrollTo(scrollX, 0)
        }

        isFirstScroll = false
    }

    override fun requestLayout() {
        isLayoutDirty = true
        super.requestLayout()
    }

    fun setBreadcrumb(fullPath: String) {
        lastPath = fullPath

        itemsLayout.removeAllViews()

        if (mBasePath == fullPath) {
            addBreadcrumb(AESDirItem(fullPath, "Data", true, 0, 0, 0), 0, false)
            scrollTo(0, 0);
            return
        }

        var currPath = ""
        val dirs = fullPath.removePrefix(mBasePath).split("/").dropWhile(String::isEmpty)
        ensureBackgroundThread {
            for (i in dirs.indices) {
                val dir = dirs[i]
                if (dir.isEmpty()) {
                    continue
                }
                currPath = "$currPath/$dir"
                val item = AESHelper.decryptAlbumData(AESDirItem("$mBasePath$currPath", dir, true, 0, 0, 0))
                post {
                    addBreadcrumb(item, i, true)
                }
            }
            post {
                scrollToSelectedItem()
            }
        }

    }

    private fun addBreadcrumb(item: AESDirItem, index: Int, addPrefix: Boolean) {
        TextView(context).apply {
            //val view: TextView = this as TextView
            var textToAdd = item.displayName.ifEmpty { item.name }
            if (addPrefix) {
                textToAdd = "> $textToAdd"
            }
            val padding = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
            setPadding(padding, padding * 2, padding, padding * 2)
            isActivated = item.path.trimEnd('/') == lastPath.trimEnd('/')
            text = textToAdd
            setTextColor(textColorStateList)
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, org.fossify.commons.R.drawable.ripple_background)
            maxEms = 10
            maxLines = 1
            typeface = ResourcesCompat.getFont(context, R.font.sfpro_display_semibold)
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

            itemsLayout.addView(this)

            setOnClickListener { v ->
                if (itemsLayout.getChildAt(index) != null && itemsLayout.getChildAt(index) == v) {
                    if ((v.tag as? AESDirItem)?.path?.trimEnd('/') == lastPath.trimEnd('/')) {
                        scrollToSelectedItem()
                    } else {
                        listener?.breadcrumbClicked(index)
                    }
                }
            }

            tag = item
        }

    }

    fun setBasePath(path: String) {
        mBasePath = path
    }

    fun updateColor(color: Int) {
        textColor = color
        setBreadcrumb(lastPath)
    }

    fun updateFontSize(size: Float, updateTexts: Boolean) {
        fontSize = size
        if (updateTexts) {
            setBreadcrumb(lastPath)
        }
    }

    fun removeBreadcrumb() {
        itemsLayout.removeView(itemsLayout.getChildAt(itemsLayout.childCount - 1))
    }

    fun getItem(index: Int) = itemsLayout.getChildAt(index).tag as AESDirItem

    fun getLastItem() = itemsLayout.getChildAt(itemsLayout.childCount - 1).tag as AESDirItem

    fun getItemCount() = itemsLayout.childCount

    interface BreadcrumbsListener {
        fun breadcrumbClicked(id: Int)
    }
}
