package music;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;   // explicit on purpose: java.awt also has a class called List

/**
 * Search box + results + video + controls. Works with any {@link MusicService}.
 * Two layouts: wide (stand-alone window) and sidebar (narrow, next to the editor).
 */
public class MusicPlayerPanel extends JPanel implements PlaybackListener {

    public static final int SIDEBAR_WIDTH = 360;

    private final MusicService service;

    private final JTextField searchField = new JTextField(20);
    private final JButton searchButton = new JButton("Search");
    private final DefaultListModel<Track> resultsModel = new DefaultListModel<>();
    private final JList<Track> resultsList = new JList<>(resultsModel);
    private final JTextArea statusArea = new JTextArea("Type something and press Search");

    private final JButton previousButton = new JButton("Previous");
    private final JButton playButton = new JButton("Play");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton nextButton = new JButton("Next");
    private final JButton openButton = new JButton("Open in browser");
    private final JButton loadMoreButton = new JButton("Load more results");

    // True when the queue being played is this result list (so new pages extend it)
    private boolean queueIsResultList = false;

    /** Wide layout (stand-alone window). */
    public MusicPlayerPanel(MusicService service) {
        this(service, false);
    }

    /** @param sidebar true for the narrow layout used next to the editor */
    public MusicPlayerPanel(MusicService service, boolean sidebar) {
        super(new BorderLayout(8, 8));
        this.service = service;
        service.setPlaybackListener(this);   // this panel hears the playback events

        setupComponents();
        if (sidebar) {
            buildSidebarLayout();
        } else {
            buildWideLayout();
        }
        setupEvents();
        service.enableTyping(searchField);
    }

    private void setupComponents() {
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setCellRenderer(new TrackCellRenderer());

        // A text area instead of a label so long messages wrap to a second line
        statusArea.setEditable(false);
        statusArea.setFocusable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setOpaque(false);
        statusArea.setRows(2);
        statusArea.setFont(UIManager.getFont("Label.font"));

        loadMoreButton.setEnabled(false);   // nothing to load until the first search
    }

    /** The result list with the "Load more" button under it. */
    private JPanel createResultsArea() {
        JPanel area = new JPanel(new BorderLayout(0, 4));
        area.add(new JScrollPane(resultsList), BorderLayout.CENTER);
        area.add(loadMoreButton, BorderLayout.SOUTH);
        return area;
    }

