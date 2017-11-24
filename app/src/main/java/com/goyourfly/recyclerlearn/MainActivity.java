package com.goyourfly.recyclerlearn;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final int TYPE_SECTION = 1;
    public static final int TYPE_CONTENT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final RecyclerView recyclerView = findViewById(R.id.recycler);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setAdapter(new MyAdapter());
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setLayoutManager(new MyLayoutManager());
//        recyclerView.setLayoutManager(linearLayoutManager);

        // 添加分割线
        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, linearLayoutManager.getOrientation());
        recyclerView.addItemDecoration(itemDecoration);


        recyclerView.setHasFixedSize(true);
//        linearLayoutManager.setAutoMeasureEnabled(false);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("MainActivity", "AllChildCount:" + recyclerView.getChildCount());
                handler.postDelayed(this,1000);
            }
        },1000);
    }


    public static class MyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Integer> data = new ArrayList<>();

        public MyAdapter() {
            for (int i = 0; i < 50; i++) {
                data.add(i);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_CONTENT)
                return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recycler, parent, false));
            return new SectionViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_section, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

        }

        @Override
        public int getItemViewType(int position) {
            if (data.get(position) % 5 == 0) {
                return TYPE_SECTION;
            }
            return TYPE_CONTENT;
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public class MyViewHolder extends RecyclerView.ViewHolder {

            public MyViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int position = getAdapterPosition();
//                        data.remove(position);
//                        notifyItemRemoved(position);
                        notifyItemChanged(position);
                    }
                });
            }
        }

        public static class SectionViewHolder extends RecyclerView.ViewHolder {

            public SectionViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                    }
                });
            }
        }
    }
}
