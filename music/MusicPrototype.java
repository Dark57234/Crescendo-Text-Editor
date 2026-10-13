package music;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Stand-alone test window for the music player (before integrating it into the editor). */
public class MusicPrototype {

    public static void main(String[] args) throws Exception {
        // The slow part (Chromium + server + API key), outside the Swing thread
        MusicService service = YouTubeMusicService.create();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Crescendo Music (prototype)");
            frame.setSize(1150, 600);
            frame.setContentPane(new MusicPlayerPanel(service));

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    service.shutdown();
                    frame.dispose();
                }
            });
            frame.setVisible(true);
        });
    }
}
