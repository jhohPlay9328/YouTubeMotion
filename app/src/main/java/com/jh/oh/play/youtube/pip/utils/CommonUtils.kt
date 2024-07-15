package com.jh.oh.play.youtube.pip.utils

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.transition.Slide
import com.jh.oh.play.youtube.pip.R


object CommonUtils {
    fun getDeviceWidth(context: Context) = context.resources.displayMetrics.widthPixels

    fun getDeviceHeight(context: Context) = context.resources.displayMetrics.heightPixels

    fun showSystemUI(activity: Activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun hideSystemUI(activity: Activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {

            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun isSupportPIP(context: Context): Boolean {
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun addFragment(
        parent: FragmentActivity,
        callFragment: Fragment,
        bundle: Bundle = Bundle(),
        pageCode: Int,
        enterAnimation: Boolean = false,
        exitAnimation: Boolean = false,
        addToBackStack: Boolean = true,
    ){
        if(enterAnimation)
            callFragment.enterTransition = Slide(Gravity.BOTTOM).apply { duration = 300 }

        if(exitAnimation)
            callFragment.exitTransition = Slide(Gravity.BOTTOM).apply { duration = 300 }

        parent.supportFragmentManager.commit(allowStateLoss = true) {
            callFragment.arguments = bundle
            add(R.id.fl_fragment_container, callFragment, pageCode.toString())
            if(addToBackStack) addToBackStack(pageCode.toString())
        }
    }

    fun removeFragment(
        fragmentManager: FragmentManager,
        pageCode: Int = -1,
    ) {
        val fragment = fragmentManager.findFragmentByTag(pageCode.toString())
        fragment?.let {
            fragmentManager.commit(allowStateLoss = true) {
                remove(it)
            }
        }
    }

    fun hasFragment(
        parent: FragmentActivity,
        pageCode: Int,
    ) = getFragment(parent, pageCode) != null

    fun getFragment(
        parent: FragmentActivity,
        pageCode: Int
    ) = parent.supportFragmentManager.findFragmentByTag(pageCode.toString())
}