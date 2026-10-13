package music;

/**
 * Receives playback events from a {@link MusicService}.
 * The service always calls these methods on the Swing thread, so it is safe to update the UI here.
 */
public interface PlaybackListener {

    /** A song started loading. {@code index} is its position in the queue. */
    void onTrackStarted(Track track, int index);

    /** A song could not be played and was skipped. */
    void onTrackFailed(Track track, String reason);

    /** There are no more playable songs in the queue. */
    void onQueueFinished();
}
