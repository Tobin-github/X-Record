package top.tobin.xrecord.ui.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.tobin.xrecord.R

/**
 * 快速记账面板。
 *
 * 保存后不关闭面板，只清空金额与备注，方便连续录入同一天的几笔账；
 * 用户手动下拉或点外部区域才关闭。
 *
 * "已记账"提示直接画在面板内，而不是走 Scaffold 的 Snackbar：弹层是独立窗口，
 * 主窗口的 Snackbar 会被它的遮罩挡住，用户根本看不到。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    onDismiss: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSavedNotice by remember { mutableStateOf(false) }

    // 每次打开面板都从干净的状态开始，避免上次没保存的内容残留
    LaunchedEffect(Unit) { viewModel.startNewEntry() }

    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) {
            viewModel.prepareForNextEntry()
            showSavedNotice = true
            delay(1_800)
            showSavedNotice = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column {
            // 固定高度占位，提示出现和消失都不会让下方的表单跳动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (showSavedNotice) {
                    Text(
                        text = stringResource(R.string.editor_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

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
            )
        }
    }
}
