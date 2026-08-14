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
import com.podxboq.yahora.data.DeleteTagResult
import com.podxboq.yahora.data.EntryRepository
import com.podxboq.yahora.data.LogEntryResult
import com.podxboq.yahora.data.RenameTagResult
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
    /** The tag whose context menu is open, if any. */
    val menuTagId: Long? = null,
    /**
     * The tag being renamed, if any. It shares [draftName] with the add dialog,
     * since only one of the two can be on screen at a time, and it is kept whole
     * so a failure can still name the tag the user was editing.
     */
    val renamingTag: Tag? = null,
    /** The tags that hold entries. They are the ones that cannot be deleted. */
    val tagIdsWithEntries: Set<Long> = emptySet(),
) {
    val renamingTagId: Long? get() = renamingTag?.id

    /**
     * Whether a tag has any history. It decides both halves of a pair of menu
     * actions that can never apply at once: with history there are entries to
     * view and the tag cannot be deleted; without it there is nothing to show
     * and deleting is safe. The inapplicable one is left out of the menu
     * entirely rather than shown greyed out.
     */
    fun hasEntries(tagId: Long): Boolean = tagId in tagIdsWithEntries
}

/**
 * A one-shot announcement for the user, shown and forgotten. Kept out of
 * [TagCloudUiState] so it cannot be replayed when the screen recomposes.
 */
sealed interface TagCloudMessage {
    data class EntryLogged(val tagName: String, val timestamp: Long) : TagCloudMessage
    data class EntryFailed(val tagName: String) : TagCloudMessage
    data class RenameFailed(val tagName: String) : TagCloudMessage
    data class DeleteFailed(val tagName: String) : TagCloudMessage
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
        viewModelScope.launch {
            entryRepository.observeTagIdsWithEntries().collect { ids ->
                _uiState.update { it.copy(tagIdsWithEntries = ids.toSet()) }
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

    /**
     * A long press opens the tag's context menu — and logs nothing: only a short
     * tap records an entry.
     */
    fun onTagLongClick(tag: Tag) {
        _uiState.update { it.copy(menuTagId = tag.id) }
    }

    fun onDismissMenu() {
        _uiState.update { it.copy(menuTagId = null) }
    }

    /** Stars a tag, or unstars it. Emphasis only: the cloud's order is untouched. */
    fun onToggleFavorite(tag: Tag) {
        onDismissMenu()
        viewModelScope.launch { tagRepository.toggleFavorite(tag.id) }
    }

    /**
     * Deletes a tag outright: no confirmation dialog, because a tag that can be
     * deleted at all holds no history to lose. The menu is closed first, since
     * the chip it hangs from is about to disappear.
     */
    fun onDeleteClick(tag: Tag) {
        onDismissMenu()
        viewModelScope.launch {
            when (tagRepository.deleteTag(tag.id)) {
                DeleteTagResult.Deleted -> Unit

                // The menu hides the action, but an entry may have landed while
                // it was open. The foreign key is the guarantee, not the UI.
                DeleteTagResult.HasEntries ->
                    _messages.send(TagCloudMessage.DeleteFailed(tag.name))
            }
        }
    }

    /** Opens the rename dialog on the name the tag has now, ready to be edited. */
    fun onRenameClick(tag: Tag) {
        _uiState.update {
            it.copy(
                menuTagId = null,
                renamingTag = tag,
                draftName = tag.name,
                nameError = null,
            )
        }
    }

    fun onDismissRenameDialog() {
        _uiState.update { it.copy(renamingTag = null, draftName = "", nameError = null) }
    }

    fun onConfirmRename() {
        val tag = _uiState.value.renamingTag ?: return
        val name = _uiState.value.draftName
        viewModelScope.launch {
            when (tagRepository.renameTag(tag.id, name)) {
                RenameTagResult.Renamed -> onDismissRenameDialog()

                RenameTagResult.DuplicateName ->
                    _uiState.update { it.copy(nameError = TagNameError.DUPLICATE) }

                RenameTagResult.BlankName ->
                    _uiState.update { it.copy(nameError = TagNameError.BLANK) }

                RenameTagResult.NameTooLong ->
                    _uiState.update { it.copy(nameError = TagNameError.TOO_LONG) }

                // Nothing left to edit: report it and close rather than leaving
                // a dialog that can never succeed.
                RenameTagResult.TagNotFound -> {
                    onDismissRenameDialog()
                    _messages.send(TagCloudMessage.RenameFailed(tag.name))
                }
            }
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
