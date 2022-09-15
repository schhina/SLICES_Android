package my.slice.app;

// Helper class to store useful slice info
public class Slice {
    int [] times;
    int number;
    String songUri;
    boolean saved = false;

    public Slice(int[] times, int number, String songUri) {
        this.times = times;
        this.number = number;
        this.songUri = songUri;
        saved = true;
    }

    public Slice() {
        times = new int[]{0, -1};
        number = -1;
        songUri = "";
    }

    public int[] getTimes() {
        return times;
    }

    public void setTimes(int[] times) {
        this.times = times;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getSongUri() {
        return songUri;
    }

    public void setSongUri(String songUri) {
        this.songUri = songUri;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }
}
