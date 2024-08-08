package com.smallcloud.refactai.panes.sharedchat.browser

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.*
import com.intellij.util.messages.MessageBus
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.panes.sharedchat.Events
import com.smallcloud.refactai.settings.AppSettingsState
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent

class ChatWebView(val messageHandler:  (event: Events.FromChat) -> Unit): Disposable {
    private val jsPoolSize = "200"

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
    }

    fun setStyle() {
        val isDarkMode = UIUtil.isUnderDarcula()
        val mode = if (isDarkMode) { "dark" } else { "light" }
        val bodyClass = if (isDarkMode) { "vscode-dark" } else { "vscode-light" }
        val backgroundColour = UIUtil.getPanelBackground()
        val red = backgroundColour.red
        val green = backgroundColour.green
        val blue = backgroundColour.blue
        this.webView.executeJavaScriptAsync("""document.body.style.setProperty("background-color", "rgb($red, $green, $blue");""")
        this.webView.executeJavaScriptAsync("""document.body.class = "$bodyClass";""")
        this.webView.executeJavaScriptAsync("""document.documentElement.className = "$mode";""")

    }

    val webView by lazy {
        val osName = System.getProperty("os.name").lowercase()
        val useOsr = when {
            osName.contains("mac") || osName.contains("darwin") -> false
            osName.contains("win") -> false
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> true
            else -> true
        }

        val browser = JBCefBrowser
            .createBuilder()
            .setUrl("http://refactai/index.html")
            .setOffScreenRendering(useOsr)
            .build()

        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            jsPoolSize,
        )
        browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

        CefApp.getInstance().registerSchemeHandlerFactory("http", "refactai", RequestHandlerFactory())

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        addMessageHandler(myJSQueryOpenInBrowser)

        val myJSQueryOpenInBrowserRedirectHyperlink = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        myJSQueryOpenInBrowserRedirectHyperlink.addHandler { href ->
            if(href.isNotEmpty()) {
                BrowserUtil.browse(href)
            }
            null
        }

        var installedScript = false
        var setupReact = false

        browser.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if(isLoading) {
                    return;
                }

                if(!installedScript) {
                    installedScript = setUpJavaScriptMessageBus(browser, myJSQueryOpenInBrowser)
                }

                if (!setupReact) {
                    setupReact = true
                    setUpReact(browser)
                }

                setUpJavaScriptMessageBusRedirectHyperlink(browser, myJSQueryOpenInBrowserRedirectHyperlink)
                setStyle()
            }

        }, browser.cefBrowser)

        val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
        messageBus
            .connect(PluginState.instance)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun apiKeyChanged(newApiKey: String?) {
                    setupReact = false
                    installedScript = false
                    browser.cefBrowser.reload()
                    addMessageHandler(myJSQueryOpenInBrowser)
                }
            })

        browser.createImmediately()

        browser
    }

    fun addMessageHandler(myJSQueryOpenInBrowser: JBCefJSQuery) {
        myJSQueryOpenInBrowser.addHandler { msg ->
            val event = Events.parse(msg)

            if(event != null) {
                messageHandler(event)
            }
            null
        }
    }

    fun setUpReact(browser: CefBrowser) {
        val settings = AppSettingsState.instance
        val script = """
        const options = {
          host: "jetbrains",
          tabbed: false,
          themeProps: {
            accentColor: "gray",
            scaling: "90%",
            hasBackground: false
          },
          features: {
            vecdb: ${settings.vecdbIsEnabled},
            ast: ${settings.astIsEnabled},
          },
          apiKey: "${if (settings.apiKey == null) "" else "${settings.apiKey}" }",
          addressURL: "${if (settings.userInferenceUri == null) "" else "${settings.userInferenceUri}" }"
        };
        window.__INITIAL_STATE = options;
        
        function loadChatJs() {
            const element = document.getElementById("refact-chat");
            RefactChat.render(element, options);
        };
        
        const script = document.createElement("script");
        script.onload = loadChatJs;
        script.src = "http://refactai/dist/chat/index.umd.cjs";
        document.head.appendChild(script);
        """.trimIndent()
        browser.executeJavaScript(script, browser.url, 0)
    }

    fun setUpJavaScriptMessageBusRedirectHyperlink(browser: CefBrowser?, myJSQueryOpenInBrowser: JBCefJSQuery) {
        val script = """window.openLink = function(href) {
             ${myJSQueryOpenInBrowser.inject("href")}
        }
        document.addEventListener('click', function(event) { 
            if (event.target.tagName.toLowerCase() === 'a') { 
                event.preventDefault(); 
                window.openLink(event.target.href); 
            } 
        });""".trimIndent()
        browser?.executeJavaScript(script, browser.url, 0)
    }

    fun setUpJavaScriptMessageBus(browser: CefBrowser?, myJSQueryOpenInBrowser: JBCefJSQuery): Boolean {

        val script = """window.postIntellijMessage = function(event) {
             const msg = JSON.stringify(event);
             ${myJSQueryOpenInBrowser.inject("msg")}
        }""".trimIndent()
        if(browser != null) {
            browser.executeJavaScript(script, browser.url, 0);
            return true
        }
        return false
    }

    fun postMessage(message: Events.ToChat?) {
        if(message != null) {
            val json = Events.stringify(message)
            this.postMessage(json)
        }
    }

    fun postMessage(message: String) {
        // println("postMessage: $message")
        val script = """window.postMessage($message, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

    fun getComponent(): JComponent {
        return webView.component
    }

    override fun dispose() {
        this.webView.dispose()
    }


}