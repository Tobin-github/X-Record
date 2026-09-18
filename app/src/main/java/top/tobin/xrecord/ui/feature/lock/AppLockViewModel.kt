package top.tobin.xrecord.ui.feature.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.core.security.AppLockManager
import top.tobin.xrecord.core.security.AppLockState
import top.tobin.xrecord.core.security.BiometricPromptLauncher
import top.tobin.xrecord.data.preferences.AppLockConfig
import top.tobin.xrecord.data.preferences.SettingsDataSource

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockManager: AppLockManager,
    settings: SettingsDataSource,
    @ApplicationContext context: Context,
) : ViewModel() {

    val state: StateFlow<AppLockState> = appLockManager.state

    val config: StateFlow<AppLockConfig> = settings.appLockConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppLockConfig(),
        )

    /** 设备是否具备可用的生物识别。不具备时设置页隐藏对应开关。 */
    val biometricAvailable: Boolean = BiometricPromptLauncher.canAuthenticate(context)

    private val _pinRejected = MutableStateFlow(false)
    val pinRejected: StateFlow<Boolean> = _pinRejected.asStateFlow()

    fun submitPin(pin: String) {
        viewModelScope.launch {
            if (appLockManager.verify(pin)) {
                _pinRejected.value = false
                appLockManager.unlock()
            } else {
                _pinRejected.value = true
            }
        }
    }

    fun clearPinError() {
        _pinRejected.value = false
    }

    fun unlock() {
        appLockManager.unlock()
    }

    fun lockNow() {
        appLockManager.lock()
    }

    fun enable(pin: String) {
        viewModelScope.launch { appLockManager.enable(pin) }
    }

    fun changePin(pin: String) {
        viewModelScope.launch { appLockManager.changePin(pin) }
    }

    fun disable() {
        viewModelScope.launch { appLockManager.disable() }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { appLockManager.setBiometricEnabled(enabled) }
    }

    fun setTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { appLockManager.setTimeoutSeconds(seconds) }
    }

    fun onBackgrounded(now: Long) {
        appLockManager.onBackgrounded(now)
    }

    fun onForegrounded(now: Long) {
        appLockManager.onForegrounded(now)
    }
}
