# RecyclerView 学习笔记

> RecyclerView version: 26.1.0

使用RecyclerView很久了，应该是从一出来就在使用吧，虽然大概的原理都懂，但是一直懒得看它的实现细节，最近想自己写几个LayoutManager，所以趁这个机会学习一下RecyclerView的源码，了解它的实现细节，这样写起LayoutManager来也会更得心应手吧。

#### 先来看看它有哪些主要的成员：

* Recycler mRecycler // 缓存管理者，final类型-不允许扩展
* LayoutManager mLayoutManager // 数据展示者
* RecyclerViewDataObserver mObserver // 数据观察者
* Adapter mAdapter // 数据提供者
* ItemAnimator mItemAnimator // 动画类
* ArrayList\<ItemDecoration\> mItemDecorations // 装饰类


### Recycler

Recycler是缓存管理者，它包含了几个缓存容器，一直没太明白mChangedScrap和mAttachedScrap的区别，或许区别不是特别大，谁要是知道了麻烦告诉我一声

* mChangedScrap：这里缓存的ViewHolder因为数据没有变化，所以可以直接使用
* mAttachedScrap：这里缓存的ViewHolder因为数据没有变化，所以可以直接使用
* mCachedViews：数据已经发生变化，重新使用需要调用Adapter.onBindViewHolder(...)
* mViewCacheExtension：用户自定义的缓存，通过RecyclerView.setViewCacheExtension(...)，通过这个类，可以实现用户自己管理RecyclerView的缓存
* mRecyclerPool：独立的ViewHolder缓冲池，可以多个RecyclerView共用，通过RecyclerView.setRecycledViewPool(...)设置

##### RecycledViewPool
RecyclerViewPool是一个Map，通过	`<Int(Type),ScrapData>` 这样的格式存储数据
也就是说，它和位置没有关系，只在乎Item的Type类型

````java
public static class RecycledViewPool {
	...
	// RecycledViewPool很简单，是一个缓冲池，
	SparseArray<ScrapData> mScrap = new SparseArray<>();
	...
}

````

##### Recycler的获取ViewHolder的机制是这样的：

````java
getViewForPosition(int position) ->
getViewForPosition(int position, boolean dryRun) ->
tryGetViewHolderForPositionByDeadline(int position,
                boolean dryRun, long deadlineNs){
                		// 实际的代码...
                }
````

* 首先，从mChangedScrap中查找是否存在需要的ViewHolder，如果有则直接返回，否则
* 从mAttachedScrap中查找是否存在需要的ViewHolder，否则
* 从mCachedViews中查找是否存在需要的ViewHolder，否则
* 检查mViewCacheExtension是否不为空，如果是，从mViewCacheExtension中获取ViewHolder，否则
* 从RecyclerViewPool中获取ViewHolder
* 如果这个时候ViewHolder还是null，则从mAdapter.createViewHolder()创建一个ViewHolder

##### Recycler缓存ViewHolder的机制是这样的：
* 共有三种类型的缓存
* A.通过mAttachedScrap或者mChangedScrap列表缓存
* B.通过mCachedViews列表缓存
* C.通过mRecyclerPool缓存

> 这里忽略了 mViewCacheExtension

这三种缓存方式的区别是：

* A方式缓存的ViewHolder所包含的数据是有效的，如位置、View中的值等等
* B方式缓存的ViewHolder数据已经是无效的，需要重新调用bindViewHolder进行数据绑定。
* C方式缓存的ViewHolder是在B方式无法缓存的情况向才会缓存到C中，数据已经无效，需要重新调用bindViewHolder进行数据绑定，但是这种方式可以实现多个RecyclerView共同一个缓存池

Recycler对外分别提供了两个方法进行数据的缓存：

* 1:`scrapView(View view)` 根据一定的Policy将ViewHolder缓存到`mAttachedScrap`或者`mChangedScrap`中
* 2:`recycleView(View view)` 将ViewHolder缓存到mCachedViews中，如果缓存失败了，则将ViewHolder缓存到mRecyclerPool中

### LayoutManager

