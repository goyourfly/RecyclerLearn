package com.goyourfly.recyclerlearn;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

/**
 * Created by gaoyufei on 2017/11/22.
 */

public class MyLayoutManager extends RecyclerView.LayoutManager {
    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT);
    }


    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        fillViewPort(recycler, state);
    }

    /**
     * 之所以把对Child的排列单独一个方法是因为对Child的
     * 排列不光会在onLayoutChildren中使用
     * 还会在滑动的时候或者其他的地方使用
     * @param recycler
     * @param state
     */
    private void fillViewPort(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // 1.获取当前布局中的第一个的开始位置
        View firstChild = getChildAt(0);
        int firstTop = firstChild == null ? 0 : getDecoratedTop(firstChild);
        int firstPosition = firstChild == null ? 0 : getPosition(firstChild);
        int previewPosition = firstPosition - 1;
        //  第一个Item的上一个Item底部其实就是第一个元素的顶部
        int previewBottom = firstTop;
        log("[" + getChildCount() + "]" + firstTop + "," + firstPosition + "," + previewBottom);

        // 2.回收所有的Child
        detachAndScrapAttachedViews(recycler);


        // 3.准备开始布局，由于firstChild
        // 可能在整个RecyclerView的顶部，中间，甚至是底部
        // 所以从firstChild开始布局，有可能需要往上布局
        // 也有可能需要往下布局

        // 3.1 正向布局：既从上往下的布局
        int nextPosition = firstPosition;
        for (; firstTop < getHeight()
                && nextPosition >= 0
                && nextPosition < state.getItemCount(); nextPosition++) {
            firstTop += layoutItem(recycler, firstTop, nextPosition, false);
        }

        // 3.2 反向布局：从下往上布局
        int prevPosition = previewPosition;
        for (; previewBottom >= 0
                && prevPosition >= 0
                && prevPosition < state.getItemCount(); prevPosition--) {
            previewBottom -= layoutItem(recycler, previewBottom, prevPosition, true);
        }


        // 4.清理对用户不可见的View
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (getDecoratedBottom(child) < 0 || getDecoratedTop(child) > getHeight()) {
//                removeAndRecycleView(child,recycler);
                detachAndScrapView(child,recycler);
            }
        }
    }

    private int layoutItem(RecyclerView.Recycler recycler, int offset, int position, boolean revert) {
        int itemMeasureHeight = 0;
        View child = recycler.getViewForPosition(position);
        addView(child);
        measureChildWithMargins(child, 0, 0);
        itemMeasureHeight = getDecoratedMeasuredHeight(child);
        if (!revert) {
            layoutDecoratedWithMargins(child, 0, offset, getWidth(), offset + itemMeasureHeight);
        } else {
            layoutDecoratedWithMargins(child, 0, offset - itemMeasureHeight, getWidth(), offset);
        }
        return itemMeasureHeight;
    }

    private void log(String text) {
        Log.d("MyLayoutManager", text);
    }


    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null) {
                child.offsetTopAndBottom(-dy);
            }
        }
        fillViewPort(recycler, state);
        return -dy;
    }

}
