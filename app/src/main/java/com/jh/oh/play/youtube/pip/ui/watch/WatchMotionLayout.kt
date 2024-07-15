package com.jh.oh.play.youtube.pip.ui.watch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.motion.widget.KeyFrames
import androidx.constraintlayout.motion.widget.KeyPosition
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.OnSwipe
import com.jh.oh.play.youtube.pip.R
import timber.log.Timber
import kotlin.math.abs

class WatchMotionLayout (
    context: Context,
    attributeSet: AttributeSet? = null
) : MotionLayout(context, attributeSet) {
    private val touchView by lazy {
        findViewById<View>(R.id.player_touch_area)
    }
    private val touchRect = Rect()

    private var isTouchRect = false

    private var mActivePointerId = MotionEvent.INVALID_POINTER_ID

    private var mLastInterceptTouchY = 0f
    private var mInterceptPosY = 0f

    private var mLastTouchY = 0f
    private var mPosY = 0f

    private var isTransitioning = false

    private var orientation = MotionConst.Param.ORIENTATION_PORT
    private var view = MotionConst.Param.VIEW_FULL
    private var player = MotionConst.Param.PLAYER_BASE
    private var bottomPosition = MotionConst.Param.BOTTOM_ATTACH
    private var bottomVisible = MotionConst.Param.BOTTOM_SHOW
    private var menu = MotionConst.Param.MENU_HIDE

    init {
        super.addTransitionListener(object : TransitionListener {
            override fun onTransitionStarted(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int
            ) {
                Timber.d("Transition Test WatchMotionLayout onTransitionStarted startId $startId, endId $endId")
                isTransitioning = true
            }

            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {
                Timber.d("Transition Test WatchMotionLayout onTransitionChange startId $startId, endId $endId,  progress $progress")
            }

            override fun onTransitionTrigger(
                motionLayout: MotionLayout?,
                triggerId: Int,
                positive: Boolean,
                progress: Float
            ) {}

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                Timber.d("Transition Test WatchMotionLayout onTransitionCompleted currentId $currentId, progress $progress")
                isTransitioning = false
                isTouchRect = false
                setStateValue()

                createTransitionView()
            }
        })
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                event.actionIndex.also { pointerIndex ->
                    mLastInterceptTouchY = event.getY(pointerIndex)

                    // 최초 터치 이벤트가 플레이엉 영역일때만 intercept
                    touchView.getHitRect(touchRect)
                    isTouchRect = touchRect.contains(
                        event.getX(pointerIndex).toInt(),
                        event.getY(pointerIndex).toInt()
                    )
                }
                mActivePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if(isTouchRect) {
                    val y: Float = event.findPointerIndex(mActivePointerId).let { pointerIndex ->
                        event.getY(pointerIndex)
                    }
                    mInterceptPosY = y - mLastInterceptTouchY

                    // zoom 이벤트가 아니라 swipe 이벤트가 동작하도록 하는 기능
                    when {
                        isCheckSwipeUp() -> {
                            if(mInterceptPosY > 10) {
                                return onTouchEvent(event)
                            }
                        }
                        isCheckSwipeDown() -> {
                            if(mInterceptPosY < -10) {
                                return onTouchEvent(event)
                            }
                        }
                        else -> {
                            if(abs(mInterceptPosY) > 10) {
                                return onTouchEvent(event)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mActivePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                event.actionIndex.also { pointerIndex ->
                    event.getPointerId(pointerIndex)
                        .takeIf { it == mActivePointerId }
                        ?.run {
                            val newPointerIndex = if (pointerIndex == 0) 1 else 0
                            mLastInterceptTouchY = event.getY(newPointerIndex)
                            mActivePointerId = event.getPointerId(newPointerIndex)
                        }
                }
            }
        }
        return super.onInterceptTouchEvent(event) && isTouchRect
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if(abs(mPosY) < 10) {
                    when {
                        isStateViewMini() -> setTransitionToView(MotionConst.Param.VIEW_FULL)
                        isStateMenuShow() -> setTransitionToMenu(MotionConst.Param.MENU_HIDE)
                        isStateMenuHide() -> setTransitionToMenu(MotionConst.Param.MENU_SHOW)
                    }
                }
                return super.onSingleTapConfirmed(e)
            }

            override fun onDown(e: MotionEvent): Boolean {
                mPosY = 0f
                mLastTouchY = e.y
                return super.onDown(e)
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val y: Float = e.y
                mPosY = y - mLastTouchY
                return super.onSingleTapUp(e)
            }
        }
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouchRect = false


//                if(abs(mPosY) < 10) {
//                    when {
//                        isStateViewMini() -> setTransitionToView(MotionConst.Param.VIEW_FULL)
//                        isStateMenuShow() -> setTransitionToMenu(MotionConst.Param.MENU_HIDE)
//                        isStateMenuHide() -> setTransitionToMenu(MotionConst.Param.MENU_SHOW)
//                    }
//                    PopUtils.hideIme(activity)
//                }
            }
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchRect) {
                    touchView.getHitRect(touchRect)
                    isTouchRect = touchRect.contains(event.x.toInt(), event.y.toInt())
                }
            }
        }
        return super.onTouchEvent(event) && isTouchRect
    }

    fun initializeLayout(
        isHorizontalVideo: Boolean = true
    ) {
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_attach_show_hide: ${R.id.port_full_base_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_attach_show_show: ${R.id.port_full_base_attach_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_attach_hide_hide: ${R.id.port_full_base_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_attach_hide_show: ${R.id.port_full_base_attach_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_overlay_show_hide: ${R.id.port_full_base_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_overlay_show_show: ${R.id.port_full_base_overlay_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_overlay_hide_hide: ${R.id.port_full_base_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_base_overlay_hide_show: ${R.id.port_full_base_overlay_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_attach_show_hide: ${R.id.port_full_collapse_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_attach_show_show: ${R.id.port_full_collapse_attach_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_attach_hide_hide: ${R.id.port_full_collapse_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_attach_hide_show: ${R.id.port_full_collapse_attach_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_overlay_show_hide: ${R.id.port_full_collapse_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_overlay_show_show: ${R.id.port_full_collapse_overlay_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_overlay_hide_hide: ${R.id.port_full_collapse_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_collapse_overlay_hide_show: ${R.id.port_full_collapse_overlay_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_attach_show_hide: ${R.id.port_full_expand_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_attach_show_show: ${R.id.port_full_expand_attach_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_attach_hide_hide: ${R.id.port_full_expand_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_attach_hide_show: ${R.id.port_full_expand_attach_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_overlay_show_hide: ${R.id.port_full_expand_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_overlay_show_show: ${R.id.port_full_expand_overlay_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_overlay_hide_hide: ${R.id.port_full_expand_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_full_expand_overlay_hide_show: ${R.id.port_full_expand_overlay_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_base_attach_show_hide: ${R.id.port_mini_base_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_base_attach_hide_hide: ${R.id.port_mini_base_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_base_overlay_show_hide: ${R.id.port_mini_base_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_base_overlay_hide_hide: ${R.id.port_mini_base_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_collapse_attach_show_hide: ${R.id.port_mini_collapse_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_collapse_attach_hide_hide: ${R.id.port_mini_collapse_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_collapse_overlay_show_hide: ${R.id.port_mini_collapse_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_collapse_overlay_hide_hide: ${R.id.port_mini_collapse_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_expand_attach_show_hide: ${R.id.port_mini_expand_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_expand_attach_hide_hide: ${R.id.port_mini_expand_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_expand_overlay_show_hide: ${R.id.port_mini_expand_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.port_mini_expand_overlay_hide_hide: ${R.id.port_mini_expand_overlay_hide_hide}")

        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_attach_show_hide: ${R.id.land_full_base_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_attach_show_show: ${R.id.land_full_base_attach_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_attach_hide_hide: ${R.id.land_full_base_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_attach_hide_show: ${R.id.land_full_base_attach_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_overlay_show_hide: ${R.id.land_full_base_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_overlay_show_show: ${R.id.land_full_base_overlay_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_overlay_hide_hide: ${R.id.land_full_base_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_base_overlay_hide_show: ${R.id.land_full_base_overlay_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_attach_show_hide: ${R.id.land_full_collapse_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_attach_show_show: ${R.id.land_full_collapse_attach_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_attach_hide_hide: ${R.id.land_full_collapse_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_attach_hide_show: ${R.id.land_full_collapse_attach_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_overlay_show_hide: ${R.id.land_full_collapse_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_overlay_show_show: ${R.id.land_full_collapse_overlay_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_overlay_hide_hide: ${R.id.land_full_collapse_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_collapse_overlay_hide_show: ${R.id.land_full_collapse_overlay_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_attach_show_hide: ${R.id.land_full_expand_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_attach_show_show: ${R.id.land_full_expand_attach_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_attach_hide_hide: ${R.id.land_full_expand_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_attach_hide_show: ${R.id.land_full_expand_attach_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_overlay_show_hide: ${R.id.land_full_expand_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_overlay_show_show: ${R.id.land_full_expand_overlay_show_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_overlay_hide_hide: ${R.id.land_full_expand_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_full_expand_overlay_hide_show: ${R.id.land_full_expand_overlay_hide_show}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_base_attach_show_hide: ${R.id.land_mini_base_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_base_attach_hide_hide: ${R.id.land_mini_base_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_base_overlay_show_hide: ${R.id.land_mini_base_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_base_overlay_hide_hide: ${R.id.land_mini_base_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_collapse_attach_show_hide: ${R.id.land_mini_collapse_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_collapse_attach_hide_hide: ${R.id.land_mini_collapse_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_collapse_overlay_show_hide: ${R.id.land_mini_collapse_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_collapse_overlay_hide_hide: ${R.id.land_mini_collapse_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_expand_attach_show_hide: ${R.id.land_mini_expand_attach_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_expand_attach_hide_hide: ${R.id.land_mini_expand_attach_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_expand_overlay_show_hide: ${R.id.land_mini_expand_overlay_show_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.land_mini_expand_overlay_hide_hide: ${R.id.land_mini_expand_overlay_hide_hide}")
        Timber.d("Transition Test WatchMotionLayout setTransitionToState R.id.close: ${R.id.close}")

        player = when(isHorizontalVideo) {
            true -> MotionConst.Param.PLAYER_BASE
            false -> MotionConst.Param.PLAYER_EXPAND
        }

        createTransitionView()
    }

    @SuppressLint("DiscouragedApi")
    private fun getConstraintSetId(stateName: String) =
        resources.getIdentifier(stateName, "id", context.packageName)

    private fun getKeyFrameSet() = KeyFrames().apply {
        addKey(getKeyPosition(R.id.player_container))
        addKey(getKeyPosition(R.id.player_menu_container))
        addKey(getKeyPosition(R.id.player_mini_container))
        addKey(getKeyPosition(R.id.information_container))
        addKey(getKeyPosition(R.id.bottom_container))
        addKey(getKeyPosition(R.id.exo_player_progress))
    }

    private fun getKeyPosition(viewId: Int) = when(isStateOrientationPort()) {
        true -> getPortKeyPosition(viewId)
        false -> getLandKeyPosition(viewId)
    }

    private fun getPortKeyPosition(viewId: Int) = KeyPosition().apply {
        setViewId(viewId)
        setValue(KeyPosition.PERCENT_X, resources.getInteger(R.integer.motion_portrait_percent_x))
        setValue(KeyPosition.PERCENT_WIDTH, resources.getInteger(R.integer.motion_portrait_percent_width))
        framePosition = resources.getInteger(R.integer.motion_portrait_frame_position)
    }

    private fun getLandKeyPosition(viewId: Int) = KeyPosition().apply {
        setViewId(viewId)
        setValue(KeyPosition.PERCENT_Y, resources.getInteger(R.integer.motion_landscape_percent_y))
        setValue(KeyPosition.PERCENT_HEIGHT, resources.getInteger(R.integer.motion_landscape_percent_height))
        framePosition = resources.getInteger(R.integer.motion_landscape_frame_position)
    }

    fun setBottomPortHeight(height: Int) {
        setBottomHeight(R.id.port_full_collapse_overlay_show_hide, height)
        setBottomHeight(R.id.port_full_collapse_overlay_show_show, height)
        setBottomHeight(R.id.port_full_collapse_overlay_hide_hide, height)
        setBottomHeight(R.id.port_full_collapse_overlay_hide_show, height)
        setBottomHeight(R.id.port_full_expand_overlay_show_hide, height)
        setBottomHeight(R.id.port_full_expand_overlay_show_show, height)
        setBottomHeight(R.id.port_full_expand_overlay_hide_hide, height)
        setBottomHeight(R.id.port_full_expand_overlay_hide_show, height)
        requestLayout()
    }
    fun setBottomLandHeight(height: Int) {
        setBottomHeight(R.id.land_full_base_overlay_show_hide, height)
        setBottomHeight(R.id.land_full_base_overlay_show_show, height)
        setBottomHeight(R.id.land_full_base_overlay_hide_hide, height)
        setBottomHeight(R.id.land_full_base_overlay_hide_show, height)
        setBottomHeight(R.id.land_full_collapse_overlay_show_hide, height)
        setBottomHeight(R.id.land_full_collapse_overlay_show_show, height)
        setBottomHeight(R.id.land_full_collapse_overlay_hide_hide, height)
        setBottomHeight(R.id.land_full_collapse_overlay_hide_show, height)
        setBottomHeight(R.id.land_full_expand_overlay_show_hide, height)
        setBottomHeight(R.id.land_full_expand_overlay_show_show, height)
        setBottomHeight(R.id.land_full_expand_overlay_hide_hide, height)
        setBottomHeight(R.id.land_full_expand_overlay_hide_show, height)
        requestLayout()
    }

    private fun setBottomHeight(constraintSetId: Int, height: Int) {
        getConstraintSet(constraintSetId).constrainHeight(R.id.bottom_container, height)
    }

    private fun createTransitionView() {
        val startStateName = "${orientation}_${MotionConst.Param.VIEW_FULL}_${player}_${bottomPosition}_${bottomVisible}_${menu}"
        val endStateName = "${orientation}_${MotionConst.Param.VIEW_MINI}_${player}_${bottomPosition}_${bottomVisible}_${MotionConst.Param.MENU_HIDE}"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout createTransitionView transitionName : $transitionName, transitionId : $transitionId")
        getTransition(transitionId)?.let {
            if(isStateOrientationPort() && isStatePlayerCollapse() && isStateBottomAttach() ||
                    isStateOrientationLand()) {
                when {
                    isStateViewFull() -> it.setOnSwipe(null)
                    isStateViewMini() -> it.setOnSwipe(getOnSwipeDown())
                }
            }
            it.keyFrameList.clear()
            it.addKeyFrame(getKeyFrameSet())
        }
    }

    private fun getOnSwipeDown() = OnSwipe().apply {
        dragDirection = OnSwipe.DRAG_DOWN
        touchAnchorId = R.id.player_touch_area
        touchAnchorSide = OnSwipe.SIDE_MIDDLE
        onTouchUp = OnSwipe.ON_UP_AUTOCOMPLETE
        setMaxVelocity(context.resources.getInteger(R.integer.motion_decelerate_velocity))
        setMaxAcceleration(context.resources.getInteger(R.integer.motion_decelerate_acceleration))
    }

    fun setTransitionToOrientation(changeOrientationState: String) {
        val startStateName = "${MotionConst.Param.ORIENTATION_PORT}_${view}_${player}_${bottomPosition}_${bottomVisible}_${menu}"
        val endStateName = "${MotionConst.Param.ORIENTATION_LAND}_${view}_${player}_${bottomPosition}_${bottomVisible}_${menu}"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout setTransitionToOrientation " +
                "transitionName : $transitionName, " +
                "transitionId : $transitionId, " +
                "changeOrientationState : $changeOrientationState"
        )

        if(getTransition(transitionId) == null) return
        setTransition(transitionId)
        when(changeOrientationState) {
            MotionConst.Param.ORIENTATION_PORT -> transitionToStart()
            else -> transitionToEnd()
        }
        orientation = changeOrientationState
    }

    fun setTransitionToView(changeViewState: String) {
        val startStateName = "${orientation}_${MotionConst.Param.VIEW_FULL}_${player}_${bottomPosition}_${bottomVisible}_${menu}"
        val endStateName = "${orientation}_${MotionConst.Param.VIEW_MINI}_${player}_${bottomPosition}_${bottomVisible}_${MotionConst.Param.MENU_HIDE}"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout setTransitionToView " +
                "transitionName : $transitionName, " +
                "transitionId : $transitionId, " +
                "changeViewState : $changeViewState"
        )

        if(getTransition(transitionId) == null) return
        setTransition(transitionId)
        when(changeViewState) {
            MotionConst.Param.VIEW_FULL -> transitionToStart()
            else -> transitionToEnd()
        }
        view = changeViewState
    }

    fun setTransitionToBottomPosition(changeBottomPosition: String) {
        val startStateName = "${orientation}_${view}_${player}_${MotionConst.Param.BOTTOM_ATTACH}_${bottomVisible}_${menu}"
        val endStateName = "${orientation}_${view}_${player}_${MotionConst.Param.BOTTOM_OVERLAY}_${bottomVisible}_${menu}"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout setTransitionToBottomPosition " +
                "transitionName : $transitionName, " +
                "transitionId : $transitionId, " +
                "changeBottomPosition : $changeBottomPosition"
        )

        if(getTransition(transitionId) == null) return
        setTransition(transitionId)
        when(changeBottomPosition) {
            MotionConst.Param.BOTTOM_ATTACH -> transitionToStart()
            else -> transitionToEnd()
        }
        bottomPosition = changeBottomPosition
    }

    fun setTransitionToBottomVisible(changeBottomVisible: String) {
        val startStateName = "${orientation}_${view}_${player}_${bottomPosition}_${MotionConst.Param.BOTTOM_SHOW}_${menu}"
        val endStateName = "${orientation}_${view}_${player}_${bottomPosition}_${MotionConst.Param.BOTTOM_HIDE}_${menu}"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout setTransitionToBottomVisible " +
                "transitionName : $transitionName, " +
                "transitionId : $transitionId, " +
                "changeBottomVisible : $changeBottomVisible"
        )

        if(getTransition(transitionId) == null) return
        setTransition(transitionId)
        when(changeBottomVisible) {
            MotionConst.Param.BOTTOM_SHOW -> transitionToStart()
            else -> transitionToEnd()
        }
        bottomVisible = changeBottomVisible
    }

    fun setTransitionToMenu(changeMenuState: String) {
        val startStateName = "${orientation}_${view}_${player}_${bottomPosition}_${bottomVisible}_${MotionConst.Param.MENU_HIDE}"
        val endStateName = "${orientation}_${view}_${player}_${bottomPosition}_${bottomVisible}_${MotionConst.Param.MENU_SHOW}"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout setTransitionToMenu " +
                "transitionName : $transitionName, " +
                "transitionId : $transitionId, " +
                "changeMenuState : $changeMenuState"
        )

        if(getTransition(transitionId) == null) return
        setTransition(transitionId)
        when(changeMenuState) {
            MotionConst.Param.MENU_HIDE -> transitionToStart()
            else -> transitionToEnd()
        }
        menu = changeMenuState
    }

    fun setTransitionToClose() {
        val startStateName = "${orientation}_${MotionConst.Param.VIEW_MINI}_${player}_${bottomPosition}_${bottomVisible}_${MotionConst.Param.MENU_HIDE}"
        val endStateName = "close"
        val transitionName = "${startStateName}_to_${endStateName}"

        val transitionId = getConstraintSetId(transitionName)
        Timber.d("Transition Test WatchMotionLayout setTransitionToClose " +
                "transitionName : $transitionName, " +
                "transitionId : $transitionId"
        )

        if(getTransition(transitionId) == null) return
        setTransition(transitionId)
        transitionToEnd()

        view = MotionConst.Param.VIEW_CLOSE
    }

    // 세로 화면 상태인지 판단
    fun setStateValue() {
//        try {
            val currentName = resources.getResourceEntryName(currentState)
            Timber.d("Transition Test WatchMotionLayout setStateValue currentName : $currentName")
            val states = currentName.split("_")
            if (states.size > 1) {
                orientation = states[0]
                view = states[1]
                player = states[2]
                bottomPosition = states[3]
                bottomVisible = states[4]
                menu = states[5]
            } else {
                view = MotionConst.Param.VIEW_CLOSE
            }
//        } catch (e : Resources.NotFoundException){
//            e.printStackTrace()
//        }
    }

    fun isStateOrientationPort() = orientation == MotionConst.Param.ORIENTATION_PORT
    fun isStateOrientationLand() = orientation == MotionConst.Param.ORIENTATION_LAND

    fun isStateViewFull() = view == MotionConst.Param.VIEW_FULL
    fun isStateViewMini() = view == MotionConst.Param.VIEW_MINI
    fun isStateViewClose() = view == MotionConst.Param.VIEW_CLOSE

    fun isStatePlayerBase() = player == MotionConst.Param.PLAYER_BASE
    fun isStatePlayerExpand() = player == MotionConst.Param.PLAYER_EXPAND
    fun isStatePlayerCollapse() = player == MotionConst.Param.PLAYER_COLLAPSE

    fun isStateBottomAttach() = bottomPosition == MotionConst.Param.BOTTOM_ATTACH
    fun isStateBottomOverlay() = bottomPosition == MotionConst.Param.BOTTOM_OVERLAY

    fun isStateBottomShow() = bottomVisible == MotionConst.Param.BOTTOM_SHOW
    fun isStateBottomHide() = bottomVisible == MotionConst.Param.BOTTOM_HIDE

    fun isStateMenuShow() = menu == MotionConst.Param.MENU_SHOW
    fun isStateMenuHide() = menu == MotionConst.Param.MENU_HIDE

    fun isOrientationTransition(): Boolean {
        val startStateName = "${MotionConst.Param.ORIENTATION_PORT}_${view}_${player}_${bottomPosition}_${bottomVisible}_${menu}"
        val endStateName = "${MotionConst.Param.ORIENTATION_LAND}_${view}_${player}_${bottomPosition}_${bottomVisible}_${menu}"

        Timber.d("Transition Test WatchMotionLayout isOrientationTransition startState : $startState, endState : $endState / startStateId : ${getConstraintSetId(startStateName)}[$startStateName], endStateId : ${getConstraintSetId(endStateName)}[$endStateName]")

        return startState == getConstraintSetId(startStateName) &&
                endState == getConstraintSetId(endStateName)
    }

    // FullPlayer/내부 PIP로 변경되는 transition인지 판단
    fun isViewTransition(): Boolean {
        val startStateName = "${orientation}_${MotionConst.Param.VIEW_FULL}_${player}_${bottomPosition}_${bottomVisible}_${menu}"
        val endStateName = "${orientation}_${MotionConst.Param.VIEW_MINI}_${player}_${bottomPosition}_${bottomVisible}_${MotionConst.Param.MENU_HIDE}"

        Timber.d("Transition Test WatchMotionLayout isViewTransition startState : $startState, endState : $endState / startStateId : ${getConstraintSetId(startStateName)}[$startStateName], endStateId : ${getConstraintSetId(endStateName)}[$endStateName]")

        return startState == getConstraintSetId(startStateName) &&
                endState == getConstraintSetId(endStateName)
    }

    fun isOverlayTransition(): Boolean {
        val startStateName = "${orientation}_${view}_${player}_${MotionConst.Param.BOTTOM_ATTACH}_${bottomVisible}_${menu}"
        val endStateName = "${orientation}_${view}_${player}_${MotionConst.Param.BOTTOM_OVERLAY}_${bottomVisible}_${menu}"

        Timber.d("Transition Test WatchMotionLayout isOverlayTransition startState : $startState, endState : $endState / startStateId : ${getConstraintSetId(startStateName)}[$startStateName], endStateId : ${getConstraintSetId(endStateName)}[$endStateName]")

        return startState == getConstraintSetId(startStateName) &&
                endState == getConstraintSetId(endStateName)
    }

    private fun isCheckSwipeUp() = isStateOrientationPort() &&
            isStateViewFull() &&
            (isStatePlayerExpand() && isStateBottomAttach() && isStateBottomShow()).not()

    private fun isCheckSwipeDown() = isStateOrientationLand() && isStateViewFull()

    fun isTransitioning() = isTransitioning
}