/*
 * Copyright (C) 2026 podxboq
 *
 * This file is part of Yahora.
 *
 * Yahora is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.podxboq.yahora.ui

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podxboq.yahora.R
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagName
import com.podxboq.yahora.ui.theme.YahoraTheme
import kotlinx.coroutines.flow.collectLatest
import java.util.Date

/**
 * Everything the tag cloud can ask of its host. Grouped in one type so the
 * stateless content is easy to drive from tests.
 */
data class TagCloudCallbacks(
    val onAddTagClick: () -> Unit,
    val onDraftNameChange: (String) -> Unit,
    val onConfirmAddTag: () -> Unit,
    val onDismissAddDialog: () -> Unit,
    val onTagClick: (Tag) -> Unit,
    val onTagLongClick: (Tag) -> Unit,
    val onDismissMenu: () -> Unit,
    val onViewEntries: (Tag) -> Unit,
    val onRenameClick: (Tag) -> Unit,
    val onConfirmRename: () -> Unit,
    val onDismissRenameDialog: () -> Unit,
    val onDeleteClick: (Tag) -> Unit,
    val onToggleFavorite: (Tag) -> Unit,
    val onPurgeClick: (Tag) -> Unit,
    val onConfirmPurge: () -> Unit,
    val onDismissPurgeDialog: () -> Unit,
)

@Composable
fun TagCloudScreen(
    viewModel: TagCloudViewModel,
    onOpenTagDetail: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        // collectLatest, so a second tap replaces the first announcement instead
        // of queueing behind it: the offer to undo must be about the last tap.
        viewModel.messages.collectLatest { message ->
            val undoable = message as? TagCloudMessage.EntryLogged
            val result = snackbarHostState.showSnackbar(
                message = message.toText(context),
                actionLabel = undoable?.let { undoLabel },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && undoable != null) {
                viewModel.onUndoEntry(undoable.entryId)
            }
        }
    }

    TagCloudContent(
        state = state,
        snackbarHostState = snackbarHostState,
        callbacks = TagCloudCallbacks(
            onAddTagClick = viewModel::onAddTagClick,
            onDraftNameChange = viewModel::onDraftNameChange,
            onConfirmAddTag = viewModel::onConfirmAddTag,
            onDismissAddDialog = viewModel::onDismissAddDialog,
            onTagClick = viewModel::onTagClick,
            onTagLongClick = viewModel::onTagLongClick,
            onDismissMenu = viewModel::onDismissMenu,
            onViewEntries = { tag ->
                viewModel.onDismissMenu()
                onOpenTagDetail(tag)
            },
            onRenameClick = viewModel::onRenameClick,
            onConfirmRename = viewModel::onConfirmRename,
            onDismissRenameDialog = viewModel::onDismissRenameDialog,
            onDeleteClick = viewModel::onDeleteClick,
            onToggleFavorite = viewModel::onToggleFavorite,
            onPurgeClick = viewModel::onPurgeClick,
            onConfirmPurge = viewModel::onConfirmPurge,
            onDismissPurgeDialog = viewModel::onDismissPurgeDialog,
        ),
        modifier = modifier,
    )
}

