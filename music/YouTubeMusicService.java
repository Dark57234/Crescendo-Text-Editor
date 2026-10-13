package music;

import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.text.JTextComponent;

/** {@link MusicService} backed by YouTube: search with the Data API, play with the embedded player. */
public class YouTubeMusicService implements MusicService {

    private final BrowserPanel player;
    private final YouTubeSearchClient searchClient;

    private final List<Track> queue = new ArrayList<>();
    private final Set<String> unavailableIds = new HashSet<>();   // songs that already failed
    private int currentIndex = -1;                                 // -1 = nothing playing
    private PlaybackListener listener;

    public YouTubeMusicService(BrowserPanel player, YouTubeSearchClient searchClient) {
        this.player = player;
        this.searchClient = searchClient;
        // When player.html sends an event (ENDED, ERROR...), call handlePlayerEvent
        player.setEventHandler(this::handlePlayerEvent);
    }

    /** Builds everything this service needs. Slow: do NOT call it on the Swing thread. */
    public static YouTubeMusicService create() throws Exception {
        return new YouTubeMusicService(new BrowserPanel(), YouTubeSearchClient.fromConfigFile());
    }

    // ---------- MusicService ----------

    @Override
    public List<Track> search(String query) throws IOException, InterruptedException {
        return searchClient.search(query);
    }

    @Override
    public List<Track> searchMore() throws IOException, InterruptedException {
        return searchClient.searchMore();
    }

    @Override
    public boolean hasMoreResults() {
        return searchClient.hasMore();
    }

    @Override
    public void playQueue(List<Track> tracks, int startIndex) {
        queue.clear();
        queue.addAll(tracks);
        playAt(startIndex);
    }

    @Override
    public void addToQueue(List<Track> tracks) {
        queue.addAll(tracks);
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void resume() {
        player.resume();
    }

    @Override
    public void next() {
        int index = findPlayable(currentIndex + 1, +1);
        if (index == -1) {
            if (listener != null) {
                listener.onQueueFinished();
            }
        } else {
            playAt(index);
        }
    }

    @Override
    public void previous() {
        int index = findPlayable(currentIndex - 1, -1);
        if (index != -1) {
            playAt(index);
        }
    }

    @Override
    public void seekTo(int seconds) {
        player.seekTo(seconds);
    }

    @Override
    public Track getCurrentTrack() {
        if (currentIndex < 0 || currentIndex >= queue.size()) {
            return null;
        }
        return queue.get(currentIndex);
    }

    @Override
    public boolean isUnavailable(Track track) {
        return unavailableIds.contains(track.id());
    }

    @Override
    public void setPlaybackListener(PlaybackListener listener) {
        this.listener = listener;
    }

    @Override
    public Component getView() {
        return player.getComponent();
    }

    @Override
    public void enableTyping(JTextComponent field) {
        player.allowTyping(field);
    }

    @Override
    public String getExternalUrl(Track track) {
        return "https://www.youtube.com/watch?v=" + track.id();
    }

    @Override
    public void shutdown() {
        player.dispose();
    }

    // ---------- Internal logic ----------

    private void playAt(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        currentIndex = index;
        Track track = queue.get(index);
        player.loadVideo(track.id());
        if (listener != null) {
            listener.onTrackStarted(track, index);
        }
    }

    /**
     * Walks the queue from {@code start} in steps of {@code step} (+1 forward, -1 backward)
     * and returns the first song that has not failed, or -1 if there is none.
     */
    private int findPlayable(int start, int step) {
        for (int i = start; i >= 0 && i < queue.size(); i += step) {
            if (!unavailableIds.contains(queue.get(i).id())) {
                return i;
            }
        }
        return -1;
    }

    /** Events sent by player.html: READY, PLAYING, PAUSED, ENDED, "ERROR <code>". */
    private void handlePlayerEvent(String event) {
        if (event.equals("ENDED")) {
            next();                                    // song finished: play the next one
        } else if (event.startsWith("ERROR")) {
            Track failed = getCurrentTrack();
            if (failed == null) {
                return;
            }
            unavailableIds.add(failed.id());
            String code = event.substring("ERROR".length()).trim();
            if (listener != null) {
                listener.onTrackFailed(failed, describeError(code));
            }
            next();                                    // skip it automatically
        }
    }

    /** Turns YouTube's error code into a message a user can understand. */
    private static String describeError(String code) {
        return switch (code) {
            case "101", "150" -> "the owner does not allow playback outside YouTube";
            case "100" -> "the video was removed or is private";
            case "5" -> "this format is not supported by the embedded browser";
            case "2" -> "invalid video ID";
            default -> "playback error " + code;
        };
    }
}