LayoutManager的主要工作是measure和position item views，以及根据一定的规则来确定是否回收不再对用户可见item view

* A:Child的measure，既尺寸和布局边界的计算
* B:Child的layout，既位置的摆放和移动
* C:Child的回收管理，既何时回收，何时生成
* D:Child何时add/attach和remove/detach到RecyclerView中


##### RecyclerView的measure
> 先来看看RecyclerView是如何计算自己的尺寸的

LayoutManager是RecyclerView的内部类，它没有继承任何的父类，所以，对于Android的View系统来说，LayoutManager是个什么玩意它根本不知道，RecyclerView才是属于View树中的一员，measure方法也只会传递到RecyclerView，那LayoutManager又是怎么处理Child的measure呢？

其实，原理特别简单，就是RecyclerView把一部分measure的工作交给LayoutManager去处理，相当于将Child的measure工作交给了LayoutManager，具体的实现是，RecyclerView在自己的`onMeasure(int widthSpec,int heightSpec)`方法中调用LayoutManager的`onLayoutChildren(Recycler recycler,State state)`，虽然原理听起来很简单，但是实现细节是很复杂，我简单的梳理一下，删除了很多细节：

````java
@Override
protected void onMeasure(int widthSpec, int heightSpec) {
	...
    if (mLayout.mAutoMeasure) {
        final int widthMode = MeasureSpec.getMode(widthSpec);
        final int heightMode = MeasureSpec.getMode(heightSpec);
        final boolean skipMeasure = widthMode == MeasureSpec.EXACTLY
                && heightMode == MeasureSpec.EXACTLY;
        // 根据Mode判断是否需要跳过measure，
        // 第一次进来的 mode 一般是MeasureSpec.UNSPECIFIED，
        // 既父类想知道RecyclerView想要多大空间，所以不会
        // 跳过measure过程
        mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
        if (skipMeasure || mAdapter == null) {
            return;
        }
        // mState是一个状态记录的类，包含很多和RecyclerView
        // 状态相关的属性，由于onMeasure会执行多次，每次的measure
        // 做的工作可能不同，所以用 mLayoutStep
        // 记录当前measure或者layout是那种类型，一共有三种
        // STEP_START:初次measure
        // STEP_LAYOUT:第二次measure
        // STEP_ANIMATIONS:处理动画
        // 如果初次进来，调用相应的第一步对应的方法，下面
        // 分析了这个方法
        if (mState.mLayoutStep == State.STEP_START) {
            dispatchLayoutStep1();
        }
        // set dimensions in 2nd step. Pre-layout should happen with old dimensions for
        // consistency
        mLayout.setMeasureSpecs(widthSpec, heightSpec);
        mState.mIsMeasuring = true;
        // 执行第二步Layout
        dispatchLayoutStep2();

        // 经过第二次Measure之后，RecyclerView的Child自身尺寸已经
        // 计算出来了，所以我们可以根据Child的尺寸重新调整RecyclerView
        // 自己的尺寸了
        mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);
    } else {
    	...
        // 现在的LayoutManager一般都走得上面那个分支，
        // 既 mAutoMeasure=true，所以不介绍这个分支
    }
}


/**
 * 这个方法主要做下面几件事情：
 * - 处理Adapter的刷新
 * - 计算动画
 * - 保存当前View状态
 * - 如果需要，执行预布局并保存这些信息
 */
