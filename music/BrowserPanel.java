package music;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.function.Consumer;

/** The embedded browser that shows the YouTube player. Hides everything JCEF-related. */
public class BrowserPanel {

    private final LocalPlayerServer server = new LocalPlayerServer();
    private final CefApp app;
    private final CefBrowser browser;
    private boolean browserHasFocus = true;          // who owns the keyboard: Chromium or Swing
    private Consumer<String> eventHandler = event -> { };   // receives events from player.html

    /** Starts the local server and Chromium. Slow: do NOT call it on the Swing thread. */
    public BrowserPanel() throws Exception {
        server.start();

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(new File("jcef-bundle"));
        builder.getCefSettings().windowless_rendering_enabled = false;
        // Chromium blocks sound until the user clicks the page; the user already chose the song in our UI
        builder.addJcefArgs("--autoplay-policy=no-user-gesture-required");
        builder.setAppHandler(new MavenCefAppHandlerAdapter() { });
        app = builder.build();

        CefClient client = app.createClient();

        // console.log(...) from the page -> NetBeans Output
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser b, CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                System.out.println("[JS] " + message);
                return false;
            }
        });

        // Keyboard handoff between Chromium and Swing (see allowTyping)
        client.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public void onGotFocus(CefBrowser b) {
                if (browserHasFocus) {
                    return;
                }
                browserHasFocus = true;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                b.setFocus(true);
            }

            @Override
            public void onTakeFocus(CefBrowser b, boolean next) {
                browserHasFocus = false;
            }
        });

        // JS -> Java bridge: player.html calls window.cefQuery({request: "ENDED"}) and it lands here
        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser b, CefFrame frame, long queryId, String request,
                                   boolean persistent, CefQueryCallback callback) {
                // This runs on a Chromium thread, NOT the Swing thread: hop over before doing anything
                SwingUtilities.invokeLater(() -> eventHandler.accept(request));
                callback.success("");
                return true;
            }
        }, true);
        client.addMessageRouter(router);

        browser = client.createBrowser(server.getUrl(), false, false);
    }

    /** The Swing component to put in a window. */
    public Component getComponent() {
        return browser.getUIComponent();
    }

    /** Who gets the player events (ENDED, ERROR 150...). Always called on the Swing thread. */
    public void setEventHandler(Consumer<String> handler) {
        this.eventHandler = handler;
    }

    /** Lets a text field receive the keyboard even though the browser is on screen. */
    public void allowTyping(JTextComponent field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!browserHasFocus) {
                    return;
                }
                browserHasFocus = false;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                field.requestFocus();
            }
        });
    }

    // ---------- Player controls ----------

    public static boolean isValidVideoId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}");
    }

    public void loadVideo(String videoId) {
        if (!isValidVideoId(videoId)) {
            throw new IllegalArgumentException("Invalid YouTube video ID: " + videoId);
        }
        runJs("loadVideo('" + videoId + "')");
    }

    public void pause() {
        runJs("pauseVideo()");
    }

    public void resume() {
        runJs("resumeVideo()");
    }

    public void seekTo(int seconds) {
        runJs("seekTo(" + seconds + ")");
    }

    private void runJs(String code) {
        System.out.println("[JAVA -> JS] " + code);
        browser.executeJavaScript(code, browser.getURL(), 0);
    }

    /** Shuts everything down when the app closes. */
    public void dispose() {
        app.dispose();
        server.stop();
    }
}
