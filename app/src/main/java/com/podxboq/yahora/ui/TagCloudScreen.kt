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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podxboq.yahora.R
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.ui.theme.YahoraTheme

/**
 * Everything the tag cloud can ask of its host. Grouped in one type so the
 * stateless content is easy to drive from tests.
 */
data class TagCloudCallbacks(
    val onAddTagClick: () -> Unit,
    val onDraftNameChange: (String) -> Unit,
    val onConfirmAddTag: () -> Unit,
    val onDismissAddDialog: () -> Unit,
)

@Composable
fun TagCloudScreen(viewModel: TagCloudViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TagCloudContent(
        state = state,
        callbacks = TagCloudCallbacks(
            onAddTagClick = viewModel::onAddTagClick,
            onDraftNameChange = viewModel::onDraftNameChange,
            onConfirmAddTag = viewModel::onConfirmAddTag,
            onDismissAddDialog = viewModel::onDismissAddDialog,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagCloudContent(
    state: TagCloudUiState,
    callbacks: TagCloudCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = callbacks.onAddTagClick) {
                Text(stringResource(R.string.add_tag))
            }
        },
    ) { innerPadding ->
        if (state.tags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tag_cloud_empty))
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.tags.forEach { tag ->
                    SuggestionChip(
                        onClick = { /* Logging an entry arrives with the next feature. */ },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
    }

    if (state.isAddDialogVisible) {
        AddTagDialog(state = state, callbacks = callbacks)
    }
}

@Composable
private fun AddTagDialog(state: TagCloudUiState, callbacks: TagCloudCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissAddDialog,
        title = { Text(stringResource(R.string.add_tag_dialog_title)) },
        text = {
            OutlinedTextField(
                value = state.draftName,
                onValueChange = callbacks.onDraftNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tag_name_label)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { error ->
                    { Text(stringResource(error.messageRes())) }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = callbacks.onConfirmAddTag) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = callbacks.onDismissAddDialog) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun TagNameError.messageRes(): Int = when (this) {
    TagNameError.BLANK -> R.string.tag_name_error_blank
    TagNameError.DUPLICATE -> R.string.tag_name_error_duplicate
}

@Preview(showBackground = true)
@Composable
private fun TagCloudContentPreview() {
    YahoraTheme(dynamicColor = false) {
        TagCloudContent(
            state = TagCloudUiState(
                tags = listOf(Tag(id = 1, name = "Coffee"), Tag(id = 2, name = "Medication")),
            ),
            callbacks = TagCloudCallbacks({}, {}, {}, {}),
        )
    }
}
