package my.slice.app;

// Helper class to store useful playlist info
public class Playlist {
    String uri;
    String name;
    String id;
    String imageUrl;

    public Playlist(String uri, String name, String id, String imageUrl) {
        this.uri = uri;
        this.name = name;
        this.id = id;
        this.imageUrl = imageUrl;
    }

    public Playlist() {
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
