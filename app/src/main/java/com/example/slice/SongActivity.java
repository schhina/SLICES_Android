package com.example.slice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarChangeListener;
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.Result;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerState;
import com.squareup.picasso.Picasso;

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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;

public class SongActivity extends AppCompatActivity {

    // Song and Playlist fields
    private Track model;
    private String playlistUri;

    // Recycler stuff
    private RecyclerView sliceRecycler;
    private RecyclerView.Adapter <FindSlice> sliceAdapter;
    private ArrayList<Slice> slices = new ArrayList<>();

    // Spotify Stuff
    private SpotifyAppRemote mSpotifyAppRemote;
    private static final String CLIENT_ID = "71ea8dc10ab14aea83e374692c3fea85";
    private static final String REDIRECT_URI = "http://127.0.0.1:8000/";
    private static final int REQUEST_CODE = 1337;

    Menu menu;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song);

        // Toolbar stuff
        Toolbar toolbar = findViewById(R.id.song_toolbar);;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);


        // Init song and playlist fields
        String songUri = (String) getIntent().getExtras().get("SongUri");
        String songName = (String) getIntent().getExtras().get("SongName");
        int duration = (int) getIntent().getExtras().get("SongDuration");
        String SongID = (String) getIntent().getExtras().get("SongID");
        String imageUrl = getIntent().getExtras().getString("image");
        String artist = getIntent().getExtras().getString("artist");
        playlistUri = (String) getIntent().getExtras().get("PlaylistUri");
        model = new Track(songUri, SongID, playlistUri, songName, duration, imageUrl);
        model.artist = artist;

        // Init Toolbar
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.song_collapsing_toolbar);
        toolBarLayout.setTitle(model.name + " - " + model.artist);
        toolBarLayout.setCollapsedTitleTypeface(Typeface.create("monospace", Typeface.NORMAL));
        toolBarLayout.setExpandedTitleTypeface(Typeface.create("monospace", Typeface.NORMAL));

        // Init recycler
        sliceRecycler = findViewById(R.id.song_activity_slice_recycler);
        sliceRecycler.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        // Init Slice list
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Spotify stuff
        connect(false);

        // Init Adapter
        sliceAdapter = new RecyclerView.Adapter<FindSlice>() {
            @NonNull
            @Override
            public FindSlice onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.slice_template, parent, false);
                return new FindSlice(view);
            }

            @Override
            public void onBindViewHolder(@NonNull FindSlice holder, int position) {

                // Init Fields
                Slice slice = slices.get(position);
                int left = slice.times[0];
                int right = slice.times[1];

                // Init Edit texts
                holder.left.setText(left/1000.0 + "");
                holder.right.setText(right/1000.0 + "");

                // Init seekbar
                holder.seekbar.setDataType(CrystalRangeSeekbar.DataType.FLOAT);
                holder.seekbar.setMaxValue((float) (model.duration_ms/1000.0));
                holder.seekbar.setMinStartValue((float) (slices.get(position).times[0]/1000.0));
                holder.seekbar.setMaxStartValue((float) (slices.get(position).times[1]/1000.0));
                holder.seekbar.apply();
                System.out.println( "slice starts at " + left/1000.0);

                // Delete Button
                holder.delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            slices.remove(position);
                            save(false);
                            sliceAdapter.notifyDataSetChanged();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                });

                // Do this when the seekbar changes
                holder.seekbar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
                    @Override
                    public void valueChanged(Number minValue, Number maxValue) {
                        System.out.println(position + " got moved a bit");

                        // This is to prevent infinite recursion from happening between this and the edit texts
                        if (!closeEnough(parseDouble(holder.left.getText().toString()), minValue.doubleValue()) ||
                        !closeEnough(parseDouble(holder.right.getText().toString()), maxValue.doubleValue())){
                            System.out.println("not close enought");
                            holder.left.setText(minValue + "");
                            holder.right.setText(maxValue + "");
                        }

                        int left = (int) (holder.seekbar.getSelectedMinValue().doubleValue()*1000);
                        int right = (int) (holder.seekbar.getSelectedMaxValue().doubleValue()*1000);
                        slices.get(position).times[0] = left;
                        slices.get(position).times[1] = right;
                        try{
                            save(true);
                        }
                        catch(JSONException e){
                            e.printStackTrace();
                        }
                    }


                });

                // When the left EditText is updated;
                holder.left.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        System.out.println("Left got updtated to " + holder.left.getText().toString());
                        int left = (int) (parseDouble(holder.left.getText().toString())*1000);
                        System.out.println("left is " + left);
                        slices.get(position).times[0] = left;

                        // Prevents infinite Recursion from happening between the left edit text and seekbar
                        if (!closeEnough(holder.seekbar.getSelectedMinValue().intValue(), left)) {
                            holder.seekbar.setMinStartValue((float) (left / 1000.0));
                            holder.seekbar.setMaxStartValue((float) (slices.get(position).times[1] / 1000.0));
                            holder.seekbar.apply();
                        }

                        try {
                            save(true);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return false;
                    }
                });

                // When the right edtitext si updated
                holder.right.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        System.out.println("Right got updtated to " + holder.right.getText().toString());
                        int right = (int) (parseDouble(holder.right.getText().toString())*1000);
                        slices.get(position).times[1] = right;

                        // Prevents infinite recursion from the right edit text and the seekbar
                        if (!closeEnough(holder.seekbar.getSelectedMaxValue().intValue(), right)) {
                            holder.seekbar.setDataType(CrystalRangeSeekbar.DataType.FLOAT);
                            holder.seekbar.setMinStartValue((float) (slices.get(position).times[0] / 1000.0));
                            holder.seekbar.setMaxStartValue((float) (right / 1000.0));
                            holder.seekbar.apply();
                        }
                        try {
                            save(true);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return false;
                    }
                });

            }

            @Override
            public int getItemCount() {
                return slices.size();
            }
        };

        sliceRecycler.setAdapter(sliceAdapter);
        System.out.println(sliceAdapter.getItemCount());

        // TODO: Find a stable way to not make slice lists over 5(original default value) break the seekbars
        sliceRecycler.getRecycledViewPool().setMaxRecycledViews(0,50);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        getMenuInflater().inflate(R.menu.song_menu, menu);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.println(t.getName());
            if (t.getName().equals("PlaySongThread")) {
                PlaySongThread rpt = (PlaySongThread) t;
                if (model.uri.equals(rpt.trackUri)){
                    menu.findItem(R.id.action_play_song).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause_24));
                }
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Play the song button
            case R.id.action_play_song:
                playSong(item.getActionView());
                return true;

            // Add a slice to the list and recycler view
            case R.id.action_add_slice:
                addSlice(item.getActionView());
                return true;

            // Open song in spotify
            case R.id.action_open_spotify_song:

                // Check if spotify is installed
                if (SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())){
                    // Open song in spotif
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(model.uri));
                    intent.putExtra(Intent.EXTRA_REFERRER,
                            Uri.parse("android-app://" + getApplicationContext().getPackageName()));
                    startActivity(intent);
                }
                else {
                    // Open song in web browser
                    String cleaned;
                    if (model.uri.length() > 15) cleaned = model.uri.substring(14);
                    else cleaned = "";
                    System.out.println(cleaned);
                    if (cleaned.equals(""))
                        Snackbar.make(sliceRecycler, "Sorry, couldn't find that song", Snackbar.LENGTH_SHORT).show();
                    else {
                        Uri uriUrl = Uri.parse("https://open.spotify.com/album/" + cleaned);
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

    // When you exit this page, save the slices
    @Override
    protected void onStop() {
        super.onStop();

        try{
            save(false);
        }
        catch(JSONException e){
            e.printStackTrace();
        }
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
                            playSong(true);
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

    // Determines if two doubles are close enough. Used to prevent infinite Recursion with the Seekbar.
    public boolean closeEnough(double l, double r){
        double val = l - r;
        if (val < 0) val *= -1;
        return val < 0.5;
    }

    // When the top left back arrow
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Load the slices from the json into the arraylist
    public void load() {
        try {
            String json = getSlices();
            JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
            System.out.println(jsonObject.toString());
            if (jsonObject.has(playlistUri)) {
                JSONObject playlist = (JSONObject) jsonObject.get(playlistUri);
                if (playlist.has(model.uri)) {
                    JSONObject song = (JSONObject) playlist.get(model.uri);
                    slices.clear();
                    for (int i = 0; i < song.names().length(); i += 2) {
                        String name = song.names().getString(i);
                        String name2 = song.names().getString(i + 1);
                        this.slices.add(new Slice(new int[]{song.getInt(name), song.getInt(name2)}, i / 2, model.uri));
                        System.out.println(Arrays.toString(slices.get(i / 2).times));
                    }
                    printSlice();
                    sliceRecycler.post(new Runnable() {
                        @Override
                        public void run() {
                            sliceAdapter.notifyDataSetChanged();
                        }
                    });

                }
            }

        }
        catch(JSONException e){
            System.out.println("JSONEException");
            e.printStackTrace();
        }
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

    // Print the slices to the console
    public void printSlice(){
        System.out.println("Printing slice");
        for(int i = 0; i < slices.size(); i++){
            System.out.println((Arrays.toString(slices.get(i).times)));
        }
    }

    // Sort and remove overlap in slices arraylist
    public boolean clean(){
        boolean error = false;

        // Sort the arraylist by the first number first (Insertion or Selection sort)
        printSlice();
        ArrayList<Slice> temp = new ArrayList<>();
        int length = slices.size();
        for(int j = 0; j < length; j++) {
            Slice s = slices.get(0);
            for (int i = 1; i < slices.size(); i++) {
                Slice sl = slices.get(i);
                if (sl.times[0] > s.times[0]){
                    s = sl;
                }
            }
            slices.remove(s);
            temp.add(0, s);
        }
        System.out.println("Printing temp");
        for(int i = 0; i < slices.size(); i++){
            System.out.println(Arrays.toString(temp.get(i).times));
        }
        printSlice();
        slices = temp;

        // Check and remove any overlap
        for(int i = 0; i < slices.size() - 1; i++){
            Slice first = slices.get(i);
            Slice second = slices.get(i + 1);
            if(first.times[1] > second.times[0]){
                slices.remove(i + 1);
                i--;
                error = true;
            }
        }
        printSlice();


        return error;
    }

    // Save the arraylist Slices into the JSON file
    public void save(boolean isEditText) throws JSONException {
        String json = getSlices();
        JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
        JSONObject playlist = (JSONObject) ((jsonObject.has(playlistUri)) ? jsonObject.get(playlistUri) : new JSONObject());
        JSONObject newSong = new JSONObject();

        // S0rt and clean the Slice ArrayList if this request wasn't made by an edit text or seekbar
        if (!isEditText) if (clean()) Snackbar.make(sliceRecycler, "The problem with the slices has been removed", Snackbar.LENGTH_SHORT).show();

        // Add the slices into the json file
        for(int j = 0; j < slices.size(); j++){
            Slice s = slices.get(j);
            int first = s.times[0];
            int second = s.times[1];
            slices.get(j).number = j;
            newSong.put("slice_" + j + "_start", first);
            newSong.put("slice_" + j + "_end", second);
        }

        printSlice();
        System.out.println(newSong);

        // Replace or add the new JSONObject into the playlist object
        if (playlist.has(model.uri)) playlist.remove(model.uri);
        if (slices.size() != 0) playlist.put(model.uri, newSong);
        if (!jsonObject.has(playlistUri)) jsonObject.put(playlistUri, playlist);
        System.out.println(jsonObject.toString());
        write(jsonObject.toString());

        // If not an edit text or seekbar making the request, load from the json file to ensure its accurate and update the ui
        if (!isEditText) load();
    }

    // Write the given JSON String into the JSON file
    public boolean write(String s){
        try {
            File file = new File(getApplicationContext().getFilesDir(), "JSON_SLICES");
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(s);
            bufferedWriter.close();
            return true;
        }
        catch(IOException e){
            e.printStackTrace();
            return false;
        }
    }

    // Add a slice to the ArrayList and Adapter
    public void addSlice(View v){
        try {

            // Hard limit on number of slices because of the default recycler values
            if (slices.size() > 20){
                Snackbar.make(v, "Slice can only handle 20 slices at the moment", Snackbar.LENGTH_SHORT).show();
                return;
            }

            System.out.println("Adding a slice");
            save(false);
            Slice brand_new = new Slice();
            brand_new.songUri = model.uri;
            brand_new.times = new int[]{0, model.duration_ms};
            brand_new.number = slices.size();
            slices.add(brand_new);
            printSlice();

            // We do not save after addign the item to the slices list
            sliceRecycler.getRecycledViewPool().clear();
            sliceAdapter.notifyDataSetChanged();
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        System.out.println("Done addding a slice");
        return;
    }

    public void playSong(View v){
        playSong(false);
    }

    // Play the Current song by making a new thread
    public void playSong(boolean restart){
        // Check if any other threads are playing something
        boolean running = false;
        try {
            save(false);
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                System.out.println(t.getName());
                if (t.getName().equals("PlaySongThread")) {
                    PlaySongThread pst = (PlaySongThread) t;
                    if (pst.trackUri.equals(model.uri)){
                        menu.findItem(R.id.action_play_song).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause_24));
                        running = true;
                    }
                    pst.terminate();
                    t.interrupt();
                }
                if (t.getName().equals("Playlist Runner")) {
                    PlaylistActivity.RunPlaylistThread rpt = (PlaylistActivity.RunPlaylistThread) t;
                    rpt.terminate();
                    t.interrupt();
                }
            }

            // Play the song and start the thread for slices
            if (!SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())) Snackbar.make(sliceRecycler, "Spotify must be installed to use this feature", Snackbar.LENGTH_SHORT).show();
            else if (running && !restart){
                mSpotifyAppRemote.getPlayerApi().pause();
                menu.findItem(R.id.action_play_song).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause_24));
            }
            else{
                if (!mSpotifyAppRemote.isConnected()) {
                    // Snackbar.make(findViewById(android.R.id.content), "Wait a few seconds before trying again", Snackbar.LENGTH_SHORT);
                    connect(false);
                    Toast.makeText(this, "Wait a few seconds before trying again", Toast.LENGTH_SHORT).show();
                    System.out.println("Wasn't connected to spotify at first");
                }
                menu.findItem(R.id.action_play_song).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_baseline_pause_24));
                if (!restart) mSpotifyAppRemote.getPlayerApi().play(model.uri);
                PlaySongThread thread = new PlaySongThread();
                thread.setName("PlaySongThread");
                thread.start();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    // Adapter class
    public static class FindSlice extends RecyclerView.ViewHolder{
        EditText left, right;
        Button delete;
        CrystalRangeSeekbar seekbar;

        public FindSlice(@NonNull View itemView) {
            super(itemView);
            left = itemView.findViewById(R.id.slisce_template_left_time_EditText);
            right = itemView.findViewById(R.id.slice_template_right_time_EditText);
            delete = itemView.findViewById(R.id.slice_template_delete_button);
            seekbar = itemView.findViewById(R.id.slice_template_range_seekbar);
        }
    }


    // Thread to play the song
    public class PlaySongThread extends Thread{
        int seconds = 0;
        int duration = model.duration_ms;
        boolean shown = false;
        boolean isConnecting = false;
        boolean running = true;
        String trackUri = model.uri;

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
            CallResult <Empty> cr = mSpotifyAppRemote.getPlayerApi().seekTo(0);
            Result<Empty> r = cr.await(1, TimeUnit.SECONDS);
            if (r.isSuccessful()){
                System.out.println("Went to 0 seconds correctly");
            }
            else{
                System.out.println(r.getErrorMessage());
                if (r.getErrorMessage() == "Result was not delivered on time" && !shown) {
                    Snackbar.make(sliceRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }
                r.getError().printStackTrace();
                System.out.println("Was not able to go to 0");
            }

            System.out.println(isPlaying());
            do{
                if (!confirmed && mSpotifyAppRemote.isConnected()){
                    Snackbar.make(sliceRecycler, "Playing this song!", Snackbar.LENGTH_SHORT).show();
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

                if (st.equals("break"))break;

                // iter is here to make sure this thread doesn't end prematurely because it takes a second for spotify api to recognize what we are listening to
                iter++;
                AlarmManager alarmManager =
                        (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(getApplicationContext(), AlertReciever.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), iter, intent, 0);
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 3000, pendingIntent);
            }
            while(running && (st.equals(model.uri)) || st.equals("") || iter < 10 || !mSpotifyAppRemote.isConnected());
            System.out.println("Loop over");

            pause();
        }

        private void pause(){
            terminate();
        }

        // Check if we are disconnected from spotify and reconnect if so
        private void check(){
            if (!running) return;
            if (!mSpotifyAppRemote.isConnected() && !isConnecting) {
                isConnecting = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Spotify disconnected");
                        connect(true);
                    }
                });
                terminate();
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
                    Snackbar.make(sliceRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
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
                    Snackbar.make(sliceRecycler, "Trouble connecting to spotify", Snackbar.LENGTH_SHORT).show();
                    shown = true;
                }

            }
            return "";
        }

        public void terminate(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    menu.findItem(R.id.action_play_song).setIcon(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_baseline_play_arrow_24));
                }
            });
            running = false;
        }
    }
}