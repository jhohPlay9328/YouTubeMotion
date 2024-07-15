package com.jh.oh.play.youtube.pip

import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.jh.oh.play.youtube.pip.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


abstract class BaseFragment<DB: ViewDataBinding, VM: BaseViewModel>(
    @LayoutRes val layoutResId: Int,
): Fragment() {
    abstract val viewModel: VM
    abstract val parentViewModel: BaseViewModel
    protected val activityViewModel: MainViewModel by activityViewModels()

    private lateinit var _binding: DB
    protected val binding get() = _binding


    // View를 초기화하는 부분
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DataBindingUtil.inflate(inflater, layoutResId, container, false)
        with(_binding){
            lifecycleOwner = this@BaseFragment
        }

        loadView()
        registerListener()
        registerFlow()
        return binding.root
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