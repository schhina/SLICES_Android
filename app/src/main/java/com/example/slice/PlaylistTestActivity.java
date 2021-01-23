package com.example.slice;

import android.os.Bundle;

import com.example.slice.adapter.MyAdapter;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class PlaylistTestActivity extends AppCompatActivity {

    RecyclerView test_recycler_view;
    ArrayList<Integer> test_list = new ArrayList<>();
    RecyclerView.Adapter<FindTest> adapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_test);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_playlist);
//        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(R.id.toolbar_layout);
        // toolBarLayout.setTitle(getTitle());

        test_recycler_view = findViewById(R.id.test_recycler_view);
        test_recycler_view.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

        initArrayList();

    }

    @Override
    protected void onStart() {
        super.onStart();

        adapter = new RecyclerView.Adapter<FindTest>() {
            @NonNull
            @Override
            public FindTest onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View itemView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.test_layout, parent, false);
                return new FindTest(itemView);
            }

            @Override
            public void onBindViewHolder(@NonNull FindTest holder, int position) {
                // holder.test.setText(test_list.get(position));

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Snackbar.make(view, "Snackbar is sick af", Snackbar.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return test_list.size();
            }
        };

        test_recycler_view.setAdapter(adapter);
    }

    public void initArrayList(){
        for(int i = 0; i < 20; i ++){
            test_list.add(i);
        }
    }

    public static class FindTest extends RecyclerView.ViewHolder{
        TextView test;
        public FindTest(@NonNull View itemView) {
            super(itemView);

            test = itemView.findViewById(R.id.test_textView);
        }
    }
}