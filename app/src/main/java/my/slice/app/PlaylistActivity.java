package my.slice.app;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Result;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerContext;
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

    // Playlist model
    public my.slice.app.Playlist model;

    // RecyclerView Stuff
    RecyclerView songRecycler;
    RecyclerView.Adapter <FindSong> songAdapter;
    ArrayList<Track> song_list = new ArrayList<>();
    NestedScrollView scrollView;

    // Clearing Slices Dialog stuff
    private AlertDialog.Builder clearDataDialogBuilder;
    private AlertDialog clearDataDialog;

    FloatingActionButton fab;

    // Service Stuff
    private boolean isBound = false;
    private PlaylistService service;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Connected to service
            PlaylistService.LocalBinder binder = (PlaylistService.LocalBinder) iBinder;
            service = binder.getService();
            isBound = true;
            String uri = service.getURI();
            System.out.println("binded");
            if (uri != null && model != null && uri.equals(model.uri))fab.setImageResource(R.drawable.ic_baseline_pause_24);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            // Disconnected to service
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        // Toolbar stuff
        Toolbar toolbar = findViewById(R.id.toolbar_playlist);;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        // Connect to spotify
        connect(false);

        // Get playlist info from intent
        String playlistUri = (String) getIntent().getExtras().get("playlistUri");
        String playlistName = (String) getIntent().getExtras().get("playlistName");
        String id = (String) getIntent().getExtras().get("playlistId");
        String imageUrl = getIntent().getExtras().getString("image");
        token = (String) getIntent().getExtras().get("token");

        // Update views and model class
        ImageView imageview = findViewById(R.id.playlist_image);
        Picasso.get().load(imageUrl).into(imageview);
        fab = findViewById(R.id.playlist_fab);
        fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        model = new my.slice.app.Playlist(playlistUri, playlistName, id, "");
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(model.name);
        toolBarLayout.setCollapsedTitleTypeface(Typeface.create("monospace", Typeface.NORMAL));
        toolBarLayout.setExpandedTitleTypeface(Typeface.create("monospace", Typeface.NORMAL));
        scrollView = findViewById(R.id.playlist_scroll);

        for (Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.println(t.getName());
            if (t.getName().equals("Playlist Runner")) {
                RunPlaylistThread rpt = (RunPlaylistThread) t;
                if (model.uri.equals(rpt.playlistUri)){
                    fab.setImageResource(R.drawable.ic_baseline_pause_24);
                }
            }
        }
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playTest(false);
            }
        });

        // Recycler View
        songRecycler = findViewById(R.id.test_recycler_view);
        songRecycler.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        // Todo: Figure out if i need to put overscroll on recycler view or scroll view
