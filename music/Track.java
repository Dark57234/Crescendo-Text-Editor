package music;

/**
 * One song. It does not depend on where the music comes from:
 * for YouTube, {@code id} is the video ID; for an offline source it could be a file path.
 */
public record Track(String id, String title, String artist, String imageUrl) {

    /** This is the text the results list shows for each song. */
    @Override
    public String toString() {
        return title + " - " + artist;
    }
}
