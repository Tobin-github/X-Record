package top.tobin.xrecord.ui.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.data.repository.AuthRepository
import top.tobin.xrecord.data.repository.CategoryError
import top.tobin.xrecord.data.repository.CategoryRepository
import top.tobin.xrecord.data.repository.CategoryResult

enum class CategoriesMessage {
    NAME_EMPTY,
    PRESET_CANNOT_DELETE,
    IN_USE,
    HAS_CHILDREN,
}

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val type: CategoryType = CategoryType.EXPENSE,
    val categories: List<CategoryEntity> = emptyList(),
    val message: CategoriesMessage? = null,
) {
    val topLevel: List<CategoryEntity> get() = categories.filter { it.parentId == null }

    fun childrenOf(categoryId: Long): List<CategoryEntity> =
        categories.filter { it.parentId == categoryId }
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val typeFlow = MutableStateFlow(CategoryType.EXPENSE)
    private val messageFlow = MutableStateFlow<CategoriesMessage?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CategoriesUiState> = combine(
        authRepository.currentUserId,
        typeFlow,
    ) { userId, type -> userId to type }
        .flatMapLatest { (userId, type) ->
            if (userId == null) {
                flowOf(CategoriesUiState(isLoading = false, type = type))
            } else {
                categoryRepository.observeCategories(userId, type).map { categories ->
                    CategoriesUiState(isLoading = false, type = type, categories = categories)
                }
            }
        }
        .combine(messageFlow) { state, message -> state.copy(message = message) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    fun setType(type: CategoryType) {
        typeFlow.value = type
    }

    fun create(name: String, parentId: Long?) {
        viewModelScope.launch {
            val userId = authRepository.currentUserId.first() ?: return@launch
            handle(categoryRepository.create(userId, typeFlow.value, name, parentId))
        }
    }

    fun rename(category: CategoryEntity, name: String) {
        viewModelScope.launch { handle(categoryRepository.rename(category, name)) }
    }

    fun setHidden(category: CategoryEntity, hidden: Boolean) {
        viewModelScope.launch { categoryRepository.setHidden(category.id, hidden) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { handle(categoryRepository.delete(category)) }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    private fun handle(result: CategoryResult) {
        if (result is CategoryResult.Failure) {
            messageFlow.value = when (result.error) {
                CategoryError.NAME_EMPTY -> CategoriesMessage.NAME_EMPTY
                CategoryError.PRESET_CANNOT_DELETE -> CategoriesMessage.PRESET_CANNOT_DELETE
                CategoryError.IN_USE -> CategoriesMessage.IN_USE
                CategoryError.HAS_CHILDREN -> CategoriesMessage.HAS_CHILDREN
                CategoryError.UNKNOWN -> CategoriesMessage.NAME_EMPTY
            }
        }
    }
}
