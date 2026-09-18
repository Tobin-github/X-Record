package top.tobin.xrecord.ui.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.tobin.xrecord.BuildConfig
import top.tobin.xrecord.core.di.IoDispatcher
import top.tobin.xrecord.data.backup.BackupSummary
import top.tobin.xrecord.data.backup.ImportError
import top.tobin.xrecord.data.backup.ImportResult
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.BackupRepository

enum class BackupMessage {
    EXPORT_SUCCESS,
    EXPORT_FAILED,
    IMPORT_SUCCESS,
    IMPORT_INVALID,
    IMPORT_UNSUPPORTED,
    IMPORT_BROKEN,
    READ_FAILED,
}

data class BackupUiState(
    val isBusy: Boolean = false,
    /** 不为 null 时表示已解析出备份内容，等待用户确认覆盖。 */
    val pendingImport: BackupSummary? = null,
    val message: BackupMessage? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** 待写入的用户选择的文件。确认导入时才读取第二次，避免把全文留在内存里。 */
    private var pendingUri: Uri? = null
    private var pendingText: String? = null

    fun suggestedFileName(): String = "XRecord-${LocalDate.now()}.json"

    fun export(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            val userId = authRepository.currentUserId.first()
            if (userId == null) {
                _uiState.update { it.copy(isBusy = false, message = BackupMessage.EXPORT_FAILED) }
                return@launch
            }

            val message = withContext(ioDispatcher) {
                runCatching {
                    val content = backupRepository.exportToJson(
                        userId = userId,
                        appVersionName = BuildConfig.VERSION_NAME,
                    )
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray())
                    } ?: error("无法写入所选文件")
                }.fold(
                    onSuccess = { BackupMessage.EXPORT_SUCCESS },
                    onFailure = { BackupMessage.EXPORT_FAILED },
                )
            }
            _uiState.update { it.copy(isBusy = false, message = message) }
        }
    }

    /** 读取并解析所选文件，成功则等待用户确认。 */
    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }

            val text = withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }.getOrNull()
            }

            if (text == null) {
                _uiState.update { it.copy(isBusy = false, message = BackupMessage.READ_FAILED) }
                return@launch
            }

            when (val result = backupRepository.parse(text)) {
                is ImportResult.Success -> {
                    pendingUri = uri
                    pendingText = text
                    _uiState.update {
                        it.copy(isBusy = false, pendingImport = result.summary)
                    }
                }

                is ImportResult.InvalidFormat -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = when (result.reason) {
                            ImportError.UNSUPPORTED_VERSION -> BackupMessage.IMPORT_UNSUPPORTED
                            ImportError.MALFORMED -> BackupMessage.IMPORT_INVALID
                        },
                    )
                }

                ImportResult.BrokenReferences -> _uiState.update {
                    it.copy(isBusy = false, message = BackupMessage.IMPORT_BROKEN)
                }
            }
        }
    }

    fun confirmImport() {
        val text = pendingText ?: return
        pendingUri = null

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, pendingImport = null) }
            val userId = authRepository.currentUserId.first()
            if (userId == null) {
                pendingText = null
                _uiState.update { it.copy(isBusy = false, message = BackupMessage.IMPORT_INVALID) }
                return@launch
            }

            val message = withContext(ioDispatcher) {
                when (backupRepository.importFromJson(userId, text)) {
                    is ImportResult.Success -> BackupMessage.IMPORT_SUCCESS
                    is ImportResult.InvalidFormat -> BackupMessage.IMPORT_INVALID
                    ImportResult.BrokenReferences -> BackupMessage.IMPORT_BROKEN
                }
            }
            pendingText = null
            _uiState.update { it.copy(isBusy = false, message = message) }
        }
    }

    fun cancelImport() {
        pendingUri = null
        pendingText = null
        _uiState.update { it.copy(pendingImport = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
