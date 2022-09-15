package my.slice.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Result;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static my.slice.app.App.CHANNEL_ID;

public class PlaylistService extends Service {
    // Spotify Stuff
    private SpotifyAppRemote mSpotifyAppRemote;
    private static final String CLIENT_ID = "71ea8dc10ab14aea83e374692c3fea85";
    private static final String REDIRECT_URI = "http://127.0.0.1:8000/";
    private static final int REQUEST_CODE = 1337;
    private String token = "";

    // Communicating with main stuff
    private String name;
    private String playlistUri;
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        PlaylistService getService() {
            return PlaylistService.this;
        }
    }

    // Fields to run the playlist
    int seconds = 0;
    int duration = 0;
    boolean shown = false;
    boolean isConnecting = false;
    String trackuri = "";
    boolean running = true;
    final int TIMEOUT = 5;

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("starting the service");

        // Start playing slice
        playlistUri = intent.getStringExtra("playlistUri");
        name = intent.getStringExtra("name");
        connect(true);

        // Set up notification
        Intent notificationIntent = new Intent(this, PlaylistService.class);
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
        return playlistUri;
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
                        if (restart) {
                            System.out.println("restarting this thread");
                            play(true);
                            new RunPlaylistThread().start();
//                            RunPlaylistThread thread = new RunPlaylistThread(playlistUri, mSpotifyAppRemote);
//                            thread.run();
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


    // Reconnect to spotify if disconnected
    private void check(){
        if (running) {
            if (!mSpotifyAppRemote.isConnected() && !isConnecting) {
                System.out.println("\n\n\n DISCONNECTED FROM SPOTIFY \n\n\n");
                System.out.println("Is connecting is " + isConnecting);
                System.out.println("Remote is connected is " + mSpotifyAppRemote.isConnected());
                isConnecting = true;
                connect(true);
                running = false;
                System.out.println("Ending this thread");
                // this.interrupt();
            } else {
                isConnecting = false;
            }
        }
    }

    // Check if spotify is playing anything
    private boolean isPlaying(){
        if (running) {
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
                if (error.getMessage() == ("Result was not delivered on time.") && !shown) {
                    // Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    System.out.println("Wasn't delivered on time");
                    shown = true;
                }

                return false;
            }
        }
        return false;
    }

    // Retrieve the current song
    private String getCurrent(){
        if(running) {
            check();
            Subscription<PlayerContext> sub = mSpotifyAppRemote.getPlayerApi().subscribeToPlayerContext();
            Result<PlayerContext> res = sub.await(TIMEOUT, TimeUnit.SECONDS);
            String uri = "";
            if (res.isSuccessful()) {
                PlayerContext player = res.getData();
                uri = player.uri;

            } else {
                Throwable error = res.getError();
                error.printStackTrace();
                System.out.println(error.getMessage());
                if (error.getMessage() == ("Result was not delivered on time.") && !shown) {
                    // Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }
            }

            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(TIMEOUT, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {

                PlayerState playerState = playerStateResult.getData();
                seconds = (int) playerState.playbackPosition;
                if (playerState.track != null) {
                    duration = (int) playerState.track.duration;
                    trackuri = playerState.track.uri;
                }
            } else {
                Throwable e = playerStateResult.getError();
                e.printStackTrace();
                System.out.println(e.getMessage());
                if (e.getMessage() == ("Result was not delivered on time.") && !shown) {
                    // Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }


            }
            return uri;
        }
        return "break";
    }

    public void terminate(){
        running = false;
    }


    private void play(boolean restart){
        mSpotifyAppRemote.getPlayerApi().play(playlistUri);
    }

    public boolean closeEnough(double l, double r){
        double val = l - r;
        if (val < 0) val *= -1;
        return val < 0.5;
    }

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

    // Thread class that runs the playlist
    class RunPlaylistThread extends Thread{

        @Override
        public void run(){

            if (!running) return;

            System.out.println("Starting a new playlist thread");

            String curr;
            int iter = 0;
            HashMap<String, ArrayList<int[]>> slices;
            boolean confirmed = false;

            // TODO:: Make sure all the timing is right for pauses
            // TODO:: If start and end time for slice are the same, skip it

            // Move current song to 0 seconds to let the spotify api catch up and make sure we don't skip any slices
            CallResult<Empty> cr = mSpotifyAppRemote.getPlayerApi().seekTo(0);
            Result<Empty> r = cr.await(1, TimeUnit.SECONDS);
            if (r.isSuccessful()){
                System.out.println("Went to 0 seconds correctly");
            }
            else{
                System.out.println(r.getErrorMessage());
                String str = r.getErrorMessage();
                if (str.equals("Result was not delivered on time.") && !shown) {
                    // Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }
                r.getError().printStackTrace();
                System.out.println("Was not able to go to 0");
            }

            System.out.println(isPlaying());

            // Do slice stuff
            do{

                if (!confirmed && mSpotifyAppRemote.isConnected()){
                    // Snackbar.make(songRecycler, "Playing this playlist!", Snackbar.LENGTH_SHORT).show();
                    confirmed = true;
                }
                curr = getCurrent();

                slices = load();



                // Check if current song has a slice
                if (slices.containsKey(trackuri)){
                    System.out.println("in there");
                    boolean remaining  = false;
                    for(int i = 0; i < slices.get(trackuri).size(); i++){
                        int first = slices.get(trackuri).get(i)[0];
                        int second = slices.get(trackuri).get(i)[1];

                        double f = first/1000.0;
                        double se = second/1000.0;
                        double sec = seconds/1000.0;

                        System.out.println("Seeing if " + sec + " can jump to " + f + ", " + se);
                        System.out.println("Close enought 1 is " + closeEnough(sec, f));
                        System.out.println("Close enought 2 is " + closeEnough(sec, se));
                        // Song is currently in a slice so all good
                        if ((seconds >= first || closeEnough(f, sec)) && (seconds <= second || closeEnough(se, sec) || second == duration)){
                            // if ((seconds >= first) && (seconds <= second|| second == duration)){

                            System.out.println("Currently in a slice");
                            remaining = true;
                            break;
                        }

                        // Not in a slice, so jumping to the next one
                        else if (seconds < first && !closeEnough(f, sec)){
                            System.out.println("Found a slice to jump to");
                            remaining = true;
                            check();
                            CallResult <Empty> callResult = mSpotifyAppRemote.getPlayerApi().seekTo(first);
                            Result<Empty> result = callResult.await(1, TimeUnit.SECONDS);
                            if (result.isSuccessful()){
                                System.out.println("Jumped to next slice");
                            }
                            else{
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

                    // No slices left so skipping  current song
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
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    pause();
                    return;
                }


                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    if (t.getName().equals("Playlist Runner")) {
                        System.out.println("A playlist thread is running");
                    }
                }



                // Iter makes sure we don't prematurely end playback because Spotify API takes a second to catch up
                iter ++;

                // Stupid stuff
                System.out.println("curr is " + curr);
                System.out.println(curr.equals("break"));
                System.out.println("Running is " + running);
                if (curr.equals("break")) break;
                if (!running) break;

            }
            while (running && !curr.equals("break") && ( curr.equals(playlistUri)) || curr.equals("") || iter < 10 || !mSpotifyAppRemote.isConnected());
            System.out.println("is playing is " + isPlaying());
            System.out.println("curr is " + curr);
            System.out.println("Loop over");

            pause();

        }

        private void pause(){
            running = false;
            stopForeground(true);
            stopSelf();
        }

        // Load the slices from the json file into the arraylist
        private HashMap<String, ArrayList<int[]>> load(){
            if(running) {
                HashMap<String, ArrayList<int[]>> slices = new HashMap<>();
                try {
                    String json = getSlices();
                    JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
                    if (jsonObject.has(playlistUri)) {
                        JSONObject playlist = (JSONObject) jsonObject.get(playlistUri);
                        for (Iterator<String> it = playlist.keys(); it.hasNext(); ) {
                            String s = it.next();
                            JSONObject song = (JSONObject) playlist.get(s);
                            ArrayList<int[]> temp = new ArrayList<>();
                            for (int j = 0; j < song.names().length(); j += 2) {
                                String name = (String) song.names().get(j);
                                String name2 = (String) song.names().get(j + 1);
                                int first = (int) song.get(name);
                                int second = (int) song.get(name2);
                                int[] t = {first, second};
                                temp.add(t);
                            }
                            slices.put(s, temp);

                        }
                    }
                } catch (JSONException j) {
                    j.printStackTrace();
                }
                return slices;
            }
            return new HashMap<>();
        }



    }

}