private void dispatchLayoutStep1() {
    ...
    if (mState.mRunSimpleAnimations) {
        // 如果RecyclerView当前状态是执行简单动画，比如滑动时候的动画
        // 动画这块的处理后面会讲到
        int count = mChildHelper.getChildCount();
        for (int i = 0; i < count; ++i) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
            ...
            // 通过ItemAnimator获取当前状态的一些信息，
            // 一般是些位置信息
            final ItemHolderInfo animationInfo = mItemAnimator
                    .recordPreLayoutInformation(mState, holder,
                            ItemAnimator.buildAdapterChangeFlagsForAnimations(holder),
                            holder.getUnmodifiedPayloads());
            // 将这些信息存到ViewInfoStore的mLayoutHolderMap中
            // 以后在正式动画的时候，这些信息将作为动画开始的状态
            mViewInfoStore.addToPreLayout(holder, animationInfo);
            ...
        }
    }
    // 这块处理的是对Item执行添加、移除、调整位置的动画
    if (mState.mRunPredictiveAnimations) {
        // 由于这些动画一般都是Item的位置发生变化，所以要
        // 先保存Item原来的位置，遍历所有的ViewHolder
        // 让mOldPosition=mPosition
        saveOldPositions();
        final boolean didStructureChange = mState.mStructureChanged;
        mState.mStructureChanged = false;
        // 执行一次layout以便计算出ViewHolder最新的位置
        mLayout.onLayoutChildren(mRecycler, mState);
        mState.mStructureChanged = didStructureChange;

        for (int i = 0; i < mChildHelper.getChildCount(); ++i) {
            final ViewHolder viewHolder = getChildViewHolderInt(child);
            // 获取最新ViewHolder的位置和状态信息
            final ItemHolderInfo animationInfo = mItemAnimator.recordPreLayoutInformation(
                    mState, viewHolder, flags, viewHolder.getUnmodifiedPayloads());
            // 将这些信息保存到mViewInfoStore
            // 至此，ViewHolder的起始位置和终止位置都已经
            // 计算完成，只需要在合适的地方触发动画即可
            if (wasHidden) {
                recordAnimationInfoIfBouncedHiddenView(viewHolder, animationInfo);
            } else {
                mViewInfoStore.addToAppearedInPreLayoutHolders(viewHolder, animationInfo);
            }
        }
        clearOldPositions();
    } else {
        clearOldPositions();
    }
    ...
    // 将mLayoutStep置为STEP_LAYOUT，静候下一次onMeasure
    mState.mLayoutStep = State.STEP_LAYOUT;
}


private void dispatchLayoutStep2() {
    // 如果当前正在执行动画，则直接结束动画，将位置置为最终位置
    mAdapterHelper.consumeUpdatesInOnePass();
    // 再执行一次LayoutChildren
    mState.mInPreLayout = false;
    mLayout.onLayoutChildren(mRecycler, mState);

   	...

    // 将mLayoutStep状态置为STEP_ANIMATIONS
    mState.mLayoutStep = State.STEP_ANIMATIONS;
}

````
接着，RecyclerView的onLayout会执行，然后调用第三步Layout

````java
private void dispatchLayoutStep3() {
    ...
    // 将mLayoutStep置为STEP_START
    mState.mLayoutStep = State.STEP_START;
    if (mState.mRunSimpleAnimations) {
    	...
        // 执行动画
        mViewInfoStore.process(mViewInfoProcessCallback);
    }

	 // 回收垃圾
    mLayout.removeAndRecycleScrapInt(mRecycler);
    ...
    // 调用onLayoutCompleted
    // 到这里，一个measure轮回就算完成
    mLayout.onLayoutCompleted(mState);
    ...
}
````

通过上面的代码，大概能知道onMeasure这一个过程，但是有几点需要注意：

* 1.onMeasure的职责是计算RecyclerView的尺寸，在它内部调用了dispachLayoutStep\*()这三个方法也是为了通过Item的大小来确定RecyclerView的尺寸，
* 2.dispachLayoutStep\*()这几个方法在其他的地方也会调用
在列表滑动的时候，不会触发onMeasure，onMeasure只会在

我们知道Android的View系统是树形结构，不管是在布局还是事件传递都是从树的根节点开始往枝叶传递，所以绘制的流程一般是这样的：
measure过程：

ViewGroup.onMeasure() -> ViewGroup.onMeasure() -> View.onMeasure()

这个过程一般会执行两遍，
举个简单的例子，地主家分地，首先，父亲问所有的儿子，你想要多少亩地，爹就给你多少，然后儿子们就会很傻的告诉父亲，我想要多少多少（其实没有告诉，只是自己记下来，但是父亲能看到），父亲等儿子们计算完成后，默默的掏出算盘算了一下，然后发现，唉，儿子太多，这地不够分呀，于是自己又定了个规则，大儿子最多给多少，二儿子给多少等等，这样地才够分，于是乎，儿子们又会在这个上限范围内重新计算一次，算算也够吃，就这样吧。

