package org.fossify.gallery.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.appcompat.content.res.AppCompatResources
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import org.fossify.commons.extensions.actionBarHeight
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getFilenameFromUri
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.navigationBarHeight
import org.fossify.commons.extensions.navigationBarOnSide
import org.fossify.commons.extensions.navigationBarWidth
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.portrait
import org.fossify.commons.extensions.setDrawablesRelativeWithIntrinsicBounds
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.statusBarHeight
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityVideoPlayerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.extensions.getFriendlyMessage
import org.fossify.gallery.extensions.hasNavBar
import org.fossify.gallery.extensions.hideSystemUI
import org.fossify.gallery.extensions.mute
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.shareMediumPath
import org.fossify.gallery.extensions.showSystemUI
import org.fossify.gallery.extensions.unmute
import org.fossify.gallery.fragments.PlaybackSpeedFragment
import org.fossify.gallery.helpers.DRAG_THRESHOLD
import org.fossify.gallery.helpers.EXOPLAYER_MAX_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_MIN_BUFFER_MS
import org.fossify.gallery.helpers.FAST_FORWARD_VIDEO_MS
import org.fossify.gallery.helpers.GO_TO_NEXT_ITEM
import org.fossify.gallery.helpers.GO_TO_PREV_ITEM
import org.fossify.gallery.helpers.HIDE_SYSTEM_UI_DELAY
import org.fossify.gallery.helpers.MAX_CLOSE_DOWN_GESTURE_DURATION
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import org.fossify.gallery.helpers.SHOW_NEXT_ITEM
import org.fossify.gallery.helpers.SHOW_PREV_ITEM
import org.fossify.gallery.interfaces.PlaybackSpeedListener
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.min

