package com.goyourfly.recyclerlearn;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by gaoyufei on 2017/11/21.
 * 通过这类，得出结论，如果想要在RecyclerView
 * 中控制View的位置是不合理的，也是不好实现的
 * 所以最好交给LayoutManager来实现
 */

public class MyRecyclerView extends RecyclerView {
    public MyRecyclerView(Context context) {
        super(context);
    }

    public MyRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void offsetChildrenVertical(int dy) {
        try {
            Class recycler = Class.forName("android.support.v7.widget.RecyclerView");
            Field field = recycler.getDeclaredField("mChildHelper");
            field.setAccessible(true);
            Object childHelperObj = field.get(this);
            Class childHelper = Class.forName("android.support.v7.widget.ChildHelper");
            Method getChildCount = childHelper.getDeclaredMethod("getChildCount");
            getChildCount.setAccessible(true);
            final int childCount = (int) getChildCount.invoke(childHelperObj);
            Method getChildAt = childHelper.getDeclaredMethod("getChildAt", Integer.TYPE);
            getChildAt.setAccessible(true);
            for (int i = 0; i < childCount; i++) {
                View view = (View) getChildAt.invoke(childHelperObj, i);
                RecyclerView.LayoutParams layoutParams = ((RecyclerView.LayoutParams) view.getLayoutParams());
                if(getAdapter().getItemViewType(layoutParams.getViewAdapterPosition()) == MainActivity.TYPE_CONTENT){
                    view.offsetTopAndBottom(dy);
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
}
