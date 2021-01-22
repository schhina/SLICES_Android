package com.example.slice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarChangeListener;
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar;
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

    // Views
    private TextView name;

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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song);

        // Init song and playlist fields
        String songUri = (String) getIntent().getExtras().get("SongUri");
        String songName = (String) getIntent().getExtras().get("SongName");
        int duration = (int) getIntent().getExtras().get("SongDuration");
        String SongID = (String) getIntent().getExtras().get("SongID");
        playlistUri = (String) getIntent().getExtras().get("PlaylistUri");
        model = new Track(songUri, SongID, playlistUri, songName, duration, "");

        // Init Views
        name = findViewById(R.id.song_activity_name_textView);
        name.setText(model.name);

        // Init recycler
        sliceRecycler = findViewById(R.id.song_activity_slice_recycler);
        sliceRecycler.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

    }

    @Override
    protected void onStart() {
        super.onStart();

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
                System.out.println((slice.times[0]));
                holder.seekbar.setDataType(CrystalRangeSeekbar.DataType.FLOAT);
                holder.seekbar.apply();
                holder.left.setText(left/1000.0 + "");
                holder.right.setText(right/1000.0 + "");
                holder.number.setText("Slice #" + slice.number);
                holder.seekbar.setMaxValue((float) (model.duration_ms/1000.0));
                holder.seekbar.setMinStartValue((float) (left/1000.0));
                holder.seekbar.setMaxStartValue((float) (right/1000.0));
                holder.seekbar.apply();
                System.out.println( "slice starts at " + left/1000.0);

                // Delete Button
                holder.delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            slices.remove(position);
                            save();
                            sliceAdapter.notifyDataSetChanged();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                });

                // Save button
