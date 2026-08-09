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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.podxboq.yahora.data.AddTagResult
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a typed tag name was refused. */
enum class TagNameError {
    BLANK,
    DUPLICATE,
}

data class TagCloudUiState(
    val tags: List<Tag> = emptyList(),
    val isAddDialogVisible: Boolean = false,
    val draftName: String = "",
    val nameError: TagNameError? = null,
)

class TagCloudViewModel(private val repository: TagRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TagCloudUiState())
    val uiState: StateFlow<TagCloudUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }
    }

    fun onAddTagClick() {
        _uiState.update { it.copy(isAddDialogVisible = true, draftName = "", nameError = null) }
    }

    fun onDraftNameChange(name: String) {
        // Typing is the user correcting themselves: drop any previous complaint.
        _uiState.update { it.copy(draftName = name, nameError = null) }
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(isAddDialogVisible = false, draftName = "", nameError = null) }
    }

    fun onConfirmAddTag() {
        val name = _uiState.value.draftName
        viewModelScope.launch {
            when (repository.addTag(name)) {
                is AddTagResult.Created ->
                    _uiState.update {
                        it.copy(isAddDialogVisible = false, draftName = "", nameError = null)
                    }

                AddTagResult.DuplicateName ->
                    _uiState.update { it.copy(nameError = TagNameError.DUPLICATE) }

                AddTagResult.BlankName ->
                    _uiState.update { it.copy(nameError = TagNameError.BLANK) }
            }
        }
    }

    companion object {
        fun factory(repository: TagRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TagCloudViewModel(repository) as T
            }
    }
}