所以onMeasure()一般最少会执行两遍，等onMeasure执行完成后，父亲就会根据每个儿子分的地尺寸，执行具体的分配(onLayout)，如大儿子，你最大，给你山那头最大的那一块地吧，小儿子，你最可爱，给你那块水地吧，等等，所以其实程序中的很多逻辑跟现实逻辑是相通的。

扯远了，我觉得RecyclerView有意混淆onMeasure和onLayout的过程，在它内部，布局就是那三个Step，不管是在onMeasure调用还是onLayout调用、或者是其他地方调用，反正就这三步，按顺序走完。


##### LayoutManagerd的onLayoutChildren

`onLayoutChildren`: 顾名思义，就是对RecyclerView中ChildView的管理，既Cell或者说是Item，它主要负责对ChildView在RecyclerView中的布局，如果直接看LinearLayoutManager这类已经写好的LayoutManager太复杂，因为系统提供的LayoutManager需要考虑的东西特别多，所以我决定从头写一个，一点一点的完成一个最小但是可用的LayoutManager。

自定义一个LayoutManager需要你继承一下LayoutManager：

````java
public class MyLayoutManager extends RecyclerView.LayoutManager{}
````
由于LayoutManager是一个抽象类，有一个抽象方法是必须重写的：

````java
public class MyLayoutManager extends RecyclerView.LayoutManager {
    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT);
    }
}
````

OK，这就是最小的自定义LayoutManager，然后你就可以高高兴兴的把它设置到RecyclerView中，点击编译运行，装到手机上试一下，然后你就会发现什么也没有，额，有才怪呢。

上面刚刚说了，LayoutManager的onLayoutChildren是负责ChildView的布局，由于我们没有重写这个方法，而父类也只是个空实现，对ChildView我们什么也没有做，所以就什么都不会显示喽...

那我们看看怎么写这个onLayoutChildren

````java
// 先来看看这个方法的参数
// recycler：上面介绍过，负责ChildView的回收
// state：保存一些RecyclerView的状态
@Override
public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
    fillViewport(recycler, state);
}

/**
 * 这个方法的作用就是将RecyclerView中用户可见部分填满
 */
void fillViewport(RecyclerView.Recycler recycler, RecyclerView.State state){
	// 当执行到这里呢，有可能RecyclerView一个ChildView都没有
	// 也有可能已经有了，所以我们先获取一下childCount
    int childCount = getChildCount();
    // 1.获取第一个ChildView的信息，如果没有，默认是0
    int firstChildTop = 0;
    int firstPosition = 0;
    if(childCount > 0){
    	View firstChild = getChildAt(0);
    	firstChildTop = getDecoratedTop(firstChild);
    	firstPosition = getPosition(firstChild);
    }

    // 2.暂时回收所有的ChildView
    detachAndScrapAttachedViews(recycler);

    // 我们想实现LinearLayoutManager的效果，
    // 所以要把RecyclerView填满，既让它的ChildView
    // 从上往下填满RecyclerView，所以我们从第一个
    // firstPosition开始往下排列ChildView

    // 3.准备开始布局，由于firstChild
    // 可能在整个RecyclerView的顶部，中间，甚至是底部
    // 所以从firstChild开始布局，有可能需要往上布局
    // 也有可能需要往下布局
    // 3.1 向下布局
    int nextPosition = firstPosition;
    int nextTop = firstChildTop;
    for (; nextTop < getHeight()
            && nextPosition >= 0
            && nextPosition < state.getItemCount(); nextPosition++) {
        // 从第一位置开始，一个Item一个Item的往下填满RecyclerView
        View child = recycler.getViewForPosition(nextPosition);
        addView(child);
        // addView完成以后，调用下measure测量一下这个Child想要多大的空间
        measureChildWithMargins(child, 0, 0);
        // 用这个方法获取measureHeight会把Decorated也加上，所以最好用
        // 这个方法获取高度
        int itemMeasureHeight = getDecoratedMeasuredHeight(child);
        // 将ChildView放在对应的位置
        layoutDecoratedWithMargins(child, 0, nextTop, getWidth(), nextTop + itemMeasureHeight);
        // 记得累加一下nextTop的位置
        nextTop += itemMeasureHeight;
    }

    // 3.2 向上布局
    int prevPosition = firstPosition - 1;
    int preBottom = firstChildTop;
    for (; previewBottom >= 0
            && prevPosition >= 0
            && prevPosition < state.getItemCount(); prevPosition--) {
        // 从第一位置开始，一个Item一个Item的往上填满RecyclerView
        View child = recycler.getViewForPosition(prevPosition);
        addView(child);
        // addView完成以后，调用下measure测量一下这个Child想要多大的空间
        measureChildWithMargins(child, 0, 0);
        // 用这个方法获取measureHeight会把Decorated也加上，所以最好用
        // 这个方法获取高度
        int itemMeasureHeight = getDecoratedMeasuredHeight(child);
        // 将ChildView放在对应的位置
        layoutDecoratedWithMargins(child, 0, preBottom - itemMeasureHeight, getWidth(), preBottom);
        // 记得累加一下preBottom的位置
        preBottom -= itemMeasureHeight;
    }

    // 4.清理对用户不可见的View
    for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (getDecoratedBottom(child) < 0 || getDecoratedTop(child) > getHeight()) {
            removeAndRecycleView(child,recycler);
        }
    }
}
````