//                holder.save.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        int left = (int) (holder.seekbar.getSelectedMinValue().doubleValue()*1000);
//                        int right = (int) (holder.seekbar.getSelectedMaxValue().doubleValue()*1000);
//                        System.out.println("left is " + left);
//                        slices.get(position).times[0] = left;
//                        slices.get(position).times[1] = right;
//                        try {
////                            if (!slice.isSaved()) {
////                                save(left, right);
////                                slices.get(position).saved = true;
////                            }
////                            else save();
//                            save();
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                });

                // Do this when the seekbar changes
                holder.seekbar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
                    @Override
                    public void valueChanged(Number minValue, Number maxValue) {
                        holder.left.setText(minValue + "");
                        holder.right.setText(maxValue + "");
                        int left = (int) (holder.seekbar.getSelectedMinValue().doubleValue()*1000);
                        int right = (int) (holder.seekbar.getSelectedMaxValue().doubleValue()*1000);
                        // System.out.println("left is " + left);
                        slices.get(position).times[0] = left;
                        slices.get(position).times[1] = right;

//                            try {
////                            if (!slice.isSaved()) {
////                                save(left, right);
////                                slices.get(position).saved = true;
////                            }
////                            else save();
//                                save();
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                            }
                        }


                });

                // When the left edittext is updated;
                holder.left.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        System.out.println("Left got updtated to " + holder.left.getText().toString());
                        int left = (int) (parseDouble(holder.left.getText().toString())*1000);
                        System.out.println("left is " + left);
                        slices.get(position).times[0] = left;
                        // holder.seekbar.setDataType(CrystalRangeSeekbar.DataType.FLOAT);
                        holder.seekbar.setMinStartValue((float) (left/1000.0));
                        holder.seekbar.apply();
                        try {
                            save();
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
                        holder.seekbar.setDataType(CrystalRangeSeekbar.DataType.FLOAT);
                        holder.seekbar.setMaxStartValue((float) (right/1000.0));
                        holder.seekbar.apply();
                        try {
                            save();
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
        load();

        // Spotify stuff
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
                    for(int i = 0; i < song.names().length(); i += 2){
                        String name = song.names().getString(i);
                        String name2 = song.names().getString(i + 1);
                        this.slices.add(new Slice(new int[]{song.getInt(name), song.getInt(name2)}, i/2, model.uri));
                        System.out.println(Arrays.toString(slices.get(i/2).times));
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
            return "";
        }
        catch(IOException except){
            System.out.println(except.getMessage());
            return "";
        }
    }

    public void printSlice(){
        System.out.println("Printing slice");
        for(int i = 0; i < slices.size(); i++){
            System.out.println((Arrays.toString(slices.get(i).times)));
        }
    }

    public boolean clean(){
        boolean error = false;

        // Sort the arraylist by the first number first
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
        // printSlice();

        // Check if there is any overlap
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
    public void save() throws JSONException {
        String json = getSlices();
        JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
        JSONObject playlist = (JSONObject) ((jsonObject.has(playlistUri)) ? jsonObject.get(playlistUri) : new JSONObject());
        JSONObject newSong = new JSONObject();

        // S0rt and clean the Slice ArrayList
        if (clean()) Toast.makeText(getApplicationContext(), "The problem with the slices has been removed", Toast.LENGTH_SHORT).show();

        // Add the slices into the json file
        for(int j = 0; j < slices.size(); j++){
            Slice s = slices.get(j);
            int first = s.times[0];
            int second = s.times[1];
            newSong.put("slice_" + s.number + "_start", first);
            newSong.put("slice_" + s.number + "_end", second);
        }

        if (playlist.has(model.uri)) playlist.remove(model.uri);
        if (slices.size() != 0) playlist.put(model.uri, newSong);

        if (!jsonObject.has(playlistUri)) jsonObject.put(playlistUri, playlist);
        System.out.println(jsonObject.toString());
        Toast.makeText(getApplicationContext(), (write(jsonObject.toString())) ? "success" : "failure", Toast.LENGTH_SHORT).show();
        load();
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
            save();
            Slice brand_new = new Slice();
            brand_new.times = new int[]{0, model.duration_ms};
            slices.add(brand_new);
            sliceAdapter.notifyDataSetChanged();
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Play the Current song by making a new thread
    public void playSong(View v){
        // Check if any other threads are playing something
        try {
            save();
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                System.out.println(t.getName());
                if (t.getName().equals("PlaySongThread")) {
                    t.interrupt();
                }
                if (t.getName().equals("Playlist Runner")) {
                    t.interrupt();
                }
            }

            // Play the song and start the thread for slices
            mSpotifyAppRemote.getPlayerApi().play(model.uri);
            PlaySongThread thread = new PlaySongThread();
            thread.setName("PlaySongThread");
            thread.start();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void saveData(View v) {
        try {
            save();
        }
        catch(JSONException e){
            e.printStackTrace();
        }
    }



    // Adapter class
    public static class FindSlice extends RecyclerView.ViewHolder{
        EditText left, right;
        TextView number;
        Button save, delete;
        CrystalRangeSeekbar seekbar;

        public FindSlice(@NonNull View itemView) {
            super(itemView);
            left = itemView.findViewById(R.id.slisce_template_left_time_EditText);
            right = itemView.findViewById(R.id.slice_template_right_time_EditText);
            number = itemView.findViewById(R.id.slice_template_name_textView);
            // save = itemView.findViewById(R.id.slice_template_save_button);
            delete = itemView.findViewById(R.id.slice_template_delete_button);
            seekbar = itemView.findViewById(R.id.slice_template_range_seekbar);
        }
    }

    // Thread to play the song
    public class PlaySongThread extends Thread{
        int seconds = 0;
        int duration = model.duration_ms;

        // Check the current song for slices
        @Override
        public void run() {
            // Todo: Make sure the pauses are correct
            // Todo: Learn how to Interrupt the thread correctly
            String st;
//            try {
//                Thread.sleep(800);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            System.out.println(isPlaying());
            do{
                st = getCurrent();
                boolean remaining = false;
                for(Slice s : slices){
                    int first = s.times[0];
                    int second = s.times[1];

                    // Currently in a Slice
                    // Last condition may be a problem because idt we can store the very last millisecond with the range bar
                    if (seconds >= first && (seconds <= second || second == -1 || second == duration)){
                        remaining = true;
                        break;
                    }

                    // Not currently in a slice and there is one in the future
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
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }

                // Skipping the song
                if (!remaining){
                    System.out.println("Skipping");
                    mSpotifyAppRemote.getPlayerApi().skipNext();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while(isPlaying() && !st.equals("") && st.equals(model.uri));
            System.out.println("Loop over");
        }

        // Checks if anything is playing in spotify
        private boolean isPlaying(){
            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {
                PlayerState playerState = playerStateResult.getData();
                // have some fun with playerState
                return !playerState.isPaused;
            } else {
                Throwable error = playerStateResult.getError();
                error.printStackTrace();
                return false;
                // try to have some fun with the error
            }
        }

        // Returns the current song uri and updates the seconds global variable
        private String getCurrent(){
            CallResult<PlayerState> playerStateCall = mSpotifyAppRemote.getPlayerApi().getPlayerState();
            Result<PlayerState> playerStateResult = playerStateCall.await(1, TimeUnit.SECONDS);
            if (playerStateResult.isSuccessful()) {
                PlayerState playerState = playerStateResult.getData();
                // have some fun with playerState
                seconds = (int) playerState.playbackPosition;
                if (playerState.track != null){
                    return playerState.track.uri;
                }
            } else {
                Throwable error = playerStateResult.getError();
                error.printStackTrace();
                // try to have some fun with the error
            }
            return "";
        }
    }
}