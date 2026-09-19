package top.tobin.xrecord.ui.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import top.tobin.xrecord.R
import top.tobin.xrecord.core.money.MoneyFormatter
import top.tobin.xrecord.ui.components.WheelDatePickerDialog
import top.tobin.xrecord.core.util.DateTimeUtils
import top.tobin.xrecord.data.local.dao.AccountWithBalance
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.ui.theme.onColorFor
import top.tobin.xrecord.ui.theme.parseHexColor

private val KEYPAD_ROWS = listOf(
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "-"),
    listOf(".", "0", "⌫", "+"),
)

private const val KEY_BACKSPACE = "⌫"

/** 记账键盘。数字之外支持 + − × ÷ 连续运算，最后一行为求值与保存。 */
@Composable
internal fun AmountKeypad(
    canSave: Boolean,
    isSaving: Boolean,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEvaluate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KEYPAD_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        label = key,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = {
                            if (key == KEY_BACKSPACE) onBackspace() else onKey(key[0])
                        },
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            KeypadKey(
                label = "=",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onEvaluate,
                emphasized = true,
            )
            SaveKey(
                enabled = canSave,
                isSaving = isSaving,
                onClick = onSave,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SaveKey(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = stringResource(R.string.editor_save),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 分类宫格。
 *
 * 图标暂时用分类名首字加圆底代替：预置分类的图标键（如 `restaurant`）目前还没有对应素材，
 * 与其塞一堆占位图，不如先保证可读。
 */
@Composable
internal fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryCell(
                category = category,
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = parseHexColor(category.color, MaterialTheme.colorScheme.primary)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            // 用 selectable 而不是 clickable：单选控件需要把选中态暴露给无障碍服务，
            // 否则读屏用户完全无法知道当前选中了哪个分类
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 选中态用"实心圆 + 一圈浅色光晕"双层表达。
        // 之前只有填充深浅的差别，在浅色底上几乎看不出来，用户以为点击没生效。
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (selected) color.copy(alpha = 0.25f) else Color.Transparent)
                .padding(3.dp)
                .clip(CircleShape)
                .background(if (selected) color else color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) color.onColorFor() else color,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
internal fun AccountSelector(
    label: String,
    selected: AccountWithBalance?,
    accounts: List<AccountWithBalance>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selected?.account?.name ?: stringResource(R.string.editor_pick_account),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected != null) {
                Text(
                    text = MoneyFormatter.format(selected.balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (account.account.isArchived) {
                                stringResource(
                                    R.string.editor_account_archived,
                                    account.account.name,
                                )
                            } else {
                                account.account.name
                            },
                        )
                    },
                    onClick = {
                        onSelect(account.account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateSelector(
    occurredAt: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showOptions by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    AssistChip(
        onClick = { showOptions = true },
        label = { Text(text = DateTimeUtils.dayLabel(DateTimeUtils.toLocalDate(occurredAt), today)) },
        modifier = modifier,
    )

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(text = stringResource(R.string.editor_pick_date)) },
            text = {
                Column {
                    listOf(0L, 1L, 2L).forEach { daysAgo ->
                        val date = today.minusDays(daysAgo)
                        TextButton(
                            onClick = {
                                onChange(DateTimeUtils.withDate(occurredAt, date))
                                showOptions = false
                            },
                        ) {
                            Text(text = DateTimeUtils.dayLabel(date, today))
                        }
                    }
                    TextButton(
                        onClick = {
                            showOptions = false
                            showPicker = true
                        },
                    ) {
                        Text(text = stringResource(R.string.editor_pick_other_date))
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showPicker) {
        WheelDatePickerDialog(
            initialDate = DateTimeUtils.toLocalDate(occurredAt),
            onDismiss = { showPicker = false },
            onConfirm = { date ->
                // 只换日期、保留原来的时分，避免"改日期把时间清零"
                onChange(DateTimeUtils.withDate(occurredAt, date))
                showPicker = false
            },
        )
    }
}
