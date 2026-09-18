package top.tobin.xrecord.ui.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tobin.xrecord.R
import top.tobin.xrecord.data.local.entity.CategoryEntity
import top.tobin.xrecord.data.local.entity.CategoryType
import top.tobin.xrecord.ui.theme.onColorFor
import top.tobin.xrecord.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorTarget by remember { mutableStateOf<CategoryEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    val messageText = state.message?.let { message ->
        stringResource(
            when (message) {
                CategoriesMessage.NAME_EMPTY -> R.string.categories_error_name
                CategoriesMessage.PRESET_CANNOT_DELETE -> R.string.categories_error_preset
                CategoriesMessage.IN_USE -> R.string.categories_error_in_use
                CategoriesMessage.HAS_CHILDREN -> R.string.categories_error_has_children
            },
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.categories_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = if (state.type == CategoryType.EXPENSE) 0 else 1) {
                listOf(CategoryType.EXPENSE, CategoryType.INCOME).forEachIndexed { index, type ->
                    Tab(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        text = {
                            Text(
                                text = if (type == CategoryType.EXPENSE) {
                                    stringResource(R.string.records_expense)
                                } else {
                                    stringResource(R.string.records_income)
                                },
                            )
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            ) {
                state.topLevel.forEach { parent ->
                    item(key = "parent-${parent.id}") {
                        CategoryRow(
                            category = parent,
                            indent = false,
                            onRename = { editorTarget = CategoryEditorTarget.Rename(parent) },
                            onToggleHidden = { viewModel.setHidden(parent, !parent.isHidden) },
                            onDelete = { deleteTarget = parent },
                        )
                    }
                    items(
                        items = state.childrenOf(parent.id),
                        key = { "child-${it.id}" },
                    ) { child ->
                        CategoryRow(
                            category = child,
                            indent = true,
                            onRename = { editorTarget = CategoryEditorTarget.Rename(child) },
                            onToggleHidden = { viewModel.setHidden(child, !child.isHidden) },
                            onDelete = { deleteTarget = child },
                        )
                    }
                }

                item {
                    TextButton(onClick = { editorTarget = CategoryEditorTarget.Create }) {
                        Text(text = stringResource(R.string.categories_new))
                    }
                }
            }
        }
    }

    editorTarget?.let { target ->
        when (target) {
            is CategoryEditorTarget.Create -> CategoryNameDialog(
                title = stringResource(R.string.categories_new),
                initialName = "",
                parents = state.topLevel,
                showParentPicker = true,
                onDismiss = { editorTarget = null },
                onConfirm = { name, parentId ->
                    viewModel.create(name, parentId)
                    editorTarget = null
                },
            )

            is CategoryEditorTarget.Rename -> CategoryNameDialog(
                title = stringResource(R.string.categories_rename),
                initialName = target.category.name,
                parents = state.topLevel,
                showParentPicker = false,
                onDismiss = { editorTarget = null },
                onConfirm = { name, _ ->
                    viewModel.rename(target.category, name)
                    editorTarget = null
                },
            )
        }
    }

    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.categories_delete)) },
            text = { Text(text = stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(category)
                        deleteTarget = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private sealed interface CategoryEditorTarget {
    data object Create : CategoryEditorTarget

    data class Rename(val category: CategoryEntity) : CategoryEditorTarget
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    indent: Boolean,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val color = parseHexColor(category.color, MaterialTheme.colorScheme.primary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRename)
            .padding(start = if (indent) 24.dp else 0.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (indent) 28.dp else 36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.name.take(1),
                style = if (indent) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = color.onColorFor(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = category.name,
            style = if (indent) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (category.isPreset) {
            Text(
                text = stringResource(R.string.categories_preset_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (category.isHidden) {
            Text(
                text = stringResource(R.string.categories_hidden_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Text(text = "⋮", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.categories_rename)) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                if (category.isHidden) {
                                    R.string.categories_show
                                } else {
                                    R.string.categories_hide
                                },
                            ),
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleHidden()
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.categories_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    parents: List<CategoryEntity>,
    showParentPicker: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, parentId: Long?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var parentId by remember { mutableStateOf<Long?>(null) }
    var parentMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.categories_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showParentPicker) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { parentMenuExpanded = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.categories_parent),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = parents.firstOrNull { it.id == parentId }?.name
                                    ?: stringResource(R.string.categories_parent_none),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        DropdownMenu(
                            expanded = parentMenuExpanded,
                            onDismissRequest = { parentMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = stringResource(R.string.categories_parent_none))
                                },
                                onClick = {
                                    parentId = null
                                    parentMenuExpanded = false
                                },
                            )
                            parents.forEach { parent ->
                                DropdownMenuItem(
                                    text = { Text(text = parent.name) },
                                    onClick = {
                                        parentId = parent.id
                                        parentMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, parentId) }) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
