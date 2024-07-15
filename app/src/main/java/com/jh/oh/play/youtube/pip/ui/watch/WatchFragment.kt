package com.jh.oh.play.youtube.pip.ui.watch

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import com.alexvasilkov.gestures.GestureController
import com.alexvasilkov.gestures.Settings.Fit
import com.jh.oh.play.youtube.pip.BaseFragment
import com.jh.oh.play.youtube.pip.R
import com.jh.oh.play.youtube.pip.databinding.FragmentWatchBinding
import com.jh.oh.play.youtube.pip.ui.main.MainViewModel
import com.jh.oh.play.youtube.pip.utils.CommonUtils
import timber.log.Timber


class WatchFragment : BaseFragment<FragmentWatchBinding, WatchViewModel>(
    R.layout.fragment_watch
), View.OnClickListener,
    View.OnTouchListener {
    override val viewModel: WatchViewModel by viewModels()
    override val parentViewModel: MainViewModel by activityViewModels()

    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var contentObserver: ContentObserver
    private lateinit var orientationListener: OrientationEventListener

    private var rotationEnable: Boolean = false

    private var player: ExoPlayer? = null
    private var playWhenReady: Boolean = true
    private var currentWindow: Int = 0
    private var playbackPosition: Long = 0

    private var previousOrientation: Int = OrientationEventListener.ORIENTATION_UNKNOWN

    private var isTracking: Boolean = false
    private var isShowBottomReturn: Boolean = false

    private var mActivePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var mLastTouchY: Float = 0f
    private var mPosY: Float = 0f

    private var isHorizontalView: Boolean = true

    companion object {
        const val PAGE_CODE: Int = 100
    }

    private val watchHandler: Handler = Handler(
        Looper.getMainLooper()
    ) {
        when(it.what) {
            WatchConst.Param.HANDLER_PLAYER_SEEK -> updatePlayerProgress()
            WatchConst.Param.HANDLER_HIDE_MENU ->
                binding.motionLayoutRoot.setTransitionToMenu(MotionConst.Param.MENU_HIDE)
            else -> {}
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        arguments?.let {
            isHorizontalView = it.getBoolean(WatchConst.Bundle.IS_HORIZONTAL, true)
        }
    }

    override fun onStart() {
        super.onStart()

        binding.motionLayoutRoot.setTransitionToMenu(MotionConst.Param.MENU_SHOW)
    }

    override fun onStop() {
        super.onStop()

        pausePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 캡쳐 및 녹화 방지 초기화
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // 방송 영상 player release
        releasePlayer()

        // 각종 timer 및 job cancel
        cancelHideMenu()
        cancelPlayerProgress()

        // orientationListener 초기화
        requireActivity().contentResolver.unregisterContentObserver(contentObserver)
        setEnableOrientation(false)
        // onBackPressed 이벤트 초기화
        backPressedCallback.remove()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setLayout()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)

        when(isInPictureInPictureMode) {
            true -> {
                Handler(Looper.getMainLooper()).post {
                    if(!binding.motionLayoutRoot.isStateViewFull()) {
                        binding.motionLayoutRoot.setTransitionToView(MotionConst.Param.VIEW_FULL)
                    }
                    if(binding.motionLayoutRoot.isStateBottomShow()) {
                        isShowBottomReturn = true
                        binding.motionLayoutRoot.setTransitionToBottomVisible(MotionConst.Param.BOTTOM_HIDE)
                    }
                    if(binding.motionLayoutRoot.isStateMenuShow()) {
                        binding.motionLayoutRoot.setTransitionToMenu(MotionConst.Param.MENU_HIDE)
                    }
                }

                binding.exoPlayerProgress.isInvisible = true
            }
            false -> {
                if (isShowBottomReturn) {
                    isShowBottomReturn = false
                    binding.motionLayoutRoot.setTransitionToBottomVisible(MotionConst.Param.BOTTOM_SHOW)
                }

                binding.exoPlayerProgress.isInvisible = false
            }
        }
    }

    override fun loadView() {
        super.loadView()

        parentViewModel.setBottomMenuMotion(1f)

        binding.playerContainer.exoPlayerContainer.controller.settings.apply {
            isZoomEnabled = true
            isDoubleTapEnabled = true
            maxZoom = 4f
            minZoom = 1f
            doubleTapZoom = 1f

            gravity = Gravity.CENTER
            fitMethod = Fit.HORIZONTAL
        }

        initializePlayer()
        playVideo()

        binding.motionLayoutRoot.doOnAttach {
            binding.motionLayoutRoot.initializeLayout(isHorizontalView)
            // 오버레이 메뉴 UI 상태 변경
            setOverlayMenu()
            // 하단 영역 UI 상태 변경
            setBottomLayout()
            setLayout()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun registerListener() {
        super.registerListener()

        // http://bts.popkontv.kr/issues/10251 이슈 대응
        // OS 13에서 앱 설정에 Push를 off하면 앱 process를 종료한다.
        // 이때 앱 History 목록에서 앱을 선택해서 실행하면 화면을 OnCreate부터 새로 그린다.
        // 이러한 과정에서 onAttach가 실행되지 않아 onAttach에서 정의하단 backPressedCallback을
        // registerListener로 이동
        if(::backPressedCallback.isInitialized.not()) {
            backPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.motionLayoutRoot.isStateViewMini() && checkInterval() ->
                            binding.motionLayoutRoot.setTransitionToClose()
                        binding.motionLayoutRoot.isStateViewFull() && checkInterval() ->
                            binding.motionLayoutRoot.setTransitionToView(MotionConst.Param.VIEW_MINI)
                    }
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(
                requireActivity(),
                backPressedCallback
            )
        }

        binding.playerContainer.exoPlayerContainer.controller.setOnGesturesListener(
            object: GestureController.OnGestureListener {
                override fun onDown(event: MotionEvent) {
                    binding.motionLayoutRoot.onTouchEvent(event)
                    binding.playerContainer.playerRoot.onTouchEvent(event)
                }
                override fun onUpOrCancel(event: MotionEvent) {
                    binding.motionLayoutRoot.onTouchEvent(event)
                    binding.playerContainer.playerRoot.onTouchEvent(event)
                }
                override fun onSingleTapUp(event: MotionEvent) = false
                override fun onSingleTapConfirmed(event: MotionEvent) = false
                override fun onLongPress(event: MotionEvent) {}
                override fun onDoubleTap(event: MotionEvent) = false
            }
        )
        // 상단 statusbar의 화면 회전 이벤트를 감지하는 observer
        // 화면 회전 잠금이면 orientationListener의 기능을 중지한다.
        contentObserver = object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                setupOrientationEventListener()
            }
        }
        val settingUri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
        requireActivity().contentResolver.registerContentObserver(
            settingUri,
            false,
            contentObserver
        )

        // YouTube 방식의 회전을 적용하기 위한 event Listener
        // * YouTube는 디바이스가 세로인 상태에서 영상 오버레이 메뉴의 full screen 버튼을 누르면
        // 디바이스는 세로인 상태에서 가로 UI가 보여진다.
        // * 이 기능이 없으면 디바이스가 세로인 상태에서 영상 오버레이 메뉴의 full screen 버튼을 누르면
        // 가로 UI가 잠깐 보였다 다시 세로 UI로 돌아온다.
        orientationListener = object: OrientationEventListener(requireActivity()) {
            override fun onOrientationChanged(orientation: Int) {
                val newOrientation = when (orientation) {
                    in 0 .. 44 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    in 45 .. 134 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    in 135 .. 224 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    in 225 .. 314 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    in 315 .. 359 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ORIENTATION_UNKNOWN
                }
                if (checkInterval() &&
                    newOrientation != previousOrientation
                ) {
                    requireActivity().requestedOrientation = newOrientation
                    previousOrientation = newOrientation
                }
            }
        }
        setupOrientationEventListener()

        binding.motionLayoutRoot.addTransitionListener(object: MotionLayout.TransitionListener{
            override fun onTransitionStarted(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int
            ) {
                cancelHideMenu()
            }

            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {
                when {
                    binding.motionLayoutRoot.isViewTransition() ->
                        parentViewModel.setBottomMenuMotion(1 - progress)
                }
            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                setTransitionCompleted()
            }

            override fun onTransitionTrigger(
                motionLayout: MotionLayout?,
                triggerId: Int,
                positive: Boolean,
                progress: Float
            ) {}
        })

        binding.playerContainer.exoPlayerView.doOnLayout {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                CommonUtils.isSupportPIP(requireContext())) {
                val sourceRectHint = Rect()
                it.getGlobalVisibleRect(sourceRectHint)

                requireActivity().setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(
                            when(isHorizontalView) {
                                true -> Rational(16, 9)
                                false -> Rational(9, 16)
                            }
                        )
                        .setSourceRectHint(sourceRectHint)
                        .build()
                )
            }
        }

        binding.exoPlayerProgress.setOnSeekBarChangeListener(
            object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {}

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isTracking = true
                    cancelPlayerProgress()
                    when(binding.motionLayoutRoot.isStateMenuShow()) {
                        // 오버레이 메뉴가 visible 상태이면 오버레이 메뉴를 hide하는 job을 cancel
                        // Tracking 중일때 메뉴가 사라지는 것을 방지
                        true -> cancelHideMenu()
                        // 오버레이 메뉴가 gone 상태이면 오버레이 메뉴를 visible
                        false -> binding.motionLayoutRoot.setTransitionToMenu(
                            MotionConst.Param.MENU_SHOW
                        )
                    }

                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isTracking = false
                    // Tracking이 완료되면 오버레이 메뉴를 hide하는 job 재동작
                    startHideMenu()

                    seekBar?.let {
                        player?.seekTo(it.progress.toLong() * 1000)
                    }
                }
            }
        )

        binding.dragHandler.setOnTouchListener(this)

        binding.playerMiniContainer.btnMiniPlay.setOnClickListener(this)
        binding.playerMiniContainer.btnClose.setOnClickListener(this)

        binding.playerMenuContainer.btnPlay.setOnClickListener(this)
        binding.playerMenuContainer.btnRewind.setOnClickListener(this)
        binding.playerMenuContainer.btnFastForward.setOnClickListener(this)
        binding.playerMenuContainer.btnMiniPlayer.setOnClickListener(this)
        binding.playerMenuContainer.btnBottomVisibility.setOnClickListener(this)
        binding.playerMenuContainer.btnChatPosition.setOnClickListener(this)
        binding.playerMenuContainer.btnFullScreen.setOnClickListener(this)
        binding.playerMenuContainer.btnThumbsUp.setOnClickListener(this)
        binding.playerMenuContainer.btnReport.setOnClickListener(this)
        binding.playerMenuContainer.btnBlock.setOnClickListener(this)
        binding.informationContainer.btnThumbsUp.setOnClickListener(this)
        binding.informationContainer.btnReport.setOnClickListener(this)
        binding.informationContainer.btnBlock.setOnClickListener(this)

    }

    override fun registerFlow() {
        super.registerFlow()

        lifecycleScope.repeatLaunchWhenCreated {
            parentViewModel.enterPictureInPicture.collect {
                enterPictureInPictureMode()
            }
        }
    }

    override fun onClick(view: View) {
        when(view) {
            binding.playerMenuContainer.btnPlay,
            binding.playerMiniContainer.btnMiniPlay -> {
                player?.let {
                    cancelHideMenu()
                    startHideMenu()

                    when(it.isPlaying) {
                        true -> pausePlayer()
                        false -> resumePlayer()
                    }
                }
            }
            binding.playerMenuContainer.btnRewind -> {
                cancelHideMenu()
                startHideMenu()
                player?.seekBack()
            }
            binding.playerMenuContainer.btnFastForward -> {
                cancelHideMenu()
                startHideMenu()
                player?.seekForward()
            }
            binding.playerMenuContainer.btnMiniPlayer -> {
                if(checkInterval())
                    binding.motionLayoutRoot.setTransitionToView(MotionConst.Param.VIEW_MINI)
            }
            binding.playerMenuContainer.btnBottomVisibility -> {
                if(checkInterval()) {
                    when {
                        binding.motionLayoutRoot.isStateBottomShow() -> {
                            binding.motionLayoutRoot.setTransitionToBottomVisible(
                                MotionConst.Param.BOTTOM_HIDE
                            )
                        }
                        binding.motionLayoutRoot.isStateBottomHide() ->
                            binding.motionLayoutRoot.setTransitionToBottomVisible(
                                MotionConst.Param.BOTTOM_SHOW
                            )
                    }
                    setOverlayMenuTransition()
                }
            }
            binding.playerMenuContainer.btnChatPosition -> {
                if(checkInterval()) {
                    if(binding.motionLayoutRoot.isStateBottomHide()) {
                        binding.motionLayoutRoot.setTransitionToBottomVisible(
                            MotionConst.Param.BOTTOM_SHOW
                        )
                    }
                    when {
                        binding.motionLayoutRoot.isStateBottomAttach() ->
                            binding.motionLayoutRoot.setTransitionToBottomPosition(
                                MotionConst.Param.BOTTOM_OVERLAY
                            )
                        binding.motionLayoutRoot.isStateBottomOverlay() ->
                            binding.motionLayoutRoot.setTransitionToBottomPosition(
                                MotionConst.Param.BOTTOM_ATTACH
                            )
                    }
                    setOverlayMenuTransition()
                }
            }
            binding.playerMenuContainer.btnFullScreen -> {
                if(checkInterval()) {
                    when (binding.motionLayoutRoot.isStateOrientationPort()) {
                        true -> requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        false ->
                            @SuppressLint("SourceLockedOrientationActivity")
                            requireActivity().requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
            binding.playerMiniContainer.btnClose -> binding.motionLayoutRoot.setTransitionToClose()
            binding.playerMenuContainer.btnThumbsUp,
            binding.informationContainer.btnThumbsUp -> {}
            binding.playerMenuContainer.btnReport,
            binding.informationContainer.btnReport -> {}
            binding.playerMenuContainer.btnBlock,
            binding.informationContainer.btnBlock -> {}
        }
    }

    private var changeChatHeight = -1f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if(v == binding.dragHandler) {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if(binding.motionLayoutRoot.isStateMenuShow()) cancelHideMenu()

                    event.actionIndex.also { pointerIndex ->
                        mLastTouchY = event.getY(pointerIndex)
                    }
                    mActivePointerId = event.getPointerId(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    val y: Float = event.findPointerIndex(mActivePointerId).let { pointerIndex ->
                        event.getY(pointerIndex)
                    }
                    mPosY = y - mLastTouchY

                    val maxHeightRatio = WatchConst.Param.BOTTOM_MAX_HEIGHT_RATIO
                    val maxHeight = CommonUtils.getDeviceHeight(requireActivity()) * maxHeightRatio

                    val minHeightRatio = WatchConst.Param.BOTTOM_MIN_HEIGHT_RATIO
                    val minHeight = CommonUtils.getDeviceHeight(requireActivity()) * minHeightRatio

                    val chatHeight = binding.bottomContainer.height
                    changeChatHeight = (chatHeight - mPosY).run {
                        when {
                            this > maxHeight -> maxHeight
                            this < minHeight -> minHeight
                            else -> this
                        }
                    }

                    setBottomHeight(changeChatHeight)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if(binding.motionLayoutRoot.isStateMenuShow()) startHideMenu()

                    mActivePointerId = MotionEvent.INVALID_POINTER_ID
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    event.actionIndex.also { pointerIndex ->
                        event.getPointerId(pointerIndex)
                            .takeIf { it == mActivePointerId }
                            ?.run {
                                val newPointerIndex = if (pointerIndex == 0) 1 else 0
                                mLastTouchY = event.getY(newPointerIndex)
                                mActivePointerId = event.getPointerId(newPointerIndex)
                            }
                    }
                }
            }
            return true
        }
        return false
    }

    private fun enterPictureInPictureMode(){
        if(CommonUtils.isSupportPIP(requireContext())) { // PIP 모드를 지원하는 경우
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    requireActivity().enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                Build.VERSION.SDK_INT > Build.VERSION_CODES.N ->
                    @Suppress("DEPRECATION") requireActivity().enterPictureInPictureMode()
            }
        }
    }

    // 라이브 방송 시청은 player를 release한 후에 다시 initialize
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        if(player == null) {
            player = ExoPlayer.Builder(requireActivity())
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build().also {
                    binding.playerContainer.exoPlayerView.player = it

                    it.addAnalyticsListener(EventLogger())
                    it.addListener(object : Player.Listener {
                        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                            super.onTimelineChanged(timeline, reason)
                            Timber.d("ExoPlayer Test onTimelineChanged timeline : $timeline, reason : $reason")
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            super.onMediaItemTransition(mediaItem, reason)
                            Timber.d("ExoPlayer Test onMediaItemTransition mediaItem : $mediaItem, reason : $reason")
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            super.onTracksChanged(tracks)
                            Timber.d("ExoPlayer Test onTracksChanged tracks : $tracks")
                        }

                        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                            super.onMediaMetadataChanged(mediaMetadata)
                            Timber.d("ExoPlayer Test onMediaMetadataChanged mediaMetadata : $mediaMetadata")
                        }

                        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
                            super.onPlaylistMetadataChanged(mediaMetadata)
                            Timber.d("ExoPlayer Test onPlaylistMetadataChanged mediaMetadata : $mediaMetadata")
                        }

                        override fun onIsLoadingChanged(isLoading: Boolean) {
                            super.onIsLoadingChanged(isLoading)
                            Timber.d("ExoPlayer Test onIsLoadingChanged isLoading : $isLoading")
                        }

                        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                            super.onAvailableCommandsChanged(availableCommands)
                            Timber.d("ExoPlayer Test onAvailableCommandsChanged availableCommands : $availableCommands")
                        }

                        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                            super.onTrackSelectionParametersChanged(parameters)
                            Timber.d("ExoPlayer Test onTrackSelectionParametersChanged parameters : $parameters")
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            super.onPlayWhenReadyChanged(playWhenReady, reason)
                            Timber.d("ExoPlayer Test onPlayWhenReadyChanged playWhenReady : $playWhenReady, reason : $reason")
                        }

                        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                            super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
                            Timber.d("ExoPlayer Test onPlaybackSuppressionReasonChanged playbackSuppressionReason : $playbackSuppressionReason")
                        }

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            super.onRepeatModeChanged(repeatMode)
                            Timber.d("ExoPlayer Test onRepeatModeChanged repeatMode : $repeatMode")
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                            Timber.d("ExoPlayer Test onShuffleModeEnabledChanged shuffleModeEnabled : $shuffleModeEnabled")
                        }

                        override fun onPlayerErrorChanged(error: PlaybackException?) {
                            super.onPlayerErrorChanged(error)
                            Timber.d("ExoPlayer Test onPlayerErrorChanged error : $error")
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                            Timber.d("ExoPlayer Test onPositionDiscontinuity oldPosition : $oldPosition, newPosition : $newPosition, reason : $reason")
                        }

                        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
                            super.onSeekBackIncrementChanged(seekBackIncrementMs)
                            Timber.d("ExoPlayer Test onSeekBackIncrementChanged seekBackIncrementMs : $seekBackIncrementMs")
                        }

                        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
                            super.onSeekForwardIncrementChanged(seekForwardIncrementMs)
                            Timber.d("ExoPlayer Test onSeekForwardIncrementChanged seekForwardIncrementMs : $seekForwardIncrementMs")
                        }

                        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) {
                            super.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
                            Timber.d("ExoPlayer Test onMaxSeekToPreviousPositionChanged maxSeekToPreviousPositionMs : $maxSeekToPreviousPositionMs")
                        }

                        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
                            super.onAudioAttributesChanged(audioAttributes)
                            Timber.d("ExoPlayer Test onAudioAttributesChanged audioAttributes : $audioAttributes")
                        }

                        override fun onVolumeChanged(volume: Float) {
                            super.onVolumeChanged(volume)
                            Timber.d("ExoPlayer Test onVolumeChanged volume : $volume")
                        }

                        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) {
                            super.onSkipSilenceEnabledChanged(skipSilenceEnabled)
                            Timber.d("ExoPlayer Test onSkipSilenceEnabledChanged skipSilenceEnabled : $skipSilenceEnabled")
                        }

                        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                            super.onDeviceInfoChanged(deviceInfo)
                            Timber.d("ExoPlayer Test onDeviceInfoChanged deviceInfo : $deviceInfo")
                        }

                        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                            super.onDeviceVolumeChanged(volume, muted)
                            Timber.d("ExoPlayer Test onDeviceVolumeChanged volume : $volume, muted : $muted")
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            super.onVideoSizeChanged(videoSize)
                            Timber.d("ExoPlayer Test onVideoSizeChanged videoSize : $videoSize")
                        }

                        override fun onSurfaceSizeChanged(width: Int, height: Int) {
                            super.onSurfaceSizeChanged(width, height)
                            Timber.d("ExoPlayer Test onSurfaceSizeChanged width : $width, height : $height")
                        }

                        override fun onRenderedFirstFrame() {
                            super.onRenderedFirstFrame()
                            Timber.d("ExoPlayer Test onRenderedFirstFrame")
                        }

                        override fun onCues(cueGroup: CueGroup) {
                            super.onCues(cueGroup)
                            Timber.d("ExoPlayer Test onCues cueGroup : $cueGroup")
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            super.onPlayerError(error)
                            Timber.d("ExoPlayer Test onPlayerError error : $error")

                            error.printStackTrace()
                            when (error.errorCode) {
                                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                                    it.playWhenReady = playWhenReady
                                    it.seekToDefaultPosition()
                                    it.prepare()
                                }
                                // 네트워크를 끊고 연결하는 과정을 반복하면 발생하는 이슈
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                                    it.playWhenReady = playWhenReady
                                    it.seekTo(binding.exoPlayerProgress.progress.toLong() * 1000)
                                    it.prepare()
                                }
                                else -> {}
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            super.onPlaybackStateChanged(playbackState)
                            Timber.d("ExoPlayer Test onPlaybackStateChanged playbackState : $playbackState")

                            when(playbackState) {
                                Player.STATE_READY -> {
                                    binding.exoPlayerProgress.max = it.duration.toInt() / 1000
                                    binding.exoPlayerProgress.progress = it.currentPosition.toInt() / 1000
                                    setPlayTimeText()
                                }
                                Player.STATE_ENDED -> {
                                    binding.exoPlayerProgress.progress = binding.exoPlayerProgress.max
                                    setPlayTimeText()

                                    if(!requireActivity().isInPictureInPictureMode) {
                                        cancelHideMenu()
                                        binding.motionLayoutRoot.setTransitionToMenu(
                                            MotionConst.Param.MENU_SHOW
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }

                        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                            super.onPlaybackParametersChanged(playbackParameters)
                            Timber.d("ExoPlayer Test onPlaybackParametersChanged playbackParameters : $playbackParameters")
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            super.onIsPlayingChanged(isPlaying)
                            Timber.d("ExoPlayer Test onIsPlayingChanged isPlaying : $isPlaying")
                            when(isPlaying) {
                                true -> startPlayerProgress()
                                false -> cancelPlayerProgress()
                            }
                        }
                    })
                }
        }
    }

    // 라이브 방송 시청은 player를 release한 후에 다시 initialize
    private fun releasePlayer() {
        player?.let{
            playbackPosition = it.currentPosition
            currentWindow = it.currentMediaItemIndex
            playWhenReady = it.playWhenReady
            it.release()
            player = null
        }
    }

    private fun playVideo() {
        player?.let {
            val mediaItem = MediaItem.fromUri("https://storage.googleapis.com/exoplayer-test-media-0/play.mp3")

            it.setMediaItem(mediaItem)

            it.playWhenReady = playWhenReady
            it.seekToDefaultPosition()
            it.prepare()
        }
    }

    // VOD, 다시보기 방송 시청은 player를 pasue한 후에 다시 play
    private fun resumePlayer() {
        player?.let {
            if(it.playbackState == Player.STATE_ENDED) it.seekTo(0)

            it.play()
            binding.playerMenuContainer.btnPlay.setImageResource(R.drawable.play_ic)
            binding.playerMiniContainer.btnMiniPlay.setImageResource(R.drawable.play_ic_black)
            binding.exoPlayerProgress.progress = it.currentPosition.toInt() / 1000
            setPlayTimeText()
        }
    }
    // VOD, 다시보기 방송 시청은 player를 pasue한 후에 다시 play
    private fun pausePlayer() {
        player?.let {
            it.pause()
            binding.playerMenuContainer.btnPlay.setImageResource(R.drawable.pause_ic)
            binding.playerMiniContainer.btnMiniPlay.setImageResource(R.drawable.pause_ic_black)
            binding.exoPlayerProgress.progress = it.currentPosition.toInt() / 1000
            setPlayTimeText()
        }
    }

    private fun setTransitionCompleted() {
        // 오버레이 메뉴 UI 상태 변경
        setOverlayMenu()
        // 하단 영역 UI 상태 변경
        setBottomLayout()
        // System UI 상태 변경
        setSystemUI()

        // 선물 화면 recyclerView width 갱신, 채팅 화면 scroll 최하단 이동

        startHideMenu()

        when {
            binding.motionLayoutRoot.isStateViewFull() -> {
                parentViewModel.setBottomMenuMotion(1f)
            }
            binding.motionLayoutRoot.isStateViewMini() -> {
                parentViewModel.setBottomMenuMotion(0f)
            }
        }

        // 하위 View 상태 변경(오버레이 메뉴 show/hide는 예외 처리)
        if(binding.motionLayoutRoot.isViewTransition() ||
            binding.motionLayoutRoot.isOrientationTransition()) initializeZoom()

        if(binding.motionLayoutRoot.isOrientationTransition()) setOverlayMenuTransition()

        // 화면 종료 처리
        if(binding.motionLayoutRoot.isStateViewClose()) finishFragment()
    }

    private fun initializeZoom() {
        binding.playerContainer.exoPlayerContainer.controller.apply {
            this.state.set(0f, 0f, 1f, state.rotation)
            updateState()
        }
    }

    private fun setOverlayMenu() {
        binding.playerMenuContainer.btnBottomVisibility.setImageResource(
            when (binding.motionLayoutRoot.isStateBottomShow()) {
                true -> R.drawable.chat_on
                false -> R.drawable.chat_off
            }
        )

        binding.playerMenuContainer.btnChatPosition.setImageResource(
            when{
                binding.motionLayoutRoot.isStateOrientationLand() &&
                        binding.motionLayoutRoot.isStateBottomOverlay() -> R.drawable.chat_right
                else -> R.drawable.chat_bottom
            }
        )

        binding.playerMenuContainer.btnChatPosition.isInvisible =
            when {
                binding.motionLayoutRoot.isStateOrientationPort() &&
                        binding.motionLayoutRoot.isStatePlayerBase() -> true
                else -> false
            }
    }

    private fun setOverlayMenuTransition() {
        when (binding.motionLayoutRoot.isStateOrientationPort()) {
            true -> {
                when {
                    // button : base, information : show
                    binding.motionLayoutRoot.isStatePlayerBase().not() &&
                            binding.motionLayoutRoot.isStateBottomAttach() &&
                            binding.motionLayoutRoot.isStateBottomHide() -> {
                        binding.playerMenuContainer.clInfoTop.isInvisible = false
                        binding.playerMenuContainer.clInfoBottom.isInvisible = false
                    }
                    // button : overlay, information : show
                    binding.motionLayoutRoot.isStatePlayerBase().not() &&
                            binding.motionLayoutRoot.isStateBottomOverlay() -> {
                        binding.playerMenuContainer.clInfoTop.isInvisible = false
                        binding.playerMenuContainer.clInfoBottom.isInvisible = false
                    }
                    // button : base, information : hide
                    else -> {
                        binding.playerMenuContainer.clInfoTop.isInvisible = true
                        binding.playerMenuContainer.clInfoBottom.isInvisible = true
                    }
                }
            }
            false -> {
                when {
                    // button : base, information : show
                    binding.motionLayoutRoot.isStateBottomAttach() &&
                            binding.motionLayoutRoot.isStateBottomHide() -> {
                        binding.playerMenuContainer.clInfoTop.isInvisible = false
                        binding.playerMenuContainer.clInfoBottom.isInvisible = false
                    }
                    // button : overlay, information : show
                    binding.motionLayoutRoot.isStateBottomOverlay() -> {
                        binding.playerMenuContainer.clInfoTop.isInvisible = false
                        binding.playerMenuContainer.clInfoBottom.isInvisible = false
                    }
                    // button : base, information : hide
                    else -> {
                        binding.playerMenuContainer.clInfoTop.isInvisible = true
                        binding.playerMenuContainer.clInfoBottom.isInvisible = true
                    }
                }
            }
        }
    }

    private fun setBottomLayout() {
        setBottomHeight()
    }

    private fun setBottomHeight(height: Float = -1f) {
        if(binding.motionLayoutRoot.isStateBottomOverlay()) {
            when (binding.motionLayoutRoot.isStateOrientationPort()) {
                true -> setBottomPortHeight(height)
                false -> setBottomLandHeight(height)
            }
        }
    }

    private fun setBottomPortHeight(chatHeight: Float = -1f) {
        val height = when(chatHeight) {
            -1f -> {
                val maxHeightRatio = WatchConst.Param.BOTTOM_MAX_HEIGHT_RATIO
                CommonUtils.getDeviceHeight(requireActivity()) * maxHeightRatio
            }
            else -> chatHeight
        }
        binding.motionLayoutRoot.setBottomPortHeight(height.toInt())
    }

    private fun setBottomLandHeight(chatHeight: Float = -1f) {
        val height = when(chatHeight) {
            -1f -> {
                val maxHeightRatio = WatchConst.Param.BOTTOM_MAX_HEIGHT_RATIO
                CommonUtils.getDeviceHeight(requireActivity()) * maxHeightRatio
            }
            else -> chatHeight
        }
        binding.motionLayoutRoot.setBottomLandHeight(height.toInt())
    }

    private fun startHideMenu() {
        if(binding.motionLayoutRoot.isStateMenuShow()) {
            if(binding.exoPlayerProgress.progress != binding.exoPlayerProgress.max &&
                !isTracking
            ) {
                watchHandler.sendEmptyMessageDelayed(
                    WatchConst.Param.HANDLER_HIDE_MENU,
                    WatchConst.Param.HIDE_MENU_DURATION
                )
            }
        }
    }
    private fun cancelHideMenu() {
        watchHandler.removeMessages(WatchConst.Param.HANDLER_HIDE_MENU)
    }

    private fun updatePlayerProgress() {
        player?.let {
            binding.exoPlayerProgress.progress += WatchConst.Param.PLAYER_SEEK_DURATION.toInt() / 1000
            setPlayTimeText()
            startPlayerProgress()
        }
    }

    private fun startPlayerProgress() {
        watchHandler.sendEmptyMessageDelayed(
            WatchConst.Param.HANDLER_PLAYER_SEEK,
            WatchConst.Param.PLAYER_SEEK_DURATION
        )
    }

    private fun cancelPlayerProgress() {
        watchHandler.removeMessages(WatchConst.Param.HANDLER_PLAYER_SEEK)
    }

    private fun setPlayTimeText() {
        val totalTime = binding.exoPlayerProgress.max.toLong()
        val totalMinute = totalTime % (60 * 60) / 60
        val totalSecond = totalTime % (60 * 60) % 60
        val formatTotalTime = getString(R.string.watch_play_time, totalMinute, totalSecond)

        val playTime = binding.exoPlayerProgress.progress.toLong()
        val playMinute = playTime % (60 * 60) / 60
        val playSecond = playTime % (60 * 60) % 60
        val formatPlayTime = getString(R.string.watch_play_time, playMinute, playSecond)

        binding.playerMenuContainer.tvPlayTime.text = getString(
            R.string.watch_play_time_all,
            formatPlayTime,
            formatTotalTime
        )
        binding.playerMenuContainer.mcvPlayTime.requestLayout()
    }

    private fun setupOrientationEventListener() {
        rotationEnable = Settings.System.getInt(
            requireActivity().contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
        setEnableOrientation(rotationEnable)
    }

    private fun setEnableOrientation(rotationEnable: Boolean) {
        when(rotationEnable) {
            true -> orientationListener.enable()
            false -> orientationListener.disable()
        }
    }

    private fun setLayout() {
        binding.motionLayoutRoot.setTransitionToOrientation(
            when(resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> MotionConst.Param.ORIENTATION_PORT
                else -> MotionConst.Param.ORIENTATION_LAND
            }
        )
        setSystemUI()
    }

    private fun setSystemUI() {
        when(binding.motionLayoutRoot.isStateViewFull()) {
            true -> when(resources.configuration.orientation) {
                // 디바이스가 세로 모드
                Configuration.ORIENTATION_PORTRAIT -> CommonUtils.showSystemUI(requireActivity())
                // 디바이스가 가로 모드
                else -> CommonUtils.hideSystemUI(requireActivity())
            }
            false -> CommonUtils.showSystemUI(requireActivity())
        }
    }


    private fun checkInterval() = binding.motionLayoutRoot.isTransitioning().not() &&
            binding.playerContainer.playerRoot.isTransitioning().not()


    fun finishFragment() {
        parentViewModel.setBottomMenuMotion(0f)
        CommonUtils.removeFragment(parentFragmentManager, PAGE_CODE)
    }
}