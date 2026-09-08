package com.koriym.alps.idea

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Wraps the standard text editor with an ALPS state-diagram preview, mirroring
 * the Markdown plugin's split-editor pattern. [FileEditorPolicy.HIDE_DEFAULT_EDITOR]
 * suppresses the plain JSON/XML editor tab that would otherwise also match.
 */
class AlpsFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        AlpsFileDetector.isAlpsFile(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val preview = AlpsPreviewFileEditor(file, textEditor)
        return TextEditorWithPreview(
            textEditor,
            preview,
            "ALPS Preview",
            TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW,
            false,
            TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW,
        )
    }

    override fun getEditorTypeId(): String = "alps-state-diagram-preview"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
