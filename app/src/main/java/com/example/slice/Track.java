package com.example.slice;

// Track helper class to hold useful track info
public class Track {
    String uri;
    String id;
    String playlistUri;
    String name;
    int duration_ms;
    String imageUrl;
    public String artist;

    public Track(String uri, String id, String playlistUri, String name, int duration, String imageUrl) {
        this.uri = uri;
        this.id = id;
        this.playlistUri = playlistUri;
        this.name = name;
        this.duration_ms = duration;
        this.imageUrl = imageUrl;
    }

    public Track() {
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlaylistUri() {
        return playlistUri;
    }

    public void setPlaylistUri(String playlistUri) {
        this.playlistUri = playlistUri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDuration_ms() {
        return duration_ms;
    }

    public void setDuration_ms(int duration) {
        this.duration_ms = duration;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