    /** Wide: results on the left, video in the middle, controls at the bottom. */
    private void buildWideLayout() {
        JPanel top = new JPanel(new BorderLayout(5, 0));
        top.add(searchField, BorderLayout.CENTER);
        top.add(searchButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel resultsArea = createResultsArea();
        resultsArea.setPreferredSize(new Dimension(340, 0));
        add(resultsArea, BorderLayout.WEST);

        Component video = service.getView();
        video.setMinimumSize(new Dimension(200, 200));   // YouTube rule: at least 200x200
        add(video, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(previousButton);
        bottom.add(playButton);
        bottom.add(pauseButton);
        bottom.add(nextButton);
        bottom.add(openButton);
        statusArea.setColumns(40);
        bottom.add(statusArea);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Sidebar: everything stacked top to bottom in a narrow column. */
    private void buildSidebarLayout() {
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JPanel searchRow = new JPanel(new BorderLayout(5, 0));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);

        Component video = service.getView();
        video.setMinimumSize(new Dimension(200, 200));   // YouTube rule: at least 200x200
        video.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 220));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(searchRow, BorderLayout.NORTH);
        top.add(video, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        add(createResultsArea(), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(1, 4, 4, 0));
        controls.add(previousButton);
        controls.add(playButton);
        controls.add(pauseButton);
        controls.add(nextButton);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(openButton, BorderLayout.CENTER);
        bottom.add(statusArea, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private void setupEvents() {
        searchButton.addActionListener(e -> doSearch());
        searchField.addActionListener(e -> doSearch());   // Enter also searches
        previousButton.addActionListener(e -> service.previous());
        playButton.addActionListener(e -> service.resume());
        pauseButton.addActionListener(e -> service.pause());
        nextButton.addActionListener(e -> service.next());
        openButton.addActionListener(e -> openInBrowser());
        loadMoreButton.addActionListener(e -> loadMore());

        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    playSelected();
                }
            }
        });
    }

    private void setStatus(String text) {
        statusArea.setText(text);
    }

    /** Double click: the whole result list becomes the queue, starting at the clicked song. */
    private void playSelected() {
        int index = resultsList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < resultsModel.size(); i++) {
            tracks.add(resultsModel.get(i));
        }
        service.playQueue(tracks, index);
        queueIsResultList = true;
    }

    /** Opens the selected song (or the current one) in the user's normal web browser. */
    private void openInBrowser() {
        Track track = resultsList.getSelectedValue();
        if (track == null) {
            track = service.getCurrentTrack();
        }
        if (track == null) {
            setStatus("Select a song first.");
            return;
        }
        String url = service.getExternalUrl(track);
        if (url == null || !Desktop.isDesktopSupported()) {
            setStatus("This song can't be opened outside the app.");
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
            service.pause();   // don't play two things at once
            setStatus("Opened \"" + track.title() + "\" in your browser.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open the browser:\n" + ex.getMessage());
        }
    }

    private void doSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty() || !searchButton.isEnabled()) {
            return;   // empty, or a search is already running
        }

        searchButton.setEnabled(false);
        loadMoreButton.setEnabled(false);
        setStatus("Searching \"" + query + "\"...");

        new SwingWorker<List<Track>, Void>() {
            @Override
            protected List<Track> doInBackground() throws Exception {
                return service.search(query);   // runs on a background thread
            }

            @Override
            protected void done() {             // back on the Swing thread
                searchButton.setEnabled(true);
                try {
                    List<Track> results = get();
                    resultsModel.clear();
                    queueIsResultList = false;   // the queue still holds the old list
                    for (Track t : results) {
                        resultsModel.addElement(t);
                    }
                    setStatus(results.size() + " results. Double click to play.");
                    loadMoreButton.setEnabled(service.hasMoreResults());
                } catch (Exception ex) {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    setStatus("Search failed.");
                    JOptionPane.showMessageDialog(MusicPlayerPanel.this,
                            "Could not search:\n" + cause.getMessage());
                }
            }
        }.execute();
    }

    /** Asks for the next page of the same search and adds it to the end of the list. */
    private void loadMore() {
        if (!loadMoreButton.isEnabled()) {
            return;
        }
        loadMoreButton.setEnabled(false);
        searchButton.setEnabled(false);
        setStatus("Loading more results...");

        new SwingWorker<List<Track>, Void>() {
            @Override
            protected List<Track> doInBackground() throws Exception {
                return service.searchMore();
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                try {
                    List<Track> more = get();
                    for (Track t : more) {
                        resultsModel.addElement(t);
                    }
                    if (queueIsResultList) {
                        service.addToQueue(more);   // keep playing into the new songs
                    }
                    setStatus(more.size() + " more results (" + resultsModel.size() + " total).");
                } catch (Exception ex) {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    setStatus("Could not load more results.");
                    JOptionPane.showMessageDialog(MusicPlayerPanel.this,
                            "Could not load more:\n" + cause.getMessage());
                }
                loadMoreButton.setEnabled(service.hasMoreResults());
            }
        }.execute();
    }

    // ---------- PlaybackListener: the service tells us what happened ----------

    @Override
    public void onTrackStarted(Track track, int index) {
        setStatus("Now playing: " + track.title());
        // Only move the selection if the list still shows the same songs as the queue
        if (index < resultsModel.size() && resultsModel.get(index).equals(track)) {
            resultsList.setSelectedIndex(index);
            resultsList.ensureIndexIsVisible(index);
        }
        resultsList.repaint();
    }

    @Override
    public void onTrackFailed(Track track, String reason) {
        setStatus("Skipped \"" + track.title() + "\": " + reason
                + ". Select it and press Open in browser.");
        resultsList.repaint();
    }

    @Override
    public void onQueueFinished() {
        setStatus("End of the list.");
    }

    /** Draws each row: gray for unavailable songs, a marker for the one playing now. */
    private class TrackCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            Track track = (Track) value;
            if (service.isUnavailable(track)) {
                setText(track + "  (unavailable)");
                if (!isSelected) {
                    setForeground(Color.GRAY);
                }
            } else if (track.equals(service.getCurrentTrack())) {
                setText(">  " + track);
                if (!isSelected) {
                    setForeground(new Color(0, 90, 170));
                }
            }
            return this;
        }
    }
}
