package com.jh.oh.play.youtube.pip.ui.watch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.constraintlayout.motion.widget.MotionLayout
import com.jh.oh.play.youtube.pip.R

class PlayerRotationMotionLayout (
    context: Context,
    attributeSet: AttributeSet? = null
) : MotionLayout(context, attributeSet) {
    private val touchView by lazy {
        findViewById<FrameLayout>(R.id.exo_player_container)
    }

    private val touchRect = Rect()
    private var isTouchRect = false

    private var mActivePointerId = MotionEvent.INVALID_POINTER_ID

    private var mLastTouchY = 0f
    private var mPosY = 0f

    private var isTouchUp = false
    private var isTransitioning = false

    init {
        super.addTransitionListener(object : TransitionListener {
            override fun onTransitionStarted(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int
            ) {
                isTransitioning = true
            }

            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {}

            override fun onTransitionTrigger(
                motionLayout: MotionLayout?,
                triggerId: Int,
                positive: Boolean,
                progress: Float
            ) {}

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                isTransitioning = false

                checkRotation()
            }
        })
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                setOrientation()

                event.actionIndex.also { pointerIndex ->
                    mLastTouchY = event.getY(pointerIndex)

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
                    mPosY = y - mLastTouchY

                    when (context.resources.configuration.orientation) {
                        Configuration.ORIENTATION_PORTRAIT -> {
                            if (mPosY < -10) {
                                return onTouchEvent(event)
                            }
                        }
                        else -> {
                            if (mPosY > 10) {
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
                            mLastTouchY = event.getY(newPointerIndex)
                            mActivePointerId = event.getPointerId(newPointerIndex)
                        }
                }
            }
        }

        return super.onInterceptTouchEvent(event) && isTouchRect
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouchUp = true

                checkRotation()
                super.onTouchEvent(event)
            }
            else -> {
                isTouchUp = false

                super.onTouchEvent(event)
            }
        }
    }

    private fun setOrientation() {
        when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                setTransition(R.id.player_base_to_player_port_rotation)
                getTransition(R.id.player_base_to_player_port_rotation).isEnabled = true
                getTransition(R.id.player_base_to_player_land_rotation).isEnabled = false
            }
            else -> {
                setTransition(R.id.player_base_to_player_land_rotation)
                getTransition(R.id.player_base_to_player_port_rotation).isEnabled = false
                getTransition(R.id.player_base_to_player_land_rotation).isEnabled = true
            }
        }
    }

    fun checkRotation() {
        when {
            isTouchUp && isTransitioning.not() -> {
                when {
                    isStateRotationPort() ->
                        (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    isStateRotationLand() ->
                        @SuppressLint("SourceLockedOrientationActivity")
                        (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                transitionToState(R.id.player_base, 1)
            }
        }
    }

    private fun isStateBase() = when(currentState) {
        R.id.player_base -> true
        else -> false
    }

    private fun isStateRotationPort() = when(currentState) {
        R.id.player_port_rotation -> true
        else -> false
    }
    private fun isStateRotationLand() = when(currentState) {
        R.id.player_land_rotation -> true
        else -> false
    }

    fun isTransitioning() = isTransitioning
}