package com.example.slice;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.squareup.picasso.Picasso;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
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
import java.util.Iterator;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyCallback;
import kaaes.spotify.webapi.android.SpotifyError;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Image;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity {

    // Spotify stuff
    private static final String CLIENT_ID = "71ea8dc10ab14aea83e374692c3fea85";
    private static final String REDIRECT_URI = "http://127.0.0.1:8000/";
    private static final int REQUEST_CODE = 1337;
    private String token = "";
    AuthenticationRequest.Builder builder =
            new AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URI);
    int offset = 0;
    int total_playlists = 0;

    // Pop up stuff
    private AlertDialog.Builder clearDataDialogBuilder;
    private AlertDialog clearDataDialog;



    // Recycler View Stuff
    RecyclerView playlistRecycler;
    RecyclerView.Adapter<FindPlaylist> playlistAdapter;
    ArrayList<Playlist> playlist_list = new ArrayList<>();
    NestedScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar and Appbar Stuff
        Toolbar toolbar = findViewById(R.id.toolbar_main_activity);;
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout =  findViewById(R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());
        toolBarLayout.setCollapsedTitleTypeface(Typeface.create("monospace", Typeface.BOLD));
        toolBarLayout.setExpandedTitleTypeface(Typeface.create("monospace", Typeface.BOLD));
        AppBarLayout appBar = findViewById(R.id.app_bar);
        appBar.setExpanded(false);


        // Recycler View init
        LinearLayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        playlistRecycler = findViewById(R.id.home_playlist_recycler);
        playlistRecycler.setLayoutManager(layoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(playlistRecycler.getContext(), layoutManager.getOrientation());
        playlistRecycler.addItemDecoration(dividerItemDecoration);
        scrollView = findViewById(R.id.main_nested_scroll);

        // Arraylist init
        builder.setScopes(new String[]{"streaming"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Clear button was pressed
            case R.id.action_clear:

                // Open dialog box to confirm clearing
                clearDataDialogBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog));
                final View popUpView = getLayoutInflater().inflate(R.layout.clear_all_slices_popup, null);
                Button confirm = popUpView.findViewById(R.id.clear_all_confirm);
                Button decline = popUpView.findViewById(R.id.clear_all_decline);

                // Delete all slice data
                confirm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clear();
                        Toast.makeText(getApplicationContext(), "Cleared Slice data", Toast.LENGTH_SHORT).show();
                        clearDataDialog.dismiss();
                    }
                });

                // Don't delete all slice data
                decline.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clearDataDialog.dismiss();
                    }
                });

                // Open dialog box
                clearDataDialogBuilder.setView(popUpView);
                clearDataDialog = clearDataDialogBuilder.create();
                clearDataDialog.show();

                return true;

            // Open about page
            case R.id.action_about:
                Snackbar.make(playlistRecycler, "About us page coming soon!", Snackbar.LENGTH_SHORT).show();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    // Get token
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);

            switch (response.getType()) {
                // Response was successful and contains auth token
                case TOKEN:
                    // Handle successful response
                    token = response.getAccessToken();
                    System.out.println("This is the state " + response.getState());
                    System.out.println(token);
                    InitArrayList();
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    System.out.println("Token wasn't retrieved");
                    System.out.println(response.getError());
                    Snackbar.make(playlistRecycler, "Unable to connect to Spotify", Snackbar.LENGTH_SHORT).show();
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
                    Snackbar.make(findViewById(R.id.content), "Trouble connecting to Spotify", Snackbar.LENGTH_SHORT).show();
                    System.out.println("IDK what happened with the token getting");
                    break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Connect to spotify acocunt
        if (!SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())) Snackbar.make(playlistRecycler, "Download Spotify to fully use Slice!", Snackbar.LENGTH_SHORT).show();

        // Init for adapter
        playlistAdapter = new RecyclerView.Adapter<FindPlaylist>() {
            @NonNull
            @Override
            public FindPlaylist onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                // attach individual list item to recycler view
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_template, parent, false);
                return new FindPlaylist(view);
            }

            @Override
            public void onBindViewHolder(@NonNull FindPlaylist holder, int position) {
                // init list item with data
                Playlist model = playlist_list.get(position);
                holder.name.setText(model.name);
                Picasso.get().load(model.imageUrl).into(holder.image);

                // on Click
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getApplicationContext(), PlaylistActivity.class);
                        intent.putExtra("playlistName", model.name);
                        intent.putExtra("playlistUri", model.uri);
                        intent.putExtra("playlistId", model.id);
                        intent.putExtra("token", token);
                        intent.putExtra("image", model.imageUrl);
                        startActivity(intent);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return playlist_list.size();
            }
        };

        playlistRecycler.setAdapter(playlistAdapter);
        System.out.println(playlistAdapter.getItemCount());

        // If the user is at the bottom of the list, do this
        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);

                int diff = (view.getBottom() - (scrollView.getHeight() + scrollView
                        .getScrollY()));

                if (diff == 0) {
                    boolean running = false;

                    // Check for any threads looking for playlists
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        // System.out.println(t.getName());
                        if (t.getName().equals("AddPlaylists")) {
                            running = true;
                            break;
                        }
                    }
                    // Look for new playlists and start a thread for it
                    if (!running && playlist_list.size() != total_playlists){
                        Snackbar.make(view, "Fetching more playlists", Snackbar.LENGTH_SHORT).show();
                        FindMorePlaylistsThread thread = new FindMorePlaylistsThread();
                        thread.setName("AddPlaylists");
                        thread.start();
                    }
                }
            }
        });

    }


    @Override
    protected void onStop() {
        super.onStop();
    }

    // Initialize the arraylist
    // Todo: replace the wrapper class with OKHTTP
    private void InitArrayList(){
        if (token.equals("")) {
            System.out.println("Token hasn't been initialized yet");
        }
        else {
            // Wrapper class stuff
            SpotifyApi api = new SpotifyApi();
            api.setAccessToken(token);
            SpotifyService spotify = api.getService();

            spotify.getMyPlaylists(new SpotifyCallback<Pager<PlaylistSimple>>() {
                @Override
                public void failure(SpotifyError spotifyError) {
                    System.out.println(spotifyError.getErrorDetails());
                    System.out.println("Couldn't get playlists");
                }

                @Override
                public void success(Pager<PlaylistSimple> playlistSimplePager, Response response) {
                    // Add the given playlists to the ArrayList
                    for (PlaylistSimple playlistSimple : playlistSimplePager.items) {
                        for (Image i :playlistSimple.images){
                            System.out.println(i.url);
                            Runnable r = new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        URL url = new URL(i.url);
                                        ImageView im = new ImageView(getApplicationContext());
                                        im.setImageBitmap(BitmapFactory.decodeStream(url.openConnection().getInputStream()));
                                    } catch (MalformedURLException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            new Thread(r).start();
                        }
                        String url = playlistSimple.images.get(0).url;

                        Playlist playlist = new Playlist(playlistSimple.uri, playlistSimple.name, playlistSimple.id, url);
                        playlist_list.add(playlist);
                        System.out.println(playlistSimple.name);
                    }
                    total_playlists = playlistSimplePager.total;
                    playlistAdapter.notifyDataSetChanged();
                    offset = playlist_list.size();

                    if (total_playlists == 0) Snackbar.make(playlistRecycler, "Make some playlists to use Slice!", Snackbar.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Todo: Create a logout system.
    private boolean logout(){
        // AuthenticationClient#clearCookies();
        return true;
    }


    public void clear(){
        // Clear all existing slices for the playlist
        try {
            String json = getSlices();
            JSONObject jsonObject = (json.equals("")) ? new JSONObject() : new JSONObject(json);
            if (json.equals("")) {
                System.out.println("couldn't find the json");
                return;
            }
            Iterator<String> keys = jsonObject.keys();
            ArrayList<String> uris = new ArrayList<>();
            while(keys.hasNext()) {
                String key = keys.next();
                uris.add(key);
            }
            System.out.println("about to remove");
            for( String s : uris){
                jsonObject.remove(s);
            }
            String o = jsonObject.toString();
            save(o);
        }
//        catch(java.util.ConcurrentModificationException e){
//            System.out.println("List was being modified somehow");
//            clear();
//        }
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





    // Adapter Helper Class
    public static class FindPlaylist extends RecyclerView.ViewHolder{
        TextView name;
        ImageView image;

        public FindPlaylist(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.song_template_name_textView);
            image = itemView.findViewById(R.id.playlist_template_imageView);
        }
    }

    // Thread to find playlists when user reaches bottom of list
    public class FindMorePlaylistsThread extends Thread{
        @Override
        public void run() {
            if (playlist_list.size() == total_playlists){
                return;
            }

            // Start connection with spotify api
            OkHttpClient client = new OkHttpClient();
            HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.spotify.com/v1/me/playlists").newBuilder();
            urlBuilder.addQueryParameter("offset", String.valueOf(offset));
            String url = urlBuilder.build().toString();
            Request request = new Request.Builder()
                    .header("Authorization", "Bearer " + token)
                    .url(url)
                    .build();

            try {
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful()){
                    try{
                        // Turn successful response into useable JSON object
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        if (jsonObject.has("items")){
                            JSONArray items = jsonObject.getJSONArray("items");

                            // Update ArrayList with new playlists
                            // Todo: Maybe add verification so the same playlist isn't added twice, may not be needed
                            for(int i = 0; i < items.length(); i ++){
                                JSONObject item = items.getJSONObject(i);
                                JSONArray images = item.getJSONArray("images");
                                JSONObject image = (JSONObject) images.get(0);
                                Playlist p = new Playlist(item.getString("uri"), item.getString("name"), item.getString("id"), image.getString("url"));
                                playlist_list.add(p);
                            }
                            offset = playlist_list.size();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    playlistAdapter.notifyDataSetChanged();
                                }
                            });
                        }
                    }
                    catch(JSONException je){
                        je.printStackTrace();
                    }
                }
                else{
                    throw new IOException("error' " + response);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}