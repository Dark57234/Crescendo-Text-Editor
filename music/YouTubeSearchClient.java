package music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Talks to the YouTube Data API v3: searches songs and keeps only the ones that can play embedded. */
public class YouTubeSearchClient {

    private static final String SEARCH_URL = "https://www.googleapis.com/youtube/v3/search";
    private static final String VIDEOS_URL = "https://www.googleapis.com/youtube/v3/videos";

    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    // Pages already downloaded: don't spend quota repeating the same one
    private final Map<String, Page> cache = new HashMap<>();

    // Remembered so searchMore() knows what to continue
    private String lastQuery;
    private String nextPageToken;

    /** One page of results plus the token YouTube gives us to ask for the next page. */
    private record Page(List<Track> tracks, String nextPageToken) {
    }

    public YouTubeSearchClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /** Reads the key from youtube-api.properties (project root). That file is never committed. */
    public static YouTubeSearchClient fromConfigFile() throws IOException {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream("youtube-api.properties")) {
            props.load(in);
        }
        String key = props.getProperty("YT_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IOException("YT_API_KEY is missing in youtube-api.properties");
        }
        return new YouTubeSearchClient(key.trim());
    }

    /** Finds playable songs (first page). Costs 100 + 1 quota units (0 if it comes from the cache). */
    public List<Track> search(String query) throws IOException, InterruptedException {
        lastQuery = query.trim();
        nextPageToken = null;   // forget the pages of the previous search
        return loadPage(null);
    }

    /** The next page of the last search. Costs another 100 + 1 units. */
    public List<Track> searchMore() throws IOException, InterruptedException {
        if (!hasMore()) {
            return List.of();
        }
        return loadPage(nextPageToken);
    }

    /** True if YouTube said there are more pages for the last search. */
    public boolean hasMore() {
        return lastQuery != null && nextPageToken != null;
    }

    private List<Track> loadPage(String pageToken) throws IOException, InterruptedException {
        String cacheKey = lastQuery.toLowerCase() + "|" + pageToken;
        Page page = cache.get(cacheKey);
        if (page != null) {
            System.out.println("(from cache, 0 units)");
        } else {
            page = fetchPage(lastQuery, pageToken);
            cache.put(cacheKey, page);
        }
        nextPageToken = page.nextPageToken();
        return page.tracks();
    }

    private Page fetchPage(String query, String pageToken) throws IOException, InterruptedException {
        String url = SEARCH_URL
                + "?part=snippet"
                + "&type=video"
                + "&videoCategoryId=10"          // 10 = Music
                + "&videoEmbeddable=true"
                + "&videoSyndicated=true"        // can be played outside youtube.com
                + "&regionCode=US"
                + "&maxResults=15"               // ask for more because the filter drops some
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + (pageToken != null ? "&pageToken=" + pageToken : "")
                + "&key=" + apiKey;

        JsonObject root = getJson(url);
        List<Track> candidates = parseSearch(root);             // step 1: search (100 units)
        List<Track> results = filterPlayable(candidates);       // step 2: verify (1 unit)
        return new Page(results, str(root, "nextPageToken", null));
    }

    /** Sends the HTTP request and returns the JSON. Written once, used twice. */
    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("YouTube answered " + response.statusCode() + ": " + response.body());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Turns the search.list answer into Tracks (first filter: fast but not always accurate). */
    private List<Track> parseSearch(JsonObject root) {
        List<Track> results = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("items")) {
            JsonObject item = element.getAsJsonObject();
            JsonObject snippet = item.getAsJsonObject("snippet");
            String videoId = str(item.getAsJsonObject("id"), "videoId", null);

            if (videoId == null || snippet == null) {
                continue;   // incomplete result: skip it
            }
            if (!str(snippet, "liveBroadcastContent", "none").equals("none")) {
                continue;   // live or upcoming stream
            }

            String title = unescape(str(snippet, "title", "(no title)"));
            String channel = unescape(str(snippet, "channelTitle", "(unknown channel)"));
            results.add(new Track(videoId, title, channel, thumbnailUrl(snippet)));
        }
        return results;
    }

    /** Asks videos.list for the CURRENT state of each video and keeps only the playable ones. */
    private List<Track> filterPlayable(List<Track> tracks) throws IOException, InterruptedException {
        if (tracks.isEmpty()) {
            return tracks;
        }

        // Join all the IDs into one: "id1,id2,id3"
        List<String> ids = new ArrayList<>();
        for (Track t : tracks) {
            ids.add(t.id());
        }
        String url = VIDEOS_URL
                + "?part=status,contentDetails"
                + "&id=" + String.join(",", ids)
                + "&key=" + apiKey;

        Set<String> playable = new HashSet<>();
        for (JsonElement element : getJson(url).getAsJsonArray("items")) {
            JsonObject video = element.getAsJsonObject();
            JsonObject status = video.getAsJsonObject("status");
            JsonObject details = video.getAsJsonObject("contentDetails");
            String id = str(video, "id", null);
            if (id == null || status == null || details == null) {
                continue;   // missing data: don't risk it
            }

            boolean embeddable = status.has("embeddable") && status.get("embeddable").getAsBoolean();
            boolean isLive = str(details, "duration", "P0D").equals("P0D");   // live streams have no duration
            boolean ageRestricted = details.has("contentRating")
                    && details.getAsJsonObject("contentRating").has("ytRating");
            boolean blockedInUS = isBlockedIn(details, "US");

            if (embeddable && !isLive && !ageRestricted && !blockedInUS) {
                playable.add(id);
            } else {
                System.out.println("Dropped " + id
                        + (isLive ? " [live]" : "")
                        + (!embeddable ? " [no embed]" : "")
                        + (ageRestricted ? " [age]" : "")
                        + (blockedInUS ? " [blocked in US]" : ""));
            }
        }

        // Keep the original search order
        List<Track> result = new ArrayList<>();
        for (Track t : tracks) {
            if (playable.contains(t.id())) {
                result.add(t);
            }
        }
        return result;
    }

    /** Is the video blocked in that country? */
    private static boolean isBlockedIn(JsonObject details, String country) {
        if (!details.has("regionRestriction")) {
            return false;
        }
        JsonObject rr = details.getAsJsonObject("regionRestriction");
        if (rr.has("blocked")) {
            for (JsonElement c : rr.getAsJsonArray("blocked")) {
                if (c.getAsString().equals(country)) {
                    return true;
                }
            }
        }
        if (rr.has("allowed")) {
            for (JsonElement c : rr.getAsJsonArray("allowed")) {
                if (c.getAsString().equals(country)) {
                    return false;
                }
            }
            return true;   // there is an allow-list and this country is not on it
        }
        return false;
    }

    /** YouTube sends titles with HTML codes (&amp; &#39;...). Turn them back into normal text. */
    private static String unescape(String s) {
        return s.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    /** Reads a text field from the JSON. If it is missing, returns 'fallback' instead of crashing. */
    private static String str(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsString();
    }

    private static String thumbnailUrl(JsonObject snippet) {
        if (!snippet.has("thumbnails")) {
            return "";
        }
        JsonObject thumbs = snippet.getAsJsonObject("thumbnails");
        if (!thumbs.has("default")) {
            return "";
        }
        return str(thumbs.getAsJsonObject("default"), "url", "");
    }
}