到这里，简单的布局就完成了，运行一下看看也好像没什么问题，但是就是不会滑动，如果要处理滑动，我们需要处理另外的一个方法：

````java
// 首先，告诉RecyclerView，我支持上下滑动
@Override
public boolean canScrollVertically() {
    return true;
}

// 然后，在用户上下滑动的时候，这个方法就会被执行
@Override
public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
	// 遍历所有的ChildCount，移动它们的位置
    for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (child != null) {
            child.offsetTopAndBottom(-dy);
        }
    }
    // 滑动的时候，也要调用fillViewPort处理布局
    fillViewPort(recycler, state);
    return -dy;
}
````

至此呢，一个最最最简陋的LayoutManager就算完工，如果需要一些复杂的效果，就需要你做一些自己的加工处理。

### RecyclerViewDataObserver

我们知道RecyclerView的数据是来自Adapter的，而展示数据则是由LayoutManager负责，做动画由ItemAnimator负责，那当数据源发生变化的时候，如何把这个信息告诉RecyclerView和各个组件呢？这个时候RecyclerViewDataObserver就派上用场了。

我们在向RecyclerView插入一条数据的时，一般都会调用Adapter的notifyItemInserted()，那我们就顺着这条线，看看RecyclerView是如何传递这一个事件的：
首先，RecyclerViewDataObserver是RecyclerView的一个属性，我们在调用RecyclerView.setAdapter()的时候，RecyclerView会把这个属性注入到Adapter中去：

````java
public void registerAdapterDataObserver(AdapterDataObserver observer) {
    mObservable.registerObserver(observer);
}
````
> AdapterDataObservable这个类很简单，就是做几个简单的封装

也就是说，相对有Adapter来说，RecyclerView是观察者，而Adapter是被观察者。
当Adapter.notifyItemInserted()被调用时，流程大概是这样的：

````java
Adapter.notifyItemInserted() ->
AdapterDataObservable.notifyItemInserted() ->
RecyclerViewDataObserver.onItemRangeInserted() ->
// 这个方法会记录刷新前Item的位置信息，动画的真正执行在
// 上面介绍步骤的dispatchLayoutStep3()
AdapterHelper.onItemRangeInserted() ->
RecyclerViewDataObserver.triggerUpdateProcessor() ->
RecyclerView.consumePendingUpdateOperations ->
RecyclerView.dispatchLayout()
````

### ItemAnimator

> 再来看一下RecyclerView动画相关的东东

