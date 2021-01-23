package com.example.slice;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

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
    private SpotifyAppRemote mSpotifyAppRemote;
    private String token = "";
    AuthenticationRequest.Builder builder =
            new AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URI);
    int offset = 0;
    int total_playlists = 0;



    // Recycler View Stuff
    RecyclerView playlistRecycler;
    RecyclerView.Adapter<FindPlaylist> playlistAdapter;
    ArrayList<Playlist> playlist_list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Recycler View init
        LinearLayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        playlistRecycler = findViewById(R.id.home_playlist_recycler);
        playlistRecycler.setLayoutManager(layoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(playlistRecycler.getContext(), layoutManager.getOrientation());
        playlistRecycler.addItemDecoration(dividerItemDecoration);


        // Arraylist init
        builder.setScopes(new String[]{"streaming"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);


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
                    System.out.println(token);
                    InitArrayList();
                    break;

                // Auth flow returned an error
                case ERROR:
                    // Handle error response
                    System.out.println("Token wasn't retrieved");
                    System.out.println(response.getError());
                    break;

                // Most likely auth flow was cancelled
                default:
                    // Handle other cases
                    System.out.println("IDK what happened with the token getting");
                    break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Connect to spotify acocunt
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
                        Log.d("MainActivity", "Connected! Yay!");
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("MainActivity", throwable.getMessage(), throwable);
                    }
                });

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
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL url = new URL(model.imageUrl);
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

                // on Click
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(getApplicationContext(), model.name, Toast.LENGTH_SHORT).show();
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

        // If the user is at the bottom of the recycler View, do this
        playlistRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!playlistRecycler.canScrollVertically(1)){
                    boolean running = false;

                    // Check for any threads looking for playlists
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        System.out.println(t.getName());
                        if (t.getName().equals("AddPlaylists")) {
                            running = true;
                            break;
                        }
                    }
                    // Look for new playlists and start a thread for it
                    if (!running){
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
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
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
                }
            });
        }
    }

    // Method to go to the Settings page
    public void goToSettings(View v){
        startActivity(new Intent(getApplicationContext(), PlaylistTestActivity.class));
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