package com.koriym.alps.idea

import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Headless verification of the provider wiring (accept/createEditor/dispose).
 * Actual JCEF rendering and click-to-navigate cannot run headlessly (no
 * desktop session, JBCefApp.isSupported() is false here) — see AGENTS.md /
 * plan notes for the manual `runIde` check required before real-world use.
 */
class AlpsFileEditorProviderTest : BasePlatformTestCase() {

    private val provider = AlpsFileEditorProvider()

    fun testAcceptsAlpsJson() {
        val file = myFixture.configureByText("alps.json", ALPS_JSON_SAMPLE).virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testRejectsPlainJson() {
        val file = myFixture.configureByText("plain.json", """{"foo": "bar"}""").virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testRejectsAlpsLikeNonJsonExtension() {
        val file = myFixture.configureByText("alps.txt", ALPS_JSON_SAMPLE).virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testAcceptsAlpsXml() {
        val file = myFixture.configureByText(
            "alps.xml",
            """<alps version="1.0"><descriptor id="goStart" type="safe"/></alps>""",
        ).virtualFile
        assertTrue(provider.accept(project, file))
    }

    fun testRejectsPlainXml() {
        val file = myFixture.configureByText("plain.xml", "<root/>").virtualFile
        assertFalse(provider.accept(project, file))
    }

    fun testCreateEditorReturnsSplitPreviewWithHiddenDefaultEditor() {
        val file = myFixture.configureByText("alps.json", ALPS_JSON_SAMPLE).virtualFile
        val editor = provider.createEditor(project, file)
        Disposer.register(testRootDisposable, editor)
        // TextEditorWithPreview eagerly warms its lazily-built UI via
        // invokeLater at construction; flush it before tearDown disposes
        // the editor, or the queued UI init races the disposal.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(editor is TextEditorWithPreview)
    }

    private companion object {
        val ALPS_JSON_SAMPLE = """{"alps": {"descriptor": [{"id": "goStart", "type": "safe"}]}}"""
    }
}
