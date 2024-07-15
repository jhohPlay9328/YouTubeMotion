package com.jh.oh.play.youtube.pip.ui.main

import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.jh.oh.play.youtube.pip.BaseActivity
import com.jh.oh.play.youtube.pip.R
import com.jh.oh.play.youtube.pip.databinding.ActivityMainBinding
import com.jh.oh.play.youtube.pip.ui.watch.WatchConst
import com.jh.oh.play.youtube.pip.ui.watch.WatchFragment
import com.jh.oh.play.youtube.pip.utils.CommonUtils

class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>(
    R.layout.activity_main
) {
    override val viewModel: MainViewModel by viewModels()
    override val pageCode = 100

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        viewModel.setEnterPictureInPicture()
    }

    override fun registerListener() {
        super.registerListener()

        binding.btn169.setOnClickListener {
            moveWatchFragment(true)
        }

        binding.btn916.setOnClickListener {
            moveWatchFragment(false)
        }
    }

    override fun registerFlow() {
        super.registerFlow()

        lifecycleScope.repeatLaunchWhenCreated {
            // 방송 화면이 소형화 되는 과정에 하단 메뉴가 보이게 하는 기능
            viewModel.setBottomMenuMotion.collect {
                binding.motionLayoutRoot.progress = it
            }
        }
    }

    private fun moveWatchFragment(isHorizontal: Boolean = true) {
        if(CommonUtils.hasFragment(
                this@MainActivity,
                WatchFragment.PAGE_CODE
        )) {
            CommonUtils.removeFragment(
                supportFragmentManager,
                WatchFragment.PAGE_CODE
            )
        }

        CommonUtils.addFragment(
            parent = this@MainActivity,
            callFragment = WatchFragment(),
            pageCode = WatchFragment.PAGE_CODE,
            bundle = bundleOf(
                WatchConst.Bundle.IS_HORIZONTAL to isHorizontal,
            ),
            enterAnimation = true,
            addToBackStack = false
        )
    }
}