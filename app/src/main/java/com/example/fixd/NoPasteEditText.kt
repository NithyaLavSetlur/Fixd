package com.example.fixd

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import androidx.appcompat.widget.AppCompatEditText

class NoPasteEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    init {
        isLongClickable = false
        setTextIsSelectable(false)
        customInsertionActionModeCallback = blockedCallback()
        customSelectionActionModeCallback = blockedCallback()
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        return when (id) {
            android.R.id.paste,
            android.R.id.pasteAsPlainText,
            android.R.id.cut,
            android.R.id.copy -> false
            else -> super.onTextContextMenuItem(id)
        }
    }

    private fun blockedCallback() = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: android.view.Menu?) = false
        override fun onPrepareActionMode(mode: ActionMode?, menu: android.view.Menu?) = false
        override fun onActionItemClicked(mode: ActionMode?, item: android.view.MenuItem?) = false
        override fun onDestroyActionMode(mode: ActionMode?) = Unit
    }
}
