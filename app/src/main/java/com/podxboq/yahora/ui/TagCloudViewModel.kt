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
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.LogEntryResult
import com.podxboq.yahora.data.Tag
import com.podxboq.yahora.data.TagName
import com.podxboq.yahora.data.TagRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a typed tag name was refused. */
enum class TagNameError {
    BLANK,
    DUPLICATE,
    TOO_LONG,
}

data class TagCloudUiState(
    val tags: List<Tag> = emptyList(),
    val isAddDialogVisible: Boolean = false,
    val draftName: String = "",
    val nameError: TagNameError? = null,
)

/**
 * A one-shot announcement for the user, shown and forgotten. Kept out of
 * [TagCloudUiState] so it cannot be replayed when the screen recomposes.
 */
sealed interface TagCloudMessage {
    data class EntryLogged(val tagName: String, val timestamp: Long) : TagCloudMessage
    data class EntryFailed(val tagName: String) : TagCloudMessage
}

class TagCloudViewModel(
    private val tagRepository: TagRepository,
    private val entryRepository: EntryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagCloudUiState())
    val uiState: StateFlow<TagCloudUiState> = _uiState.asStateFlow()

    private val _messages = Channel<TagCloudMessage>(Channel.BUFFERED)
    val messages: Flow<TagCloudMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }
    }

    /**
     * A short tap logs an entry straight away: no intermediate screen and no
     * confirmation dialog, per the product spec.
     */
    fun onTagClick(tag: Tag) {
        viewModelScope.launch {
            val message = when (val result = entryRepository.logEntry(tag.id)) {
                is LogEntryResult.Logged ->
                    TagCloudMessage.EntryLogged(tag.name, result.timestamp)

                LogEntryResult.TagNotFound ->
                    TagCloudMessage.EntryFailed(tag.name)
            }
            _messages.send(message)
        }
    }

    fun onAddTagClick() {
        _uiState.update { it.copy(isAddDialogVisible = true, draftName = "", nameError = null) }
    }

    fun onDraftNameChange(name: String) {
        // Pasting an oversized name keeps what fits rather than rejecting it all.
        // Typing is also the user correcting themselves: drop any previous complaint.
        _uiState.update { it.copy(draftName = TagName.truncate(name), nameError = null) }
    }

    fun onDismissAddDialog() {
        _uiState.update { it.copy(isAddDialogVisible = false, draftName = "", nameError = null) }
    }

    fun onConfirmAddTag() {
        val name = _uiState.value.draftName
        viewModelScope.launch {
            when (tagRepository.addTag(name)) {
                is AddTagResult.Created ->
                    _uiState.update {
                        it.copy(isAddDialogVisible = false, draftName = "", nameError = null)
                    }

                AddTagResult.DuplicateName ->
                    _uiState.update { it.copy(nameError = TagNameError.DUPLICATE) }

                AddTagResult.BlankName ->
                    _uiState.update { it.copy(nameError = TagNameError.BLANK) }

                AddTagResult.NameTooLong ->
                    _uiState.update { it.copy(nameError = TagNameError.TOO_LONG) }
            }
        }
    }

    companion object {
        fun factory(
            tagRepository: TagRepository,
            entryRepository: EntryRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TagCloudViewModel(tagRepository, entryRepository) as T
            }
    }
}
