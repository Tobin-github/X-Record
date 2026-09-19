package top.tobin.xrecord.ui.feature.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tobin.xrecord.R

/** 编辑已有流水。保存成功后直接返回列表，列表由数据库变更自动刷新。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        TransactionEditorContent(
            state = state,
            onTypeChange = viewModel::onTypeChange,
            onKey = viewModel::onKey,
            onBackspace = viewModel::onBackspace,
            onEvaluate = viewModel::onEvaluate,
            onCategorySelect = viewModel::onCategorySelect,
            onAccountSelect = viewModel::onAccountSelect,
            onToAccountSelect = viewModel::onToAccountSelect,
            onDateChange = viewModel::onDateChange,
            onRemarkChange = viewModel::onRemarkChange,
            onSave = viewModel::save,
            modifier = Modifier
                // 不再整体加 verticalScroll：表单内部已分好可滚动与固定区，
                // 两层滚动会让手指落在哪一层变得不可预期
                .padding(innerPadding),
        )
    }
}
