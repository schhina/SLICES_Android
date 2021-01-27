package com.example.slice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException;
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Result;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerState;
import com.squareup.picasso.Picasso;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.ArtistSimple;
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
    NestedScrollView scrollView;

    // Clearing Slices Dialog stuff
    private AlertDialog.Builder clearDataDialogBuilder;
    private AlertDialog clearDataDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_test);
        Toolbar toolbar = findViewById(R.id.toolbar_playlist);;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        connect();

        // Views and Intent
        String playlistUri = (String) getIntent().getExtras().get("playlistUri");
        String playlistName = (String) getIntent().getExtras().get("playlistName");
        String id = (String) getIntent().getExtras().get("playlistId");
        String imageUrl = getIntent().getExtras().getString("image");
        ImageView imageview = findViewById(R.id.playlist_image);
        Picasso.get().load(imageUrl).into(imageview);
        model = new com.example.slice.Playlist(playlistUri, playlistName, id, "");
        token = (String) getIntent().getExtras().get("token");
        TextView name = findViewById(R.id.playlist_name_textview);
        // name.setText(model.name);
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(model.name);
        toolBarLayout.setCollapsedTitleTypeface(Typeface.create("monospace", Typeface.NORMAL));
        toolBarLayout.setExpandedTitleTypeface(Typeface.create("monospace", Typeface.NORMAL));
        scrollView = findViewById(R.id.playlist_scroll);

        // Recycler View
        songRecycler = findViewById(R.id.test_recycler_view);
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
                                String artists = "";
                                for (ArtistSimple artist: track.track.artists){
                                    artists += artist.name; //  + ", ";
                                    break;
                                }
                                t.artist = artists;
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

    public void connect(){
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
    }


    @Override
    protected void onStart() {
        super.onStart();

        connect();

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
                holder.artist.setText(songModel.artist);
                if (!songModel.imageUrl.equals("")) Picasso.get().load(songModel.imageUrl).into(holder.image);
                else holder.image.setVisibility(View.INVISIBLE);

                // onclick
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(getApplicationContext(), SongActivity.class);
                        i.putExtra("SongUri", songModel.uri);
                        i.putExtra("SongName", songModel.name);
                        i.putExtra("SongDuration", songModel.duration_ms);
                        i.putExtra("SongID", songModel.id);
                        i.putExtra("image", songModel.imageUrl);
                        i.putExtra("PlaylistUri", model.uri);
                        i.putExtra("artist", songModel.artist);
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
        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);

                int diff = (view.getBottom() - (scrollView.getHeight() + scrollView
                        .getScrollY()));

                if (diff == 0) {
                    boolean running = false;
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        System.out.println(t.getName());
                        if (t.getName().equals("AddingSongsToPlaylistThread")) {
                            running = true;
                            break;
                        }
                    }
                    if (!running && song_list.size() != song_total){
                        Snackbar.make(view, "Fetching more songs", Snackbar.LENGTH_SHORT).show();
                        AddSongsThread thread = new AddSongsThread();
                        thread.setName("AddingSongsToPlaylistThread");
                        thread.start();
                        // Toast.makeText(getApplicationContext(), (addMoreToRecycle() ? "added more to list" : "couldnt add more"), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_playlist_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear_playlist:
                Toast.makeText(getApplicationContext(), "Gonna clear slices", Toast.LENGTH_SHORT).show();


                clearDataDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog));
                final View popUpView = getLayoutInflater().inflate(R.layout.clear_playlist_popup, null);
                Button confirm = popUpView.findViewById(R.id.clear_playlist_confirm_button);
                Button decline = popUpView.findViewById(R.id.clear_playlist_decline_button);

                confirm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clear();
                        Toast.makeText(getApplicationContext(), "Cleared Slice data", Toast.LENGTH_SHORT).show();
                        clearDataDialog.dismiss();
                    }
                });

                decline.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clearDataDialog.dismiss();
                    }
                });


                clearDataDialogBuilder.setView(popUpView);
                clearDataDialog = clearDataDialogBuilder.create();
                clearDataDialog.show();

                return true;

            case R.id.action_open_spotify_playlist:
                if (SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())){
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(model.uri));
                    intent.putExtra(Intent.EXTRA_REFERRER,
                            Uri.parse("android-app://" + getApplicationContext().getPackageName()));
                    startActivity(intent);
                }
                else{
                    String cleaned;
                    if (model.uri.length() > 18) cleaned = model.uri.substring(17);
                    else cleaned = "";
                    System.out.println(cleaned);
                    if (cleaned.equals("")) Snackbar.make(songRecycler, "Sorry, couldn't find that playlist", Snackbar.LENGTH_SHORT).show();
                    else{
                        Uri uriUrl = Uri.parse("https://open.spotify.com/playlist/" + cleaned);
                        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
                        startActivity(launchBrowser);
                    }

                }

                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
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
        if (!SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())){
            Snackbar.make(v, "Spotify must be installed to use this feature", Snackbar.LENGTH_SHORT).show();
        }
        else{
            if (!mSpotifyAppRemote.isConnected()) connect();
            try{
                mSpotifyAppRemote.getPlayerApi().play(model.uri);
                RunPlaylistThread thread = new RunPlaylistThread(model.uri, mSpotifyAppRemote);
                thread.setName("Playlist Runner");
                thread.start();
            }
            catch(Exception e){
                e.printStackTrace();
            }

        }


    }

    public void clear(){
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
            notFound.printStackTrace();
            return "";
        }
        catch(IOException except){
            System.out.println(except.getMessage());
            return "";
        }

    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Class for Adapter
    public static class FindSong extends RecyclerView.ViewHolder{
        TextView name, artist;
        ImageView image;

        public FindSong(@NonNull View itemView) {
            super(itemView);

            // Update fields
            image = itemView.findViewById(R.id.song_template_imageView);
            name = itemView.findViewById(R.id.song_template_name_textView);
            artist = itemView.findViewById(R.id.song_template_artist_textView);
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
                                        Track t = new Track(track.getString("uri"), track.getString("id"), model.uri, track.getString("name"), track.getInt("duration_ms"), "");
                                        if (track.has("album")){
                                            JSONObject album = track.getJSONObject("album");
                                            if (album.has("images")) {
                                                JSONObject image = (JSONObject) album.getJSONArray("images").get(0);
                                                t.imageUrl = image.getString("url");
                                            }
                                        }
                                        if (track.has("artists")){
                                            JSONObject artist = track.getJSONArray("artists").getJSONObject(0);
                                            t.artist = artist.getString("name");
                                        }
                                        System.out.println("Added " + t.name + " to the list");
                                        System.out.println("It's image url is " + t.imageUrl);
                                        System.out.println("It's artist name is " + t.artist);
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
            int iter = 0;
            HashMap<String, ArrayList<int[]>> slices;

            // TODO:: Make sure all the timing is right for pauses
            // TODO:: If start and end time for slice are the same, skip it

            check();
            CallResult <Empty> cr = mSpotifyAppRemote.getPlayerApi().seekTo(0);
            Result<Empty> r = cr.await(1, TimeUnit.SECONDS);
            if (r.isSuccessful()){
                System.out.println("Went to 0 seconds correctly");
            }
            else{
                r.getError().printStackTrace();
                System.out.println("Was not able to go to 0");
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
                            System.out.println("Currently in a slice");
                            remaining = true;
                            break;
                        }

                        // Not in a slice, so jumping to the next one
                        else if (seconds < first){
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
                            return;
                        }
                    }
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                // System.out.println("New iteration");
                iter ++;
            }
            while ((isPlaying() && !curr.equals("")) || iter < 10);
            System.out.println("is playing is " + isPlaying());
            System.out.println("curr is " + curr);
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

        private void check(){
            if (!mSpotifyAppRemote.isConnected()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Spotify disconnected");
                        connect();
                    }
                });
            }
        }



        private boolean isPlaying(){
            check();
            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {
                PlayerState playerState = playerStateResult.getData();
                return !playerState.isPaused;
            } else {
                Throwable error = playerStateResult.getError();
                error.printStackTrace();
                return false;
            }
        }

        private String getCurrent(){
            check();
            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {
                PlayerState playerState = playerStateResult.getData();
                seconds = (int) playerState.playbackPosition;
                if (playerState.track != null){
                    duration = (int) playerState.track.duration;
                    return playerState.track.uri;
                }
            } else {
                Throwable error = playerStateResult.getError();
                error.printStackTrace();
                // System.out.println("error') in the get current");
            }
            return "";
        }


    }

}