@UnstableApi
open class VideoPlayerActivity : SimpleActivity(), SeekBar.OnSeekBarChangeListener,
    TextureView.SurfaceTextureListener, PlaybackSpeedListener {
    companion object {
        private const val PLAY_WHEN_READY_DRAG_DELAY = 100L
        private const val UPDATE_INTERVAL_MS = 250L
    }

    private var mIsFullscreen = false
    private var mIsPlaying = false
    private var mWasVideoStarted = false
    private var mIsDragged = false
    private var mIsOrientationLocked = false
    private var mScreenWidth = 0
    private var mCurrTime = 0L
    private var mDuration = 0L
    private var mDragThreshold = 0f
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mTouchDownTime = 0L
    private var mProgressAtDown = 0L
    private var mCloseDownThreshold = 100f

    private var mUri: Uri? = null
    private var mExoPlayer: ExoPlayer? = null
    private var mVideoSize = Point(0, 0)
    private var mTimerHandler = Handler()
    private var mPlayWhenReadyHandler = Handler()

    private var mIgnoreCloseDown = false

    private val binding by viewBinding(ActivityVideoPlayerBinding::inflate)

    public override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        setupOrientation()
        checkNotchSupport()
        initPlayer()
    }

    override fun onResume() {
        super.onResume()
        binding.topShadow.layoutParams.height = statusBarHeight + actionBarHeight
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (config.blackBackground) {
            binding.videoPlayerHolder.background = ColorDrawable(Color.BLACK)
        }

        if (config.maxBrightness) {
            val attributes = window.attributes
            attributes.screenBrightness = 1f
            window.attributes = attributes
        }

        updateTextColors(binding.videoPlayerHolder)

        if (!portrait && navigationBarOnSide && navigationBarWidth > 0) {
            binding.videoToolbar.setPadding(0, 0, navigationBarWidth, 0)
        } else {
            binding.videoToolbar.setPadding(0, 0, 0, 0)
        }
    }

    override fun onPause() {
        super.onPause()
        pauseVideo()

        if (config.rememberLastVideoPosition && mWasVideoStarted) {
            saveVideoProgress()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            pauseVideo()
            binding.bottomVideoTimeHolder.videoCurrTime.text = 0.getFormattedDuration()
            releaseExoPlayer()
            binding.bottomVideoTimeHolder.videoSeekbar.progress = 0
            mTimerHandler.removeCallbacksAndMessages(null)
            mPlayWhenReadyHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun setupOptionsMenu() {
        (binding.videoAppbar.layoutParams as RelativeLayout.LayoutParams).topMargin =
            statusBarHeight
        binding.videoToolbar.apply {
            setTitleTextColor(Color.WHITE)
            overflowIcon = resources.getColoredDrawableWithColor(
                org.fossify.commons.R.drawable.ic_three_dots_vector,
                Color.WHITE
            )
            navigationIcon = resources.getColoredDrawableWithColor(
                org.fossify.commons.R.drawable.ic_arrow_left_vector,
                Color.WHITE
            )
        }

        updateMenuItemColors(binding.videoToolbar.menu, forceWhiteIcons = true)
        binding.videoToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_change_orientation -> changeOrientation()
                R.id.menu_open_with -> openPath(mUri!!.toString(), true)
                R.id.menu_share -> shareMediumPath(mUri!!.toString())
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }

        binding.videoToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setVideoSize()
        initTimeHolder()
        binding.videoSurfaceFrame.onGlobalLayout {
            binding.videoSurfaceFrame.controller.resetState()
        }

        binding.topShadow.layoutParams.height = statusBarHeight + actionBarHeight
        (binding.videoAppbar.layoutParams as RelativeLayout.LayoutParams).topMargin =
            statusBarHeight
        if (!portrait && navigationBarOnSide && navigationBarWidth > 0) {
            binding.videoToolbar.setPadding(0, 0, navigationBarWidth, 0)
        } else {
            binding.videoToolbar.setPadding(0, 0, 0, 0)
        }
    }

    private fun setupOrientation() {
        if (!mIsOrientationLocked) {
            if (config.screenRotation == ROTATE_BY_DEVICE_ROTATION) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } else if (config.screenRotation == ROTATE_BY_SYSTEM_SETTING) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun initPlayer() {
        mUri = intent.data ?: return
        binding.videoToolbar.title = getFilenameFromUri(mUri!!)
        initTimeHolder()

        showSystemUI(true)
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            val isFullscreen = visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
            fullscreenToggled(isFullscreen)
        }

        binding.bottomVideoTimeHolder.videoCurrTime.setOnClickListener { doSkip(false) }
        binding.bottomVideoTimeHolder.videoDuration.setOnClickListener { doSkip(true) }
        binding.bottomVideoTimeHolder.videoTogglePlayPause.setOnClickListener { togglePlayPause() }
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.setOnClickListener { showPlaybackSpeedPicker() }
        binding.bottomVideoTimeHolder.videoToggleMute.setOnClickListener {
            config.muteVideos = !config.muteVideos
            updatePlayerMuteState()
        }

        binding.videoSurfaceFrame.setOnClickListener { toggleFullscreen() }
        binding.videoSurfaceFrame.controller.settings.swallowDoubleTaps = true

        binding.bottomVideoTimeHolder.videoNextFile.beVisibleIf(
            intent.getBooleanExtra(
                SHOW_NEXT_ITEM,
                false
            )
        )
        binding.bottomVideoTimeHolder.videoNextFile.setOnClickListener { handleNextFile() }

        binding.bottomVideoTimeHolder.videoPrevFile.beVisibleIf(
            intent.getBooleanExtra(
                SHOW_PREV_ITEM,
                false
            )
        )
        binding.bottomVideoTimeHolder.videoPrevFile.setOnClickListener { handlePrevFile() }

        val gestureDetector =
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    handleDoubleTap(e.rawX)
                    return true
                }
            })

        binding.videoSurfaceFrame.setOnTouchListener { view, event ->
            handleEvent(event)
            gestureDetector.onTouchEvent(event)
            false
        }

        initExoPlayer()
        binding.videoSurface.surfaceTextureListener = this

        if (config.allowVideoGestures) {
            binding.videoBrightnessController.initialize(
                this,
                binding.slideInfo,
                true,
                binding.videoPlayerHolder,
                singleTap = { x, y ->
                    toggleFullscreen()
                },
                doubleTap = { x, y ->
                    doSkip(false)
                })

            binding.videoVolumeController.initialize(
                this,
                binding.slideInfo,
                false,
                binding.videoPlayerHolder,
                singleTap = { x, y ->
                    toggleFullscreen()
                },
                doubleTap = { x, y ->
                    doSkip(true)
                })
        } else {
            binding.videoBrightnessController.beGone()
            binding.videoVolumeController.beGone()
        }

        if (config.hideSystemUI) {
            Handler().postDelayed({
                fullscreenToggled(true)
            }, HIDE_SYSTEM_UI_DELAY)
        }

        mDragThreshold = DRAG_THRESHOLD * resources.displayMetrics.density
    }

    private fun initExoPlayer() {
        val dataSpec = DataSpec(mUri!!)
        val fileDataSource = ContentDataSource(applicationContext)
        try {
            fileDataSource.open(dataSpec)
        } catch (e: Exception) {
            showErrorToast(e)
        }

        val factory = DataSource.Factory { fileDataSource }
        val mediaSource: MediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(fileDataSource.uri!!))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MAX_BUFFER_MS,
                EXOPLAYER_MIN_BUFFER_MS,
                EXOPLAYER_MIN_BUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        mExoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(applicationContext))
            .setSeekParameters(SeekParameters.EXACT)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setPlaybackSpeed(config.playbackSpeed)
                setMediaSource(mediaSource)
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), false
                )
                if (config.loopVideos) {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
                prepare()
                initListeners()
            }

        updatePlayerMuteState()
    }

    private fun ExoPlayer.initListeners() {
        addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                @Player.DiscontinuityReason reason: Int
            ) {
                // Reset progress views when video loops.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    binding.bottomVideoTimeHolder.videoSeekbar.progress = 0
                    binding.bottomVideoTimeHolder.videoCurrTime.text = 0.getFormattedDuration()
                }
            }

            override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> videoPrepared()
                    Player.STATE_ENDED -> videoCompleted()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                mVideoSize.x = videoSize.width
                mVideoSize.y = videoSize.height
                setVideoSize()
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                binding.errorMessageHolder.errorMessage.apply {
                    if (error != null) {
                        text = error.getFriendlyMessage(context)
                        setTextColor(if (context.config.blackBackground) Color.WHITE else context.getProperTextColor())
                        fadeIn()
                    } else {
                        beGone()
                    }
                }
            }
        })
    }

    private fun videoPrepared() {
        if (!mWasVideoStarted) {
            binding.bottomVideoTimeHolder.videoTogglePlayPause.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.beVisible()
            binding.bottomVideoTimeHolder.videoToggleMute.beVisible()
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
                "${DecimalFormat("#.##").format(config.playbackSpeed)}x"
            mDuration = mExoPlayer!!.duration
            binding.bottomVideoTimeHolder.videoSeekbar.max = mDuration.toInt()
            binding.bottomVideoTimeHolder.videoDuration.text = mDuration.getFormattedDuration()
            setPosition(mCurrTime)
            updatePlaybackSpeed(config.playbackSpeed)

            if (config.rememberLastVideoPosition) {
                setLastVideoSavedPosition()
            }

            if (config.autoplayVideos) {
                resumeVideo()
            } else {
                binding.bottomVideoTimeHolder.videoTogglePlayPause.setImageResource(org.fossify.commons.R.drawable.ic_play_outline_vector)
            }
        }
    }

    private fun handleDoubleTap(x: Float) {
        val instantWidth = mScreenWidth / 7
        when {
            x <= instantWidth -> doSkip(false)
            x >= mScreenWidth - instantWidth -> doSkip(true)
            else -> togglePlayPause()
        }
    }

    private fun resumeVideo() {
        binding.bottomVideoTimeHolder.videoTogglePlayPause.setImageResource(org.fossify.commons.R.drawable.ic_pause_outline_vector)
        if (mExoPlayer == null) {
            return
        }

        val wasEnded = didVideoEnd()
        if (wasEnded) {
            setPosition(0)
        }

        mWasVideoStarted = true
        mIsPlaying = true
        mExoPlayer?.playWhenReady = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun pauseVideo() {
        binding.bottomVideoTimeHolder.videoTogglePlayPause.setImageResource(org.fossify.commons.R.drawable.ic_play_outline_vector)
        if (mExoPlayer == null) {
            return
        }

        mIsPlaying = false
        if (!didVideoEnd()) {
            mExoPlayer?.playWhenReady = false
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun togglePlayPause() {
        mIsPlaying = !mIsPlaying
        if (mIsPlaying) {
            resumeVideo()
        } else {
            pauseVideo()
        }
    }

    private fun updatePlayerMuteState() {
        val isMuted = config.muteVideos
        val drawableId = if (isMuted) {
            mExoPlayer?.mute()
            R.drawable.ic_vector_speaker_off
        } else {
            mExoPlayer?.unmute()
            R.drawable.ic_vector_speaker_on
        }

        binding.bottomVideoTimeHolder.videoToggleMute.setImageDrawable(
            AppCompatResources.getDrawable(this, drawableId)
        )
    }

    private fun setPosition(milliseconds: Long) {
        mExoPlayer?.seekTo(milliseconds)
        binding.bottomVideoTimeHolder.videoSeekbar.progress = milliseconds.toInt()
        binding.bottomVideoTimeHolder.videoCurrTime.text = milliseconds.getFormattedDuration()
    }

    private fun setLastVideoSavedPosition() {
        val seconds = config.getLastVideoPosition(mUri.toString())
        if (seconds > 0) {
            setPosition(seconds * 1000L)
        }
    }

    private fun videoCompleted() {
        if (mExoPlayer == null) {
            return
        }

        clearLastVideoSavedProgress()
        mCurrTime = mExoPlayer!!.duration
        binding.bottomVideoTimeHolder.videoSeekbar.progress =
            binding.bottomVideoTimeHolder.videoSeekbar.max
        binding.bottomVideoTimeHolder.videoCurrTime.text = mDuration.getFormattedDuration()
        pauseVideo()
    }

    private fun didVideoEnd(): Boolean {
        val currentPos = mExoPlayer?.currentPosition ?: 0
        val duration = mExoPlayer?.duration ?: 0
        return currentPos != 0L && currentPos >= duration
    }

    private fun saveVideoProgress() {
        if (!didVideoEnd()) {
            config.saveLastVideoPosition(
                mUri.toString(),
                mExoPlayer!!.currentPosition.toInt() / 1000
            )
        }
    }

    private fun clearLastVideoSavedProgress() {
        config.removeLastVideoPosition(mUri.toString())
    }

    private fun setVideoSize() {
        val videoProportion = mVideoSize.x.toFloat() / mVideoSize.y.toFloat()
        val display = windowManager.defaultDisplay
        val screenWidth: Int
        val screenHeight: Int

        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        screenWidth = realMetrics.widthPixels
        screenHeight = realMetrics.heightPixels

        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()

        binding.videoSurface.layoutParams.apply {
            if (videoProportion > screenProportion) {
                width = screenWidth
                height = (screenWidth.toFloat() / videoProportion).toInt()
            } else {
                width = (videoProportion * screenHeight.toFloat()).toInt()
                height = screenHeight
            }
            binding.videoSurface.layoutParams = this
        }

        val multiplier = if (screenWidth > screenHeight) 0.5 else 0.8
        mScreenWidth = (screenWidth * multiplier).toInt()

        if (config.screenRotation == ROTATE_BY_ASPECT_RATIO) {
            if (mVideoSize.x > mVideoSize.y) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else if (mVideoSize.x < mVideoSize.y) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private fun changeOrientation() {
        mIsOrientationLocked = true
        requestedOrientation =
            if (resources.configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
    }

    private fun toggleFullscreen() {
        fullscreenToggled(!mIsFullscreen)
    }

    private fun fullscreenToggled(isFullScreen: Boolean) {
        mIsFullscreen = isFullScreen
        if (isFullScreen) {
            hideSystemUI(true)
        } else {
            showSystemUI(true)
        }

        val newAlpha = if (isFullScreen) 0f else 1f
        arrayOf(
            binding.bottomVideoTimeHolder.videoPrevFile,
            binding.bottomVideoTimeHolder.videoTogglePlayPause,
            binding.bottomVideoTimeHolder.videoNextFile,
            binding.bottomVideoTimeHolder.videoPlaybackSpeed,
            binding.bottomVideoTimeHolder.videoToggleMute,
            binding.bottomVideoTimeHolder.videoCurrTime,
            binding.bottomVideoTimeHolder.videoSeekbar,
            binding.bottomVideoTimeHolder.videoDuration,
            binding.topShadow,
            binding.videoBottomGradient
        ).forEach {
            it.animate().alpha(newAlpha).start()
        }
        binding.bottomVideoTimeHolder.videoSeekbar.setOnSeekBarChangeListener(if (mIsFullscreen) null else this)
        arrayOf(
            binding.bottomVideoTimeHolder.videoPrevFile,
            binding.bottomVideoTimeHolder.videoNextFile,
            binding.bottomVideoTimeHolder.videoPlaybackSpeed,
            binding.bottomVideoTimeHolder.videoToggleMute,
            binding.bottomVideoTimeHolder.videoCurrTime,
            binding.bottomVideoTimeHolder.videoDuration,
        ).forEach {
            it.isClickable = !mIsFullscreen
        }

        binding.videoAppbar.animate().alpha(newAlpha).withStartAction {
            binding.videoAppbar.beVisible()
        }.withEndAction {
            binding.videoAppbar.beVisibleIf(newAlpha == 1f)
        }.start()
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        fragment.show(supportFragmentManager, PlaybackSpeedFragment::class.java.simpleName)
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag as? Boolean) {
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag = isSlow

            val drawableId =
                if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            binding.bottomVideoTimeHolder.videoPlaybackSpeed
                .setDrawablesRelativeWithIntrinsicBounds(
                    AppCompatResources.getDrawable(
                        this,
                        drawableId
                    )
                )
        }

        @SuppressLint("SetTextI18n")
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
            "${DecimalFormat("#.##").format(speed)}x"
        mExoPlayer?.setPlaybackSpeed(speed)
    }

    private fun initTimeHolder() {
        var right = 0
        var bottom = 0

        if (hasNavBar()) {
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                bottom += navigationBarHeight
            } else {
                right += navigationBarWidth
                bottom += navigationBarHeight
            }
        }

        binding.bottomVideoTimeHolder.videoTimeHolder.setPadding(0, 0, right, bottom)
        binding.bottomVideoTimeHolder.videoSeekbar.setOnSeekBarChangeListener(this)
        binding.bottomVideoTimeHolder.videoSeekbar.max = mDuration.toInt()
        binding.bottomVideoTimeHolder.videoDuration.text = mDuration.getFormattedDuration()
        binding.bottomVideoTimeHolder.videoCurrTime.text = mCurrTime.getFormattedDuration()
        setupTimer()
    }

    private fun setupTimer() {
        runOnUiThread(object : Runnable {
            override fun run() {
                if (mExoPlayer != null && !mIsDragged && mIsPlaying) {
                    mCurrTime = mExoPlayer!!.currentPosition
                    binding.bottomVideoTimeHolder.videoSeekbar.progress = mCurrTime.toInt()
                    binding.bottomVideoTimeHolder.videoCurrTime.text =
                        mCurrTime.getFormattedDuration()
                }

                mTimerHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        })
    }

    private fun doSkip(forward: Boolean) {
        if (mExoPlayer == null) {
            return
        }

        val curr = mExoPlayer!!.currentPosition
        var newPosition =
            if (forward) curr + FAST_FORWARD_VIDEO_MS else curr - FAST_FORWARD_VIDEO_MS
        newPosition = newPosition.coerceIn(0, mExoPlayer!!.duration)
        setPosition(newPosition)
        if (!mIsPlaying) {
            togglePlayPause()
        }
    }

    private fun handleEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.rawX
                mTouchDownY = event.rawY
                mTouchDownTime = System.currentTimeMillis()
                mProgressAtDown = mExoPlayer!!.currentPosition
            }

            MotionEvent.ACTION_POINTER_DOWN -> mIgnoreCloseDown = true
            MotionEvent.ACTION_MOVE -> {
                val diffX = event.rawX - mTouchDownX
                val diffY = event.rawY - mTouchDownY

                if (mIsDragged || (Math.abs(diffX) > mDragThreshold && Math.abs(diffX) > Math.abs(
                        diffY
                    )) && binding.videoSurfaceFrame.controller.state.zoom == 1f
                ) {
                    if (!mIsDragged) {
                        arrayOf(
                            binding.bottomVideoTimeHolder.videoCurrTime,
                            binding.bottomVideoTimeHolder.videoSeekbar,
                            binding.bottomVideoTimeHolder.videoDuration,
                        ).forEach {
                            it.animate().alpha(1f).start()
                        }
                    }
                    mIgnoreCloseDown = true
                    mIsDragged = true
                    var percent = ((diffX / mScreenWidth) * 100).toInt()
                    percent = min(100, max(-100, percent))

                    val skipLength = mDuration * (percent.toDouble() / 100)
                    var newProgress = mProgressAtDown + skipLength
                    newProgress = newProgress.coerceIn(0.0, mExoPlayer!!.duration.toDouble())
                    setPosition(newProgress.toLong())
                    resetPlayWhenReady()
                }
            }

            MotionEvent.ACTION_UP -> {
                val diffX = mTouchDownX - event.rawX
                val diffY = mTouchDownY - event.rawY

                val downGestureDuration = System.currentTimeMillis() - mTouchDownTime
                if (config.allowDownGesture && !mIgnoreCloseDown && Math.abs(diffY) > Math.abs(diffX) && diffY < -mCloseDownThreshold &&
                    downGestureDuration < MAX_CLOSE_DOWN_GESTURE_DURATION &&
                    binding.videoSurfaceFrame.controller.state.zoom == 1f
                ) {
                    supportFinishAfterTransition()
                }

                mIgnoreCloseDown = false
                if (mIsDragged) {
                    if (mIsFullscreen) {
                        arrayOf(
                            binding.bottomVideoTimeHolder.videoCurrTime,
                            binding.bottomVideoTimeHolder.videoSeekbar,
                            binding.bottomVideoTimeHolder.videoDuration,
                        ).forEach {
                            it.animate().alpha(0f).start()
                        }
                    }

                    if (!mIsPlaying) {
                        togglePlayPause()
                    }
                }
                mIsDragged = false
            }
        }
    }

    private fun handleNextFile() {
        Intent().apply {
            putExtra(GO_TO_NEXT_ITEM, true)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    private fun handlePrevFile() {
        Intent().apply {
            putExtra(GO_TO_PREV_ITEM, true)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    private fun resetPlayWhenReady() {
        mExoPlayer?.playWhenReady = false
        mPlayWhenReadyHandler.removeCallbacksAndMessages(null)
        mPlayWhenReadyHandler.postDelayed({
            mExoPlayer?.playWhenReady = true
        }, PLAY_WHEN_READY_DRAG_DELAY)
    }

    private fun releaseExoPlayer() {
        mExoPlayer?.apply {
            stop()
            release()
        }
        mExoPlayer = null
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (mExoPlayer != null && fromUser) {
            setPosition(progress.toLong())
            resetPlayWhenReady()
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        mIsDragged = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (mExoPlayer == null)
            return

        if (mIsPlaying) {
            mExoPlayer!!.playWhenReady = true
        } else {
            togglePlayPause()
        }

        mIsDragged = false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mExoPlayer?.setVideoSurface(Surface(binding.videoSurface.surfaceTexture))
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
}
