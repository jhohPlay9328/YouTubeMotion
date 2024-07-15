package com.jh.oh.play.youtube.pip

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseActivity<DB: ViewDataBinding, VM : BaseViewModel>(
    @LayoutRes val layoutResId: Int
): AppCompatActivity() {
    abstract val viewModel: VM
    private lateinit var _binding: DB
    protected val binding get() = _binding
    abstract val pageCode: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        }else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        if(layoutResId > 0) {
            _binding = DataBindingUtil.setContentView<DB>(this, layoutResId).apply {
                lifecycleOwner = this@BaseActivity
            }
        }

        loadView()
        registerListener()
        registerFlow()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    protected open fun loadView() {}

    protected open fun registerListener() {}

    protected open fun registerFlow(){}

    fun LifecycleCoroutineScope.repeatLaunchWhenCreated(
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                block()
            }
        }
    }
}