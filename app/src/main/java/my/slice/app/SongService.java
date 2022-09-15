package my.slice.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Result;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerState;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static my.slice.app.App.CHANNEL_ID;

public class SongService extends Service {
    // Spotify Stuff
    private SpotifyAppRemote mSpotifyAppRemote;
    private static final String CLIENT_ID = "71ea8dc10ab14aea83e374692c3fea85";
    private static final String REDIRECT_URI = "http://127.0.0.1:8000/";
    private static final int REQUEST_CODE = 1337;
    private String token = "";

    // Communicating with main stuff
    private String name;
    private String playlistUri;
    private final IBinder binder = new SongService.LocalBinder();
    public class LocalBinder extends Binder {
        SongService getService() {
            return SongService.this;
        }
    }

    // Fields to run the playlist
    int seconds = 0;
    int duration = 0;
    boolean shown = false;
    boolean isConnecting = false;
    String songUri = "";
    boolean running = true;
    final int TIMEOUT = 5;
    ArrayList<Slice> slices = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("starting the service");

        // Start playing slice
        songUri = intent.getStringExtra("songUri");
        playlistUri = intent.getStringExtra("playlistUri");
        name = intent.getStringExtra("name");
        running = true;
        connect(true);

        // Set up notification
        Intent notificationIntent = new Intent(this, SongService.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("SLICE")
                        .setContentText("Playing " + name)
                        .setSmallIcon(R.drawable.ic_baseline_play_arrow_24)
                        .setContentIntent(pendingIntent)
                        .build();

        // Notification ID cannot be 0.
        startForeground(12, notification);

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public String getURI(){
        return songUri;
    }

    @Override
    public void onDestroy() {
        System.out.println("Slices destroyed");
        running = false;
        super.onDestroy();
    }

    public void connect(boolean restart){
        // Spotify connection
        ConnectionParams connectionParams =
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(true)
                        .build();

        SpotifyAppRemote.connect(this, connectionParams,
                new Connector.ConnectionListener() {

                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        Log.d("PlaylistActivity", "Connected! Yay!");
                        if (restart){
                            System.out.println("Restarting the thread");
                            playSong();
                            new PlaySongThread().start();
                        }
                        // Now you can start interacting with App Remote
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("PlaylistActivity", throwable.getMessage(), throwable);

                        // Something went wrong when attempting to connect! Handle errors here
                    }
                });
    }

    public void playSong(){
        mSpotifyAppRemote.getPlayerApi().play(songUri);
    }

    // Determines if two doubles are close enough. Used to prevent infinite Recursion with the Seekbar.
    public boolean closeEnough(double l, double r){
        double val = l - r;
        if (val < 0) val *= -1;
        return val < 0.5;
    }

    // Retrieve the json file as a String
    public String getSlices() {
        try {
            File file = new File(getApplicationContext().getFilesDir(), "JSON_SLICES");
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            bufferedReader.close();
            String response = stringBuilder.toString();
            return response;
        }
        catch(FileNotFoundException notFound){
            System.out.println("File not found");
            notFound.printStackTrace();
            return "";
        }
        catch(IOException except){
            System.out.println(except.getMessage());
            return "";
        }
    }

    // Check if we are disconnected from spotify and reconnect if so
    private void check(){
        if (!running) return;
        if (!mSpotifyAppRemote.isConnected() && !isConnecting) {
            isConnecting = true;
            System.out.println("Spotify disconnected");
            connect(true);
            running = false;
        }
        else{
            isConnecting = false;
        }
    }

    // Checks if anything is playing in spotify
    private boolean isPlaying(){
        if (!running) return false;
        check();
        CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
        Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
        if (playerStateResult.isSuccessful()) {
            PlayerState playerState = playerStateResult.getData();
            return !playerState.isPaused;
        } else {
            Throwable error = playerStateResult.getError();
            error.printStackTrace();
            System.out.println(error.getMessage());
            if (error.getMessage() == "Result was not delivered on time" && !shown) {
                // Snackbar.make(sliceRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                shown = true;
            }
            return false;
        }
    }

    // Returns the current song uri and updates the seconds global variable
    private String getCurrent(){
        if (!running) return "break";
        check();
        CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
        Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
        if (playerStateResult.isSuccessful()) {
            PlayerState playerState = playerStateResult.getData();
            seconds = (int) playerState.playbackPosition;
            if (playerState.track != null){
                return playerState.track.uri;
            }
        } else {
            Throwable error = playerStateResult.getError();
            error.printStackTrace();
            System.out.println(error.getMessage());
            if (error.getMessage() == "Result was not delivered on time" && !shown) {
                // Snackbar.make(sliceRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                shown = true;
            }

        }
        return "";
    }

