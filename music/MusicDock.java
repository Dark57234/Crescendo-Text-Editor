package music;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Connects the music player to the editor as a sidebar.
 * The editor only needs to create one MusicDock and call toggle() and shutdown().
 */
public class MusicDock {

    private final JPanel host;                 // the editor panel that receives the sidebar (EAST)
    private final JTextComponent editorText;   // the editor's text, so typing keeps working

    private MusicService service;
    private MusicPlayerPanel sidebar;
    private boolean loading = false;

    public MusicDock(JPanel host, JTextComponent editorText) {
        this.host = host;
        this.editorText = editorText;
    }

    /** Shows or hides the player. The first time, it starts Chromium in the background. */
    public void toggle() {
        if (sidebar != null) {
            sidebar.setVisible(!sidebar.isVisible());
            refresh();
            return;
        }
        if (loading) {
            return;
        }
        loading = true;

        JLabel loadingLabel = new JLabel("Loading music player...", SwingConstants.CENTER);
        loadingLabel.setPreferredSize(new Dimension(MusicPlayerPanel.SIDEBAR_WIDTH, 0));
        loadingLabel.setOpaque(true);
        host.add(loadingLabel, BorderLayout.EAST);
        widenWindowIfNeeded();
        refresh();

        new SwingWorker<MusicService, Void>() {
            @Override
            protected MusicService doInBackground() throws Exception {
                return YouTubeMusicService.create();   // slow: Chromium + server + API key
            }

            @Override
            protected void done() {
                host.remove(loadingLabel);
                loading = false;
                try {
                    service = get();
                    sidebar = new MusicPlayerPanel(service, true);
                    service.enableTyping(editorText);
                    host.add(sidebar, BorderLayout.EAST);
                } catch (Exception ex) {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(host,
                            "Could not start the music player:\n" + cause.getMessage());
                }
                refresh();
            }
        }.execute();
    }

    /** Call this when the editor closes. */
    public void shutdown() {
        if (service != null) {
            service.shutdown();
        }
    }

    private void refresh() {
        host.revalidate();
        host.repaint();
    }

    /** Makes the window wider so the text area doesn't get squeezed by the sidebar. */
    private void widenWindowIfNeeded() {
        Window window = SwingUtilities.getWindowAncestor(host);
        int wanted = 900 + MusicPlayerPanel.SIDEBAR_WIDTH;
        if (window != null && window.getWidth() < wanted) {
            window.setSize(wanted, window.getHeight());
        }
    }
}