上面介绍过事件的传递，知道了插入一条数据后，AdapterHelper.onItemRangeInserted()方法会记录动画之前的位置信息，所以我们来看一下这个方法：

````java
/**
 * RecyclerViewDataObserver.onItemRangeInserted
 */
@Override
public void onItemRangeInserted(int positionStart, int itemCount) {
    if (mAdapterHelper.onItemRangeInserted(positionStart, itemCount)) {
        // 触发更新
        triggerUpdateProcessor();
    }
}

/**
 * AdapterHelper.onItemRangeInserted
 */
boolean onItemRangeInserted(int positionStart, int itemCount) {
    if (itemCount < 1) {
        return false;
    }
    mPendingUpdates.add(obtainUpdateOp(UpdateOp.ADD, positionStart, itemCount, null));
    // 记录一下当前需要执行的动画类型，可能会有多个
    mExistingUpdateTypes |= UpdateOp.ADD;
    return mPendingUpdates.size() == 1;
}

/**
 * 这个方法就是用来记录动画前的状态信息：位置、动画类型、item数量
 * 将这些信息缓存到mPendingUpdates
 * 由于这里可能需要频繁的创建和销毁UpdateOp对象，所以用了一个对象池的概念
 */
@Override
public UpdateOp obtainUpdateOp(int cmd, int positionStart, int itemCount, Object payload) {
    UpdateOp op = mUpdateOpPool.acquire();
    if (op == null) {
        op = new UpdateOp(cmd, positionStart, itemCount, payload);
    } else {
        op.cmd = cmd;
        op.positionStart = positionStart;
        op.itemCount = itemCount;
        op.payload = payload;
    }
return op;
}

void triggerUpdateProcessor() {
	// 这里有两个分支，如果调用RecyclerView.setHasFixedSize()，
	// 则会执行上面这个分支，否则走下面的，基本上没啥区别，只是上面的
	// 多做了一些处理，但最后都会调用requestLayout();
    if (POST_UPDATES_ON_ANIMATION && mHasFixedSize && mIsAttached) {
    	// 通过post的好处是，这个动画会在16秒这样的垂直同步帧上执行
    	// 具体可以查一下Android垂直同步
        ViewCompat.postOnAnimation(RecyclerView.this, mUpdateChildViewsRunnable);
    } else {
        mAdapterUpdateDuringMeasure = true;
        requestLayout();
    }
}

/**
 * 在上面好像有介绍这个方法，动画相关的处理
 * 在 step1 和 step3 ，所以我们先看看第一步中动画相关的部分
 */
void dispatchLayout() {
    ...
    mState.mIsMeasuring = false;
    if (mState.mLayoutStep == State.STEP_START) {
        dispatchLayoutStep1();
        mLayout.setExactMeasureSpecsFrom(this);
        dispatchLayoutStep2();
    } else if (mAdapterHelper.hasUpdates() || mLayout.getWidth() != getWidth()
            || mLayout.getHeight() != getHeight()) {
        // First 2 steps are done in onMeasure but looks like we have to run again due to
        // changed size.
        mLayout.setExactMeasureSpecsFrom(this);
        dispatchLayoutStep2();
    } else {
        // always make sure we sync them (to ensure mode is exact)
        mLayout.setExactMeasureSpecsFrom(this);
    }
    dispatchLayoutStep3();
}


private void dispatchLayoutStep1() {
    ...

    if (mState.mRunSimpleAnimations) {
        // 在布局前，先记录View的位置
        int count = mChildHelper.getChildCount();
        for (int i = 0; i < count; ++i) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
            ...
            // ItemAnimator是一个动画执行类，所以先用它记录下
            // 它需要的信息，每个ItemAnimator的子类都可以自己
            // 想记录哪些信息
            final ItemHolderInfo animationInfo = mItemAnimator
                    .recordPreLayoutInformation(mState, holder,
                            ItemAnimator.buildAdapterChangeFlagsForAnimations(holder),
                            holder.getUnmodifiedPayloads());
            // 将这些信息缓存到mViewInfoStore中
            // 注意这里是addToPreLayout
            mViewInfoStore.addToPreLayout(holder, animationInfo);
            ...
        }
    }
    ...
}

