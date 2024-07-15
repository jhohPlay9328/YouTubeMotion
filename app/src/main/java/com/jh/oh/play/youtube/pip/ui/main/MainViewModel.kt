package com.jh.oh.play.youtube.pip.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jh.oh.play.youtube.pip.BaseViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
): BaseViewModel(application){
    private val _setBottomMenuMotion = MutableSharedFlow<Float>()
    val setBottomMenuMotion = _setBottomMenuMotion.asSharedFlow()

    private val _enterPictureInPicture = MutableSharedFlow<Boolean>()
    val enterPictureInPicture = _enterPictureInPicture.asSharedFlow()

    fun setBottomMenuMotion(progress: Float) {
        viewModelScope.launch {
            _setBottomMenuMotion.emit(progress)
        }
    }

    fun setEnterPictureInPicture() {
        viewModelScope.launch {
            _enterPictureInPicture.emit(true)
        }
    }
}