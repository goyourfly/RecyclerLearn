package com.goyourfly.recyclerlearn;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by gaoyufei on 2017/11/21.
 */

public class MyViewGroup extends ViewGroup {
    public MyViewGroup(Context context) {
        super(context);
    }

    public MyViewGroup(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                requestLayout();
                requestLayout();
                requestLayout();
            }
        });

    }

    public MyViewGroup(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyViewGroup(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int modeW = MeasureSpec.getMode(widthMeasureSpec);
        int modeH = MeasureSpec.getMode(heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (modeW == MeasureSpec.EXACTLY) {
            Log.d("MyViewGroup", "EXACTLY");
        } else if (modeW == MeasureSpec.AT_MOST) {
            Log.d("MyViewGroup", "AT_MOST");
        } else if (modeW == MeasureSpec.UNSPECIFIED) {
            Log.d("MyViewGroup", "UNSPECIFIED");
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = getChildAt(i);
            view.measure(widthMeasureSpec, heightMeasureSpec);
            Log.d("MyViewGroup", "parentSize:" + width + "," + height + " --- childSize:" + view.getMeasuredWidth() + "," + view.getMeasuredHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }
}