/**
 * 根据ViewHolder的状态生成对应的动画FLAG
 */
static int buildAdapterChangeFlagsForAnimations(ViewHolder viewHolder) {
    int flags = viewHolder.mFlags & (FLAG_INVALIDATED | FLAG_REMOVED | FLAG_CHANGED);
    if (viewHolder.isInvalid()) {
        return FLAG_INVALIDATED;
    }
    if ((flags & FLAG_INVALIDATED) == 0) {
        final int oldPos = viewHolder.getOldPosition();
        final int pos = viewHolder.getAdapterPosition();
        if (oldPos != NO_POSITION && pos != NO_POSITION && oldPos != pos) {
            flags |= FLAG_MOVED;
        }
    }
    return flags;
}


/**
 * 这一步则是在onLayoutChildren()之后执行的
 * 主要处理一些动画信息以及善后工作
 */
private void dispatchLayoutStep3() {
    ...
    mState.mLayoutStep = State.STEP_START;
    if (mState.mRunSimpleAnimations) {
        for (int i = mChildHelper.getChildCount() - 1; i >= 0; i--) {
            ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
            long key = getChangedHolderKey(holder);
            // 获取View现在的状态，既layout之后的位置信息
            final ItemHolderInfo animationInfo = mItemAnimator
                    .recordPostLayoutInformation(mState, holder);
            // 将现在的位置信息存入mViewInfoStore
            // 注意这里是addToPostLayout
            mViewInfoStore.addToPostLayout(holder, animationInfo);
        }

        // 触发动画执行
        mViewInfoStore.process(mViewInfoProcessCallback);
    }

    // 一些善后工作
    ...
}

/**
 * 根据InfoRecord中flag的不同执行对应的动画
 */
void process(ProcessCallback callback) {
	// 为什么要从列表的末尾开始执行呢，因为要执行：
	// mLayoutHolderMap.removeAt(index);
	// 所以最好从后面逆序遍历
    for (int index = mLayoutHolderMap.size() - 1; index >= 0; index--) {
        final ViewHolder viewHolder = mLayoutHolderMap.keyAt(index);
        final InfoRecord record = mLayoutHolderMap.removeAt(index);
        ...
        // 以Insert为例
        else if ((record.flags & FLAG_POST) != 0) {
            //
            callback.processAppeared(viewHolder, record.preInfo, record.postInfo);
        }
        ...
        InfoRecord.recycle(record);
    }
}

/**
 * RecyclerView.animateAppearance
 */
void animateAppearance(@NonNull ViewHolder itemHolder,
        @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
    // 动画期间不可回收
    itemHolder.setIsRecyclable(false);
    if (mItemAnimator.animateAppearance(itemHolder, preLayoutInfo, postLayoutInfo)) {
        postAnimationRunner();
    }
}

/**
 * SimpleItemAnimator
 */
@Override
public boolean animateAppearance(@NonNull ViewHolder viewHolder,
        @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
		// ...
        return animateAdd(viewHolder);
    }
}

/**
 * DefaultItemAnimator
 */
@Override
public boolean animateAdd(final ViewHolder holder) {
    resetAnimation(holder);
    // O__O "… 额，add的动画就是把View透明的置为0？
    holder.itemView.setAlpha(0);
    // 由于可能同时执行的有很多个动画，所以我们将下一帧执行
    // 前多有Add类型的动画先暂存到mPendiingAdditions中
    // 同理还有mRemoveAnimations等
    mPendingAdditions.add(holder);
    return true;
}

/**
 * RecyclerView
 */
void postAnimationRunner() {
	// 紧接着马上将动画发射出去
    if (!mPostedAnimatorRunner && mIsAttached) {
        ViewCompat.postOnAnimation(this, mItemAnimatorRunner);
        mPostedAnimatorRunner = true;
    }
}

/**
 * DefaultItemAnimator
 */