    // Thread to play the song
    public class PlaySongThread extends Thread{


        // Check the current song for slices
        @Override
        public void run() {
            // Todo: Make sure the pauses are correct
            if (!running) return;
            String st;
            int iter = 0;
            boolean confirmed = false;

            // Go to 0 seconds on the current song to make sure no slices are skipped and give slice time to catch up (Pause stuff)
            check();
            CallResult<Empty> cr = mSpotifyAppRemote.getPlayerApi().seekTo(0);
            Result<Empty> r = cr.await(1, TimeUnit.SECONDS);
            if (r.isSuccessful()){
                System.out.println("Went to 0 seconds correctly");
            }
            else{
                System.out.println(r.getErrorMessage());
                if (r.getErrorMessage() == "Result was not delivered on time" && !shown) {
                    // Snackbar.make(sliceRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }
                r.getError().printStackTrace();
                System.out.println("Was not able to go to 0");
            }

            System.out.println(isPlaying());
            do{

                load();
                if (!confirmed && mSpotifyAppRemote.isConnected()){
                    // Snackbar.make(sliceRecycler, "Playing this song!", Snackbar.LENGTH_SHORT).show();
                    confirmed = true;
                }

                double sec = seconds/1000.0;
                st = getCurrent();
                boolean remaining = false;
                if (slices.size() == 0) remaining = true;
                for(Slice s : slices){
                    int first = s.times[0];
                    int second = s.times[1];

                    double f = first/1000.0;
                    double se = second/1000.0;

                    // Currently in a Slice
                    if ((seconds >= first || closeEnough(f, sec)) && (seconds <= second || closeEnough(se, sec) || second == duration)){
                        remaining = true;
                        break;
                    }

                    // Not currently in a slice and there is one in the future
                    else if (seconds < first && !closeEnough(f, sec)){
                        remaining = true;
                        check();
                        CallResult <Empty> callResult = mSpotifyAppRemote.getPlayerApi().seekTo(first);
                        Result<Empty> result = callResult.await(1, TimeUnit.SECONDS);
                        if (result.isSuccessful()){
                            System.out.println("Jumped to next slice");
                        }
                        else{
                            result.getError().printStackTrace();
                            System.out.println("Did not jump to next slice");
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            pause();
                            return;
                        }
                        break;
                    }
                }

                // Skipping the song
                if (!remaining){
                    System.out.println("Skipping");
                    check();
                    mSpotifyAppRemote.getPlayerApi().skipNext();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        pause();
                        return;
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    pause();
                    return;
                }
                System.out.println("Listening to " + st);
                System.out.println("Remaining is " + remaining);
                System.out.println(sec);

                if (st.equals("break"))break;

                // iter is here to make sure this thread doesn't end prematurely because it takes a second for spotify api to recognize what we are listening to
                iter++;
//                AlarmManager alarmManager =
//                        (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
//                Intent intent = new Intent(getApplicationContext(), AlertReciever.class);
//                PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), iter, intent, 0);
//                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 3000, pendingIntent);
            }
            while(running && (st.equals(songUri)) || st.equals("") || iter < 10 || !mSpotifyAppRemote.isConnected());
            System.out.println("Loop over");

            pause();
        }

        public void load() {
            try {
                String json = getSlices();
                JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
                System.out.println(jsonObject.toString());
                if (jsonObject.has(playlistUri)) {
                    JSONObject playlist = (JSONObject) jsonObject.get(playlistUri);
                    if (playlist.has(songUri)) {
                        JSONObject song = (JSONObject) playlist.get(songUri);
                        slices.clear();
                        for (int i = 0; i < song.names().length(); i += 2) {
                            String name = song.names().getString(i);
                            String name2 = song.names().getString(i + 1);
                            slices.add(new Slice(new int[]{song.getInt(name), song.getInt(name2)}, i / 2, songUri));
                            System.out.println(Arrays.toString(slices.get(i / 2).times));
                        }

                    }
                }

            }
            catch(JSONException e){
                System.out.println("JSONEException");
                e.printStackTrace();
            }
        }

        private void pause(){
            running = false;
            stopForeground(true);
        }



    }
}