//        OverScrollDecoratorHelper.setUpOverScroll(songRecycler, OverScrollDecoratorHelper.ORIENTATION_VERTICAL);
//        new VerticalOverScrollBounceEffectDecorator(new IOverScrollDecoratorAdapter() {
//            @Override
//            public View getView() {
//                return scrollView;
//            }
//
//            @Override
//            public boolean isInAbsoluteStart() {
//                return !scrollView.canScrollVertically(-1);
//            }
//
//            @Override
//            public boolean isInAbsoluteEnd() {
//                return !scrollView.canScrollVertically(1);
//            }
//        });

        // ArrayList init
        // Maybe change this with OKHttp
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
                                // System.out.println(i.url);
                                Track t = new Track(track.track.uri, track.track.id, model.uri, track.track.name, (int) track.track.duration_ms, i.url);
                                String artists = "";
                                for (ArtistSimple artist: track.track.artists){
                                    artists += artist.name; //  + ", ";
                                    break;
                                }
                                t.artist = artists;
                                song_list.add(t);
                                // System.out.println(t.name);
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
                    error.printStackTrace();
                }
            });
            String userId = "";
            System.out.println(userId);


        }

        // See if a playlist is currently being played
        System.out.println("checking if real");
        if (isPlaying()) fab.setImageResource(R.drawable.ic_baseline_pause_24);
        else fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
    }

    public boolean isPlaying(){
        if (!isBound){
            Intent intent = new Intent(this, PlaylistService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
        String uri = (service != null) ? service.getURI() : "";
        return (uri != null && isBound && uri.equals(model.uri));
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


    @Override
    protected void onStart() {
        super.onStart();

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
                // Update Fields and views
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

//        RecyclerView.EdgeEffectFactory edge = new RecyclerView.EdgeEffectFactory(){
//            @NonNull
//            @Override
//            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
//
//                return new EdgeEffect(view.getContext()){
//                    @Override
//                    public void onPull(float deltaDistance) {
//                        super.onPull(deltaDistance);
//                        handlePull(deltaDistance);
//                    }
//
//                    @Override
//                    public void onPull(float deltaDistance, float displacement) {
//                        super.onPull(deltaDistance, displacement);
//                        handlePull(deltaDistance);
//                    }
//
//                    private void handlePull(float deltaDistance){
//                        int sign = (direction == DIRECTION_BOTTOM) ? -1 : 1;
//                        float rotationDelta = sign * deltaDistance * -10; // -10 is OVERSCROLL_ROTATION_MAGNITUDE
//                        float translationDelta = sign * view.getWidth() * deltaDistance * 0.2f; // 0.2 is OVERSCROLL_TRANSLATION_MAGNITUDE
//                        for(int i = 0; i < view.getAdapter().getItemCount(); i++){
//                            FindSong holder = (FindSong) view.findViewHolderForAdapterPosition(i);
//                            holder.itemView.setRotation(0);
//                            holder.itemView.setTranslationY(0);
//                            holder.itemView.setRotation(holder.itemView.getRotation() + rotationDelta);
//                            holder.itemView.setTranslationY(holder.itemView.getTranslationY() + translationDelta);
//                        }
//                    }
//
//                    @Override
//                    public void onRelease() {
//                        super.onRelease();
//                        for(int i = 0; i < view.getAdapter().getItemCount(); i++) {
//                            FindSong holder = (FindSong) view.findViewHolderForAdapterPosition(i);
//                            holder.itemView.setRotation(1);
//                            holder.itemView.setTranslationY(1);
//                        }
//                    }
//
//                    @Override
//                    public void onAbsorb(int velocity) {
//                        super.onAbsorb(velocity);
//                        int sign = (direction == DIRECTION_BOTTOM) ? -1 : 1;
//                        float translationVelocity = sign * velocity * 0.5f; // 0.5 if FLING_TRANSLATION_MAGNITUDE
//                        for(int i = 0; i < view.getAdapter().getItemCount(); i++){
//                            FindSong holder = (FindSong) view.findViewHolderForAdapterPosition(i);
//                            ;
//                        }
//                    }
//                };
//
//            }
//        };


        // Check if recycler is at bottom of list
        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {

                View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
                int diff = (view.getBottom() - (scrollView.getHeight() + scrollView
                        .getScrollY()));
                if (diff == 0) {
                    boolean running = false;
                    // Check if any other thread is looking for songs
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        System.out.println(t.getName());
                        if (t.getName().equals("AddingSongsToPlaylistThread")) {
                            running = true;
                            break;
                        }
                    }
                    // Start a thread looking for more songs
                    if (!running && song_list.size() != song_total){
                        Snackbar.make(view, "Fetching more songs", Snackbar.LENGTH_SHORT).show();
                        AddSongsThread thread = new AddSongsThread();
                        thread.setName("AddingSongsToPlaylistThread");
                        thread.start();
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

            // Clear playlist button
            case R.id.action_clear_playlist:
                // Toast.makeText(getApplicationContext(), "Gonna clear slices", Toast.LENGTH_SHORT).show();

                // Open a dialog box
                clearDataDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog));
                final View popUpView = getLayoutInflater().inflate(R.layout.clear_playlist_popup, null);
                Button confirm = popUpView.findViewById(R.id.clear_playlist_confirm_button);
                Button decline = popUpView.findViewById(R.id.clear_playlist_decline_button);
                // Clear the data
                confirm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clear();
                        Snackbar.make(songRecycler, "Cleared Slice data", Snackbar.LENGTH_SHORT).show();
                        clearDataDialog.dismiss();
                    }
                });
                // Don't clear the data
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

            // Open playlist in Spotify
            case R.id.action_open_spotify_playlist:
                if (SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())){

                    // Open playlist on spotify app
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(model.uri));
                    intent.putExtra(Intent.EXTRA_REFERRER,
                            Uri.parse("android-app://" + getApplicationContext().getPackageName()));
                    startActivity(intent);
                }
                else{

                    // Open playlist on spotify website
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

    public void playButton(View v){
        play(false);
    }

    public void playTest(boolean restart){
        Intent notificationIntent = new Intent(this, PlaylistService.class);
        if (!isPlaying()){
            notificationIntent.putExtra("playlistUri", model.uri);
            notificationIntent.putExtra("name", model.name);
            startService(notificationIntent);
            bindService(notificationIntent, connection, Context.BIND_AUTO_CREATE);
            fab.setImageResource(R.drawable.ic_baseline_pause_24);
        } else{
            if (isBound) unbindService(connection);
            isBound = false;
            stopService(notificationIntent);
            fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }

    }

    // Play the playlist with slices
    public void play(boolean restart) {

        if (mSpotifyAppRemote == null) {
            Toast.makeText(getApplicationContext(), "Wait a second before trying that again", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean playing = false;

        // End any existing threads that are playing playlists or songs
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.println(t.getName());
            if (t.getName().equals("Playlist Runner")) {

                RunPlaylistThread rpt = (RunPlaylistThread) t;
                if (model.uri.equals(rpt.playlistUri)){
                    fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                    playing = true;

                }
                rpt.terminate();
                System.out.println("Found a playlist thread to end");


            }
            if (t.getName().equals("PlaySongThread")){
                SongActivity.PlaySongThread pst = (SongActivity.PlaySongThread) t;
                pst.terminate();
                t.interrupt();
            }
        }

        // Play selected playlist and start new thread to slice songs
        if (!SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())) Snackbar.make(songRecycler, "Spotify must be installed to use this feature", Snackbar.LENGTH_SHORT).show();
        else if (playing && !restart){
            mSpotifyAppRemote.getPlayerApi().pause();
            System.out.println("Playing is + " + playing);
        }
        else{
            if (!mSpotifyAppRemote.isConnected()) {
                // Snackbar.make(findViewById(android.R.id.content), "Wait a few seconds before trying again", Snackbar.LENGTH_SHORT);
                connect(false);
                Toast.makeText(this, "Wait a few seconds before trying again", Toast.LENGTH_SHORT).show();
                System.out.println("Wasn't connected to spotify at first");
            }
            else{
                try{
                    fab.setImageResource(R.drawable.ic_baseline_pause_24);
                    if (!restart) mSpotifyAppRemote.getPlayerApi().play(model.uri);
                    RunPlaylistThread thread = new RunPlaylistThread(model.uri, mSpotifyAppRemote);
                    thread.setName("Playlist Runner");
                    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                            throwable.printStackTrace();
                            System.out.println("Thread: " + ((RunPlaylistThread)thread).playlistUri + " had an oopsie");
                        }
                    });
                    System.out.println("About to start a playlist thread");
                    thread.start();
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }


        }


    }

    // Clear all existing slices for the playlist
    public void clear(){
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

    // Save the passed json into the file
    public void save(String s){
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

    // Read the json file and return it as a string
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

    // Back button on the top leftd
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Class for Adapter
    public static class FindSong extends RecyclerView.ViewHolder{
        TextView name, artist;
        ImageView image;
        double current_velocity;

        public FindSong(@NonNull View itemView) {
            super(itemView);

            // Update fields
            image = itemView.findViewById(R.id.song_template_imageView);
            name = itemView.findViewById(R.id.song_template_name_textView);
            artist = itemView.findViewById(R.id.song_template_artist_textView);

//            SpringAnimation spring = new SpringAnimation(itemView, SpringAnimation.ROTATION);
//            spring.setSpring(new SpringForce()
//                    .setFinalPosition(0f)
//                    .setDampingRatio(SpringForce.DAMPING_RATIO_HIGH_BOUNCY)
//                    .setStiffness(SpringForce.STIFFNESS_LOW)
//            ).addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
//                @Override
//                public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
//                    current_velocity = velocity;
//                }
//            });
//            SpringAnimation spring2 = new SpringAnimation(itemView, SpringAnimation.TRANSLATION_Y);
//            spring2.setSpring(new SpringForce()
//                    .setFinalPosition(0)
//                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
//                    .setStiffness(SpringForce.STIFFNESS_LOW)
//            );
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

    public boolean closeEnough(double l, double r){
        double val = l - r;
        if (val < 0) val *= -1;
        return val < 0.5;
    }


    // Thread class that runs the playlist
    class RunPlaylistThread extends Thread{

        // Fields to run the playlist
        String uri;
        String playlistUri = model.uri;
        int seconds = 0;
        int duration = 0;
        SpotifyAppRemote mSpotifyAppRemote;
        boolean shown = false;
        boolean isConnecting = false;
        String trackuri = "";
        boolean running = true;
        final int TIMEOUT = 5;


        // init the fields
        RunPlaylistThread(String u, SpotifyAppRemote spotifyAppRemote){
            uri = u;
            mSpotifyAppRemote = spotifyAppRemote;
        }

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
            CallResult <Empty> cr = mSpotifyAppRemote.getPlayerApi().seekTo(0);
            Result<Empty> r = cr.await(1, TimeUnit.SECONDS);
            if (r.isSuccessful()){
                System.out.println("Went to 0 seconds correctly");
            }
            else{
                System.out.println(r.getErrorMessage());
                String str = r.getErrorMessage();
                if (str.equals("Result was not delivered on time.") && !shown) {
                    Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }
                r.getError().printStackTrace();
                System.out.println("Was not able to go to 0");
            }

            System.out.println(isPlaying());

            // Do slice stuff
            do{

                if (!confirmed && mSpotifyAppRemote.isConnected()){
                    Snackbar.make(songRecycler, "Playing this playlist!", Snackbar.LENGTH_SHORT).show();
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

                // Keep the CPU on so we can move the song while the phone is in doze mode (uses a lot of battery according to the internet)
                AlarmManager alarmManager =
                        (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(getApplicationContext(), AlertReciever.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), iter, intent, 0);
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 3000, pendingIntent);

            }
            while (running && !curr.equals("break") && ( curr.equals(model.uri)) || curr.equals("") || iter < 10 || !mSpotifyAppRemote.isConnected());
            System.out.println("is playing is " + isPlaying());
            System.out.println("curr is " + curr);
            System.out.println("Loop over");

            pause();

        }

        private void pause(){
            terminate();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fab.setImageResource(R.drawable.ic_baseline_play_arrow_24);
                }
            });
        }

        // Load the slices from the json file into the arraylist
        private HashMap<String, ArrayList<int[]>> load(){
            if(running) {
                HashMap<String, ArrayList<int[]>> slices = new HashMap<>();
                try {
                    String json = getSlices();
                    JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
                    if (jsonObject.has(model.uri)) {
                        JSONObject playlist = (JSONObject) jsonObject.get(model.uri);
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

        // Reconnect to spotify if disconnected
        private void check(){
            if (running) {
                if (!mSpotifyAppRemote.isConnected() && !isConnecting) {
                    System.out.println("\n\n\n DISCONNECTED FROM SPOTIFY \n\n\n");
                    System.out.println("Is connecting is " + isConnecting);
                    System.out.println("Remote is connected is " + mSpotifyAppRemote.isConnected());
                    isConnecting = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("Spotify disconnected");
                            connect(true);

                        }
                    });
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
                        Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
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
                        Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
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
                        Snackbar.make(songRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
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


    }


}