@Override
public void runPendingAnimations() {
    boolean removalsPending = !mPendingRemovals.isEmpty();
    boolean movesPending = !mPendingMoves.isEmpty();
    boolean changesPending = !mPendingChanges.isEmpty();
    boolean additionsPending = !mPendingAdditions.isEmpty();
    if (!removalsPending && !movesPending && !additionsPending && !changesPending) {
        // nothing to animate
        return;
    }
    // First, remove stuff
    ...
    // Next, move stuff
    ...
    // Next, change stuff, to run in parallel with move animations
    ...
    // Next, add stuff
    // 还是以Add为例
    if (additionsPending) {
        final ArrayList<ViewHolder> additions = new ArrayList<>();
        additions.addAll(mPendingAdditions);
        mAdditionsList.add(additions);
        mPendingAdditions.clear();
        Runnable adder = new Runnable() {
            @Override
            public void run() {
                for (ViewHolder holder : additions) {
                		// 遍历执行所有的Add动画
                    animateAddImpl(holder);
                }
                additions.clear();
                mAdditionsList.remove(additions);
            }
        };
        ...
        adder.run();
    }
}

/**
 * DefaultItemAnimator
 * 正式执行add动画
 */
void animateAddImpl(final ViewHolder holder) {
    final View view = holder.itemView;
    // 看到这里应该恍然大悟了把，其实RecyclerView的Item动画
    // 只不过是在itemView上执行的属性动画，既然是这样的，那我们
    // 是不是就可以在itemView上做些坏坏的事情呢？
    final ViewPropertyAnimator animation = view.animate();
    mAddAnimations.add(holder);
    animation.alpha(1).setDuration(getAddDuration())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animator) {
                    dispatchAddStarting(holder);
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                    view.setAlpha(1);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    animation.setListener(null);
                    dispatchAddFinished(holder);
                    mAddAnimations.remove(holder);
                    dispatchFinishedWhenDone();
                }
            }).start();
}
````

### ItemDecoration

> Decoration：装饰，修饰

ItemDecoration是负责对RecyclerView进行装饰的类，如添加分割线，调整间距等，
下面这个类就是ItemDecoration基类，这个类的前两个方法是用来将画一些东西在RecyclerView的上面或者下面，最后一个是调整Item的间距

````java
/**
 * 这个类是负责实现ItemDecoration的，看起来很简单
 */
public abstract static class ItemDecoration {

    /**
     * 在Item的下层绘制
     */
    public void onDraw(Canvas c, RecyclerView parent, State state) {

    }
    /**
     * 在Item的上层绘制
     */
    public void onDrawOver(Canvas c, RecyclerView parent, State state) {

    }

	/**
    * 计算Item上下左右间隙，这些间隙的信息在计算ChildView的位置的时候
    * 非常重要，所以我们上面在获取ChildView高度的时候总是用
    * getDecoratedMeasuredXXX()，而不是直接调用View.getMeasureXXX()
    */
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {

    }
}


/**
 * RecyclerView
 */
@Override
public void draw(Canvas c) {
    super.draw(c);
	// super.draw() 会出发RecyclerView.onDraw(c)
	// 所以，下面的onDraw会先执行，然后执行这里的onDrawOver
	// onDrawOver是等所有的子View绘制完成后执行的，所以
	// 在onDrawOver方法执行的绘制会在View的上方
    final int count = mItemDecorations.size();
    for (int i = 0; i < count; i++) {
        mItemDecorations.get(i).onDrawOver(c, this, mState);
    }
    ...
}

/**
 * RecyclerView
 */
@Override
public void onDraw(Canvas c) {
    super.onDraw(c);
    final int count = mItemDecorations.size();
    for (int i = 0; i < count; i++) {
        mItemDecorations.get(i).onDraw(c, this, mState);
    }
}
````

到此呢，RecyclerView就分析的差不多了，由于RecyclerView代码实在是太多了，足足有一万三千多行，所以其中的一些实现细节都没有提，如果想要详细认识RecyclerView，一定要结合源码看，否则还是只能看个大概，另外，如果文中有什么错误，欢迎指正。


#### THE END ####
