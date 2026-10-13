package music;

import java.awt.Component;
import java.io.IOException;
import java.util.List;
import javax.swing.text.JTextComponent;

/**
 * What any music source must be able to do.
 * The UI ({@link MusicPlayerPanel}) only talks to this interface, so a source
 * (YouTube today, an offline player later) can be swapped without touching the UI.
 */
public interface MusicService {

    /** Finds songs. May be slow: never call it on the Swing thread. */
    List<Track> search(String query) throws IOException, InterruptedException;

    /** More results for the last search. Default: this source has no more pages. */
    default List<Track> searchMore() throws IOException, InterruptedException {
        return List.of();
    }

    /** True if {@link #searchMore()} can return something. */
    default boolean hasMoreResults() {
        return false;
    }

    /** Replaces the queue with {@code tracks} and starts playing at {@code startIndex}. */
    void playQueue(List<Track> tracks, int startIndex);

    /** Adds songs to the end of the queue without stopping what is playing. */
    void addToQueue(List<Track> tracks);

    void pause();

    void resume();

    /** Skips to the next playable song in the queue. */
    void next();

    /** Goes back to the previous playable song in the queue. */
    void previous();

    void seekTo(int seconds);

    /** The song playing now, or {@code null} if nothing is playing. */
    Track getCurrentTrack();

    /** True if this song already failed and should be shown as unavailable. */
    boolean isUnavailable(Track track);

    void setPlaybackListener(PlaybackListener listener);

    /** What to show on screen for this source (for YouTube: the video player). */
    Component getView();

    /**
     * Lets a text field receive the keyboard while this source is on screen.
     * Default: nothing to do. YouTube needs it because of the embedded browser.
     */
    default void enableTyping(JTextComponent field) {
    }

    /** A web page where the song can be played outside the app, or {@code null} if there is none. */
    default String getExternalUrl(Track track) {
        return null;
    }

    /** Frees everything (browser, server...) when the app closes. */
    void shutdown();
}
