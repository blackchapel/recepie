package com.blackchapel.recipai.ui.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blackchapel.recipai.core.DraftErrors
import com.blackchapel.recipai.core.PhotoSet
import com.blackchapel.recipai.core.Photos
import com.blackchapel.recipai.core.RecipeDraft
import com.blackchapel.recipai.ui.photos.EditorPhotoAdding
import java.util.UUID

/**
 * One open editor: the draft being edited and the bits of UI state that
 * must survive rotation. Held by `AppController` (one per `EditorPage`),
 * so it lives as long as the page does.
 */
@Stable
class EditorState(val initial: RecipeDraft) {
    var draft by mutableStateOf(initial)

    /** Validation messages show once a Save has failed, then stay live. */
    var showErrors by mutableStateOf(false)

    /** Rows on their way out (they animate, then leave the draft). */
    val removing = mutableStateListOf<String>()

    /**
     * Rows added while editing, which animate in. Rows the editor opened
     * with appear without it. Only read when a row first composes.
     */
    val addedRows = HashSet<String>()

    /** A field to focus on the next frame ([FieldKey]); cleared once done. */
    var focusRequest by mutableStateOf<String?>(null)

    /** The ingredient row whose unit field has focus (shows unit suggestions). */
    var unitFocus by mutableStateOf<String?>(null)

    /** Photos being copied in, which can finish after a rotation recreated the screen. */
    val photoAdding = EditorPhotoAdding()

    /** What's typed in the tag field but not yet added. */
    var tagQuery by mutableStateOf("")

    /**
     * Adds photos that finished copying to the draft as it is now. Photos
     * are copied in the app's scope and can finish after a rotation, so
     * this reads the live draft (not one captured when the pick started),
     * and a photo removed in the meantime stays removed.
     */
    fun addPhotos(names: List<String>) {
        val now = Photos.add(PhotoSet(draft.photos, draft.coverIndex), names)
        draft = draft.copy(photos = now.photos, coverIndex = now.cover)
    }

    /** The draft as it will be saved: rows still animating out already gone. */
    val effective: RecipeDraft
        get() = removing.fold(draft) { d, id -> d.removeIngredient(id).removeStep(id) }

    /** Anything to lose on Cancel: an edit, or a tag typed but not added. */
    val dirty: Boolean get() = effective != initial || tagQuery.isNotBlank()

    val errors: DraftErrors get() = effective.errors()

    fun newId(): String = UUID.randomUUID().toString()

    /** Adds a row's id to [addedRows] and asks for [focus] once it is on screen. */
    fun added(id: String, focus: String?) {
        addedRows += id
        if (focus != null) focusRequest = focus
    }

    fun remove(id: String) {
        if (id !in removing) removing += id
        if (unitFocus == id) unitFocus = null
    }

    /** The removal animation finished: drop the row for real. */
    fun gone(id: String) {
        draft = draft.removeIngredient(id).removeStep(id)
        removing.remove(id)
        addedRows.remove(id)
    }
}

/** Keys for the editor's focusable fields, so a field can be focused by name. */
object FieldKey {
    const val TITLE = "title"
    fun qty(id: String) = "$id:qty"
    fun unit(id: String) = "$id:unit"
    fun name(id: String) = "$id:name"
    fun note(id: String) = "$id:note"
    fun stepText(id: String) = "$id:text"
    fun minutes(id: String) = "$id:min"

    /** The first field a failed Save should take the user to. */
    fun firstError(draft: RecipeDraft, errors: DraftErrors): String? {
        if (errors.titleMissing) return TITLE
        draft.ingredients.firstOrNull { it.id in errors.badQuantities || it.id in errors.missingNames }?.let {
            return if (it.id in errors.badQuantities) qty(it.id) else name(it.id)
        }
        return draft.steps.firstOrNull { it.id in errors.badMinutes }?.let { minutes(it.id) }
    }
}
