package com.example.slice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Result;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Image;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.UserPrivate;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class PlaylistActivity extends AppCompatActivity {
    // Spotify Stuff
    private SpotifyAppRemote mSpotifyAppRemote;
    private static final String CLIENT_ID = "71ea8dc10ab14aea83e374692c3fea85";
    private static final String REDIRECT_URI = "http://127.0.0.1:8000/";
    private static final int REQUEST_CODE = 1337;
    private String token = "";

    // Getting new songs stuff
    private int offset = 0;
    private int song_total = 0;
    boolean waiting = true;

    // Running the playlist stuff
    public com.example.slice.Playlist model;

    // RecyclerView Stuff
    RecyclerView songRecycler;
    RecyclerView.Adapter <FindSong> songAdapter;
    ArrayList<Track> song_list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        // Test for git
        System.out.println("this is to test git");

        // Views and Intent
        String playlistUri = (String) getIntent().getExtras().get("playlistUri");
        String playlistName = (String) getIntent().getExtras().get("playlistName");
        String id = (String) getIntent().getExtras().get("playlistId");
        model = new com.example.slice.Playlist(playlistUri, playlistName, id, "");
        token = (String) getIntent().getExtras().get("token");
        TextView name = findViewById(R.id.playlist_activity_name_textView);
        name.setText(model.name);

        // Recycler View
        songRecycler = findViewById(R.id.playlist_activity_song_recycler);
        songRecycler.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        // ArrayList init
        if (!token.equals("")){
            SpotifyApi api = new SpotifyApi();
            api.setAccessToken(token);
            SpotifyService spotify = api.getService();
            spotify.getMe(new Callback<UserPrivate>() {
                @Override
                public void success(UserPrivate userPrivate, Response response) {
                    String userId = userPrivate.id;
                    System.out.println(userId);
                    String uri = model.uri.substring(17);


                    spotify.getPlaylist(userId, uri, new Callback<Playlist>() {
                        @Override
                        public void success(Playlist playlist, Response response) {
                            for(PlaylistTrack track: playlist.tracks.items){
                                Image i = track.track.album.images.get(0);
                                System.out.println(i.url);
                                Track t = new Track(track.track.uri, track.track.id, model.uri, track.track.name, (int) track.track.duration_ms, i.url);
                                song_list.add(t);
                                System.out.println(t.name);
                            }
                            song_total = playlist.tracks.total;
                            songAdapter.notifyDataSetChanged();
                            System.out.println(song_list.size() + " is the size of this playlist");
                            offset += song_list.size();
                        }

                        @Override
                        public void failure(RetrofitError error) {
                            System.out.println(error.getBody());
                            System.out.println("Error getting songs");
                        }
                    });
                }

                @Override
                public void failure(RetrofitError error) {

                }
            });
            String userId = "";
            System.out.println(userId);


        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // TODO: Check if i can just pass the spotifyappremote object from the main activity
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

                        // Now you can start interacting with App Remote
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("PlaylistActivity", throwable.getMessage(), throwable);

                        // Something went wrong when attempting to connect! Handle errors here
                    }
                });

        // Adapter
        songAdapter = new RecyclerView.Adapter<FindSong>() {
            @NonNull
            @Override
            public FindSong onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                // IDK what this does tbh
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.song_template, parent, false);
                return new FindSong(view);
            }

            @Override
            public void onBindViewHolder(@NonNull FindSong holder, int position) {
                // Update Fields
                Track songModel = song_list.get(position);
                holder.name.setText(songModel.name);

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            System.out.println(songModel.imageUrl + " is the url");
                            URL url = new URL(songModel.imageUrl);
                            Bitmap bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    holder.image.setImageBitmap(bitmap);
                                }
                            });
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                new Thread(r).start();

                // onclick
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(getApplicationContext(), SongActivity.class);
                        i.putExtra("SongUri", songModel.uri);
                        i.putExtra("SongName", songModel.name);
                        i.putExtra("SongDuration", songModel.duration_ms);
                        i.putExtra("SongID", songModel.id);

                        i.putExtra("PlaylistUri", model.uri);
                        startActivity(i);
                    }
                });

            }

            @Override
            public int getItemCount() {
                return song_list.size();
            }
        };



        songRecycler.setAdapter(songAdapter);
        System.out.println(songAdapter.getItemCount() + " is the number of songs in this playlist");

        // Check if recycler is at bottom of list
        songRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!songRecycler.canScrollVertically(1) && waiting){
                    boolean running = false;
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        System.out.println(t.getName());
                        if (t.getName().equals("AddingSongsToPlaylistThread")) {
                            running = true;
                            break;
                        }
                    }
                    if (!running){
                        AddSongsThread thread = new AddSongsThread();
                        thread.setName("AddingSongsToPlaylistThread");
                        thread.start();
                        // Toast.makeText(getApplicationContext(), (addMoreToRecycle() ? "added more to list" : "couldnt add more"), Toast.LENGTH_SHORT).show();
                    }

                }
            }
        });
    }

    public void backToHome(View v){
        Intent i = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(i);
    }

    public void play(View v) {
        // End any existing threads that are playing playlists or songs
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.println(t.getName());
            if (t.getName().equals("Playlist Runner")) {
                t.interrupt();
            }
            if (t.getName().equals("PlaySongThread")){
                t.interrupt();
            }
        }
        // Play selected playlist and start new thread to slice songs
        mSpotifyAppRemote.getPlayerApi().play(model.uri);
        RunPlaylistThread thread = new RunPlaylistThread(model.uri, mSpotifyAppRemote);
        thread.setName("Playlist Runner");
        thread.start();
    }

    public void clear(View v){
        // Clear all existing slices for the playlist
        try {
            String json = getSlices();
            JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
            if(jsonObject.has(model.uri)){
                jsonObject.remove(model.uri);
            }
            String o = jsonObject.toString();
            save(o);
        }
        catch(JSONException j){
            j.printStackTrace();
        }
    }

    public void save(String s){
        // Save the passed json into the file
        try {
            File file = new File(getApplicationContext().getFilesDir(), "JSON_SLICES");
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(s);
            bufferedWriter.close();
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    public String getSlices() {
        // Read the json file and return it as a string
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
            return "";
        }
        catch(IOException except){
            System.out.println(except.getMessage());
            return "";
        }
    }







    // Class for Adapter
    public static class FindSong extends RecyclerView.ViewHolder{
        TextView name;
        ImageView image;

        public FindSong(@NonNull View itemView) {
            super(itemView);

            // Update fields
            image = itemView.findViewById(R.id.song_template_imageView);
            name = itemView.findViewById(R.id.song_template_name_textView);
        }
    }


    // Class to add songs to the end of the adapter
    class AddSongsThread extends Thread{
        @Override
        public void run() {
            System.out.println("song list size is " + song_list.size());
            System.out.println("song total is " + song_total);
            if (song_list.size() == song_total){
                return;
            }
            OkHttpClient client = new OkHttpClient();
            HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.spotify.com/v1/playlists/" + model.id + "/tracks").newBuilder();
            // urlBuilder.addQueryParameter("playlist_id", model.id);
            urlBuilder.addQueryParameter("offset", String.valueOf(offset));
            // urlBuilder.addPathSegment("tracks");
            String url = urlBuilder.build().toString();
            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + token)
                    .url(url)
                    .build();
            try {
                okhttp3.Response response = client.newCall(request).execute();
                if(response.isSuccessful()){
                    System.out.println("It worked ')");
                    // System.out.println(response.body().string());

                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        if(jsonObject.has("items")){
                            JSONArray items = jsonObject.getJSONArray("items");
                            int i;
                            for (i = 0; i < items.length(); i ++) {
                                System.out.println("in the array");
                                JSONObject item = (JSONObject) items.get(i);
                                if (item.has("track")){
                                    JSONObject track = item.getJSONObject("track");
                                    try{
                                        JSONObject image = (JSONObject) track.getJSONArray("images").get(0);
                                        Track t = new Track(track.getString("uri"), track.getString("id"), model.uri, track.getString("name"), track.getInt("duration_ms"), image.getString("url"));
                                        song_list.add(t);

                                    }
                                    catch(Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            }
                            System.out.println("Should have added " + i + " songs");

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
                else{
                    throw new IOException("Errro " + response);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            offset = song_list.size();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    songAdapter.notifyDataSetChanged();
                }
            });
        }
    }


    // Thread class that runs the playlist
    class RunPlaylistThread extends Thread{

        // Fields to run the playlist
        String uri;
        int seconds = 0;
        int duration = 0;
        SpotifyAppRemote mSpotifyAppRemote;

        // init the fields
        RunPlaylistThread(String u, SpotifyAppRemote spotifyAppRemote){
            uri = u;
            mSpotifyAppRemote = spotifyAppRemote;
        }

        @Override
        public void run(){

            String curr;
            HashMap<String, ArrayList<int[]>> slices = load();

            System.out.println(slices.toString());
            System.out.println("Printing the slices");

            // TODO:: Make sure all the timing is right for pauses

            // Needed pause to make sure we recognize spotify is playing
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println(isPlaying());

            // Do slice stuff
            do{
                curr = getCurrent();
                slices = load();


                // Check if current song has a slice
                if (slices.containsKey(curr)){
                    System.out.println("in there");
                    boolean remaining  = false;
                    for(int i = 0; i < slices.get(curr).size(); i++){
                        int first = slices.get(curr).get(i)[0];
                        int second = slices.get(curr).get(i)[1];

                        // Song is currently in a slice so all good
                        // Same issue with the last condition as in the songActivity one
                        if (seconds >= first && (seconds <= second || second == -1 || second == duration)){
                            remaining = true;
                            break;
                        }

                        // Not in a slice, so jumping to the next one
                        else if (seconds < first){
                            remaining = true;
                            CallResult <Empty> callResult = mSpotifyAppRemote.getPlayerApi().seekTo(first);
                            Result<Empty> result = callResult.await(1, TimeUnit.SECONDS);
                            if (result.isSuccessful()){
                                System.out.println("Jumped to next slice");
                            }
                            else{
                                System.out.println("Did not jump to next slice");
                            }
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            break;
                        }

                    }

                    // No slices left so skipping  current song
                    if (!remaining){
                        System.out.println("Skipping");
                        mSpotifyAppRemote.getPlayerApi().skipNext();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // System.out.println("New iteration");
            }
            while (isPlaying() && !curr.equals(""));

            System.out.println("Loop over");

        }

        private HashMap<String, ArrayList<int[]>> load(){
            HashMap<String, ArrayList<int[]>> slices = new HashMap<>();
            try {
                String json = getSlices();
                JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
                if (jsonObject.has(model.uri)){
                    // System.out.println("Found the playlist");
                    JSONObject playlist = (JSONObject) jsonObject.get(model.uri);
                    // System.out.println("have the playlist");
                    for (Iterator<String> it = playlist.keys(); it.hasNext(); ) {
                        String s = it.next();
                        JSONObject song = (JSONObject) playlist.get(s);
                        // System.out.println("Found the songs " + s);
                        ArrayList<int[]> temp = new ArrayList<>();
                        for (int j = 0; j < song.names().length(); j += 2) {
                            String name = (String) song.names().get(j);
                            String name2 = (String) song.names().get(j + 1);
                            int first = (int) song.get(name);
                            int second = (int) song.get(name2);
                            int [] t = {first, second};
                            temp.add(t);
                        }
//                        while (song.has("slice_" + i + "_start")){
//                            int first = song.getInt("slice_" + i + "_start");
//                            int second = song.getInt("slice_" + i + "_end");
//                            int[] t = {first, second};
//                            temp.add(t);
//                            i += 1;
//                        }
                        slices.put(s, temp);

                    }
                }
            }
            catch(JSONException j){
                j.printStackTrace();
            }
            return slices;
        }



        private boolean isPlaying(){
            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {
                PlayerState playerState = playerStateResult.getData();
                // have some fun with playerState
                // System.out.println("Isplaying is goiing well");
                return !playerState.isPaused;
            } else {
                Throwable error = playerStateResult.getError();
                // System.out.println("error')");
                return false;
                // try to have some fun with the error
            }
        }

        private String getCurrent(){
            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {
                PlayerState playerState = playerStateResult.getData();
                // have some fun with playerState
                // System.out.println("Get current is going well");
                // System.out.println(playerState.track.uri);
                seconds = (int) playerState.playbackPosition;
                if (playerState.track != null){
                    duration = (int) playerState.track.duration;
                    return playerState.track.uri;
                }
                return "";
            } else {
                Throwable error = playerStateResult.getError();
                // System.out.println("error') in the get current");
                return "";
                // try to have some fun with the error
            }
        }


    }

}