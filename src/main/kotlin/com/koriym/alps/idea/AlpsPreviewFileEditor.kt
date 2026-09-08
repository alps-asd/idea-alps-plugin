package com.koriym.alps.idea

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

private const val RENDER_DEBOUNCE_MS = 300

/**
 * Renders the ALPS state diagram in a JCEF browser loaded with the bundled
 * `web/asd.bundle.js` (parser/alps-parser + generator/dot-generator +
 * @viz-js/viz, browser-safe — see web/src/main.ts). Falls back to a plain
 * message when JCEF is unavailable in the running IDE/runtime.
 */
class AlpsPreviewFileEditor(
    private val file: VirtualFile,
    private val textEditor: TextEditor,
) : UserDataHolderBase(), FileEditor {

    private val changeSupport = PropertyChangeSupport(this)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val gson = Gson()

    // Guards invokeLater callbacks from JCEF's IO thread (click handler,
    // onLoadEnd) against running after the editor tab has been closed.
    @Volatile
    private var disposed = false
    private val disposedCondition = Condition<Any> { disposed }

    private val browser: JBCefBrowserBase? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val jsQuery: JBCefJSQuery? = browser?.let { JBCefJSQuery.create(it) }
    private var pageLoaded = false
    private var pendingText: String? = null

    private val rootComponent: JComponent = browser?.component ?: JLabel(
        "JCEF is not available in this IDE/runtime; ALPS preview requires it.",
        SwingConstants.CENTER,
    )

    init {
        val cefBrowser = browser
        if (cefBrowser != null) {
            Disposer.register(this, cefBrowser)
            jsQuery?.let { Disposer.register(this, it) }

            jsQuery?.addHandler { descriptorId ->
                ApplicationManager.getApplication().invokeLater(
                    { navigateToDescriptor(descriptorId) },
                    disposedCondition,
                )
                JBCefJSQuery.Response("")
            }

            cefBrowser.getJBCefClient().addLoadHandler(
                object : CefLoadHandlerAdapter() {
                    override fun onLoadEnd(nativeBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                        if (!frame.isMain) return
                        ApplicationManager.getApplication().invokeLater(
                            {
                                pageLoaded = true
                                val clickBridge = jsQuery?.inject("id").orEmpty()
                                nativeBrowser.executeJavaScript(
                                    "window.__onNodeClick = function(id) { $clickBridge };",
                                    nativeBrowser.getURL(),
                                    0,
                                )
                                pendingText?.let { renderNow(it) }
                            },
                            disposedCondition,
                        )
                    }
                },
                cefBrowser.cefBrowser,
            )

            cefBrowser.loadURL(extractedIndexHtml().toUri().toString())

            textEditor.editor.document.addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        scheduleRender()
                    }
                },
                this,
            )

            scheduleRender()
        }
    }

    private fun scheduleRender() {
        alarm.cancelAllRequests()
        alarm.addRequest({ renderNow(textEditor.editor.document.text) }, RENDER_DEBOUNCE_MS)
    }

    private fun renderNow(text: String) {
        if (!pageLoaded) {
            pendingText = text
            return
        }
        val nativeBrowser = browser?.cefBrowser ?: return
        val encodedText = gson.toJson(text)
        nativeBrowser.executeJavaScript(
            "window.__applyRender($encodedText, 'id');",
            nativeBrowser.getURL(),
            0,
        )
    }

    private fun navigateToDescriptor(descriptorId: String) {
        val editor: Editor = textEditor.editor
        val escapedId = Regex.escape(descriptorId)
        val pattern = Regex("\"id\"\\s*:\\s*\"$escapedId\"|\\bid\\s*=\\s*\"$escapedId\"")
        val match = pattern.find(editor.document.text) ?: return
        editor.caretModel.moveToOffset(match.range.first)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun extractedIndexHtml(): Path {
        val dir = Path.of(PathManager.getSystemPath(), "alps-preview-web")
        Files.createDirectories(dir)
        copyResource("/web/index.html", dir.resolve("index.html"))
        copyResource("/web/asd.bundle.js", dir.resolve("asd.bundle.js"))
        return dir.resolve("index.html")
    }

    private fun copyResource(resourcePath: String, target: Path) {
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: error("Missing bundled resource: $resourcePath")
        // Write to a temp file and atomically rename over the shared target:
        // opening a second ALPS file must not truncate the bundle mid-read
        // for a JCEF browser already loaded from the first.
        val tempFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        try {
            stream.use { Files.copy(it, tempFile, StandardCopyOption.REPLACE_EXISTING) }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    override fun getComponent(): JComponent = rootComponent

    override fun getPreferredFocusedComponent(): JComponent? = rootComponent

    override fun getName(): String = "ALPS Diagram"

    override fun setState(state: FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        changeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        changeSupport.removePropertyChangeListener(listener)
    }

    override fun getFile(): VirtualFile = file

    override fun dispose() {
        disposed = true
    }
}