private fun TagCloudMessage.toText(context: Context): String = when (this) {
    is TagCloudMessage.EntryLogged -> context.getString(
        R.string.entry_logged,
        tagName,
        // The device's own 12h/24h preference, not a hardcoded pattern.
        DateFormat.getTimeFormat(context).format(Date(timestamp)),
    )

    is TagCloudMessage.EntryFailed -> context.getString(R.string.entry_failed, tagName)

    is TagCloudMessage.RenameFailed -> context.getString(R.string.rename_failed, tagName)

    is TagCloudMessage.DeleteFailed -> context.getString(R.string.delete_failed, tagName)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagCloudContent(
    state: TagCloudUiState,
    callbacks: TagCloudCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = callbacks.onAddTagClick) {
                Text(stringResource(R.string.add_tag))
            }
        },
    ) { innerPadding ->
        if (state.tags.isEmpty()) {
            EmptyTagCloud(modifier = Modifier.padding(innerPadding))
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.tags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        isMenuOpen = state.menuTagId == tag.id,
                        hasEntries = state.hasEntries(tag.id),
                        canPurge = state.canPurgeTags,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }

    if (state.isAddDialogVisible) {
        TagNameDialog(
            title = stringResource(R.string.add_tag_dialog_title),
            confirmLabel = stringResource(R.string.action_create),
            state = state,
            onConfirm = callbacks.onConfirmAddTag,
            onDismiss = callbacks.onDismissAddDialog,
            onDraftNameChange = callbacks.onDraftNameChange,
        )
    }

    // Deleting an empty tag needs no confirmation; this one does, because it is
    // the single place in the app where history is discarded on purpose.
    state.purgingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = callbacks.onDismissPurgeDialog,
            title = { Text(stringResource(R.string.purge_tag_dialog_title, tag.name)) },
            text = { Text(stringResource(R.string.purge_tag_dialog_text)) },
            confirmButton = {
                TextButton(onClick = callbacks.onConfirmPurge) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissPurgeDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Only ever one of the two: renaming and creating share the draft name.
    state.renamingTag?.let { tag ->
        TagNameDialog(
            title = stringResource(R.string.rename_tag_dialog_title, tag.name),
            confirmLabel = stringResource(R.string.action_rename),
            state = state,
            onConfirm = callbacks.onConfirmRename,
            onDismiss = callbacks.onDismissRenameDialog,
            onDraftNameChange = callbacks.onDraftNameChange,
        )
    }
}

/**
 * One tag in the cloud, and the anchor its context menu hangs from. Favorites
 * carry a star: the highlight must not rely on color alone, and the star is
 * described so screen readers announce it too.
 *
 * Built on [Surface] rather than `SuggestionChip` because the chip owns its
 * click handling and never reports a long press, which is the gesture the
 * context menu needs. The chip is as wide as its own name; every tag shares one
 * type style, which is what the spec's "uniform size" means.
 */
@Composable
private fun TagChip(
    tag: Tag,
    isMenuOpen: Boolean,
    hasEntries: Boolean,
    canPurge: Boolean,
    callbacks: TagCloudCallbacks,
) {
    val view = LocalView.current

    Box {
        Surface(
            modifier = Modifier
                .heightIn(min = ChipHeight)
                .combinedClickable(
                    role = Role.Button,
                    onClick = {
                        // The tap is the whole interaction and nothing moves on
                        // screen, so a haptic tick is the confirmation you get
                        // without having to look.
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                HapticFeedbackConstants.CONFIRM
                            } else {
                                HapticFeedbackConstants.VIRTUAL_KEY
                            },
                        )
                        callbacks.onTagClick(tag)
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        callbacks.onTagLongClick(tag)
                    },
                ),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (tag.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.favorite_tag),
                        modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                    )
                }
                // A safety net for narrow screens and large accessibility fonts:
                // the length cap alone does not guarantee the name fits.
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // DropdownMenu brings its own open and close transition, so the menu
        // grows out of the chip it belongs to instead of appearing whole.
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = callbacks.onDismissMenu) {
            // Two actions, one condition: history means there is something to
            // show and nothing that may be deleted, and the reverse. Neither is
            // ever greyed out — the one that does not apply is simply absent.
            if (hasEntries) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tag_menu_view_entries)) },
                    onClick = { callbacks.onViewEntries(tag) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_menu_rename)) },
                onClick = { callbacks.onRenameClick(tag) },
            )
            DropdownMenuItem(
                // The item names what it will do, not what the tag is now.
                text = {
                    Text(
                        stringResource(
                            if (tag.isFavorite) {
                                R.string.tag_menu_unfavorite
                            } else {
                                R.string.tag_menu_favorite
                            },
                        ),
                    )
                },
                onClick = { callbacks.onToggleFavorite(tag) },
            )
            if (!hasEntries) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tag_menu_delete)) },
                    onClick = { callbacks.onDeleteClick(tag) },
                )
            } else if (canPurge) {
                // Debug builds only, and only where the ordinary delete is
                // absent: a way to clear out the records left by trying the app
                // out. It names the history it takes, since that is the whole
                // difference from the delete above.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tag_menu_purge)) },
                    onClick = { callbacks.onPurgeClick(tag) },
                )
            }
        }
    }
}

private val ChipHeight = 32.dp

@Composable
private fun EmptyTagCloud(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.tag_cloud_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tag_cloud_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Naming a tag, whether it is being created or renamed: same field, same limit,
 * same errors. Only the title and the confirm label tell the two apart.
 */
@Composable
private fun TagNameDialog(
    title: String,
    confirmLabel: String,
    state: TagCloudUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDraftNameChange: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = state.draftName,
                onValueChange = onDraftNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tag_name_label)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = supportingTextFor(state),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * An error, if there is one; otherwise a character counter, but only once the
 * limit is close enough to be worth mentioning. Below that the dialog stays bare.
 */
private fun supportingTextFor(state: TagCloudUiState): (@Composable () -> Unit)? {
    state.nameError?.let { error ->
        return { Text(stringResource(error.messageRes())) }
    }

    val length = TagName.lengthOf(state.draftName)
    if (TagName.MAX_LENGTH - length > TagName.COUNTER_THRESHOLD) return null

    return {
        Text(stringResource(R.string.tag_name_counter, length, TagName.MAX_LENGTH))
    }
}

private fun TagNameError.messageRes(): Int = when (this) {
    TagNameError.BLANK -> R.string.tag_name_error_blank
    TagNameError.DUPLICATE -> R.string.tag_name_error_duplicate
    TagNameError.TOO_LONG -> R.string.tag_name_error_too_long
}

@Preview(showBackground = true)
@Composable
private fun TagCloudContentPreview() {
    YahoraTheme(dynamicColor = false) {
        TagCloudContent(
            state = TagCloudUiState(
                tags = listOf(Tag(id = 1, name = "Coffee"), Tag(id = 2, name = "Medication")),
            ),
            callbacks = TagCloudCallbacks(
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            ),
        )
    }
}
