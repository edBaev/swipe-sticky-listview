package ed.swipestickylistview;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

/**
 * @author Emil Sjölander
 * @author Eduard Baev
 */
@SuppressLint("NewApi")
public class StickyHeadersSwipeToDismissListView extends ListView implements
		OnClickListener {

	public interface OnHeaderClickListener {
		public void onHeaderClick(StickyHeadersSwipeToDismissListView l,
				View header, int itemPosition, long headerId,
				boolean currentlySticky);
	}

	private View headerView;
	/**
	 * Used when user want change swipe list mode on some rows
	 */
	public final static int SWIPE_MODE_DEFAULT = -1;

	/**
	 * Disables all swipes
	 */
	public final static int SWIPE_MODE_NONE = 0;

	/**
	 * Enables both left and right swipe
	 */
	public final static int SWIPE_MODE_BOTH = 1;

	/**
	 * Enables right swipe
	 */
	public final static int SWIPE_MODE_RIGHT = 2;

	/**
	 * Enables left swipe
	 */
	public final static int SWIPE_MODE_LEFT = 3;

	/**
	 * Binds the swipe gesture to reveal a view behind the row (Drawer style)
	 */
	public final static int SWIPE_ACTION_REVEAL = 0;

	/**
	 * Dismisses the cell when swiped over
	 */
	public final static int SWIPE_ACTION_DISMISS = 1;

	/**
	 * Marks the cell as checked when swiped and release
	 */
	public final static int SWIPE_ACTION_CHECK = 2;

	/**
	 * No action when swiped
	 */
	public final static int SWIPE_ACTION_NONE = 3;

	/**
	 * Indicates no movement
	 */
	private final static int TOUCH_STATE_REST = 0;

	/**
	 * State scrolling x position
	 */
	private final static int TOUCH_STATE_SCROLLING_X = 1;

	/**
	 * State scrolling y position
	 */
	private final static int TOUCH_STATE_SCROLLING_Y = 2;

	private int touchState = TOUCH_STATE_REST;

	private float lastMotionX;
	private float lastMotionY;
	private int touchSlop;

	int swipeFrontView = 0;
	int swipeBackView = 0;

	/**
	 * Internal listener for common swipe events
	 */
	private BaseSwipeStickyListViewListener swipeListViewListener;

	/**
	 * Internal touch listener
	 */
	private StickyHeadersSwipeToDismissTouchListener touchListener;

	/**
	 * If you create a View programmatically you need send back and front
	 * identifier
	 * 
	 * @param context
	 *            Context
	 * @param swipeBackView
	 *            Back Identifier
	 * @param swipeFrontView
	 *            Front Identifier
	 */

	private boolean areHeadersSticky = true;
	private int dividerHeight;
	private Drawable divider;
	private boolean clippingToPadding;
	private boolean clipToPaddingHasBeenSet;
	private Long currentHeaderId = null;
	private StickyHeadersSwipeToDismissAdapterWrapper adapter;
	private OnHeaderClickListener onHeaderClickListener;
	private int headerPosition;
	private ArrayList<View> footerViews;
	private StickyHeadersSwipeToDismissListViewWrapper frame;
	private boolean drawingListUnderStickyHeader = true;
	private boolean dataChanged = false;
	private boolean drawSelectorOnTop;
	private OnItemLongClickListener onItemLongClickListenerDelegate;

	private DataSetObserver dataSetChangedObserver = new DataSetObserver() {

		@Override
		public void onChanged() {
			dataChanged = true;
			currentHeaderId = null;
		}

		@Override
		public void onInvalidated() {
			currentHeaderId = null;
			frame.removeHeader();
		}
	};
	private OnItemLongClickListener onItemLongClickListenerWrapper = new OnItemLongClickListener() {

		@Override
		public boolean onItemLongClick(AdapterView<?> l, View v, int position,
				long id) {
			if (onItemLongClickListenerDelegate != null) {
				return onItemLongClickListenerDelegate.onItemLongClick(l, v,
						adapter.translateListViewPosition(position), id);
			}
			return false;
		}

	};

	public StickyHeadersSwipeToDismissListView(Context context) {
		this(context, null);
	}

	public StickyHeadersSwipeToDismissListView(Context context,
			AttributeSet attrs) {
		this(context, attrs, android.R.attr.listViewStyle);
		init(attrs);
	}

	/**
	 * Init ListView
	 * 
	 * @param attrs
	 *            AttributeSet
	 */
	private void init(AttributeSet attrs) {
		int swipeMode = SWIPE_MODE_BOTH;
		boolean swipeOpenOnLongPress = true;
		boolean swipeCloseAllItemsWhenMoveList = true;
		long swipeAnimationTime = 0;
		float swipeOffsetLeft = 0;
		float swipeOffsetRight = 0;

		int swipeActionLeft = SWIPE_ACTION_REVEAL;
		int swipeActionRight = SWIPE_ACTION_REVEAL;
		if (attrs != null) {
			TypedArray styled = getContext().obtainStyledAttributes(attrs,
					R.styleable.SwipeListView);
			swipeMode = styled.getInt(R.styleable.SwipeListView_swipeMode,
					SWIPE_MODE_BOTH);
			swipeActionLeft = styled.getInt(
					R.styleable.SwipeListView_swipeActionLeft,
					SWIPE_ACTION_REVEAL);
			swipeActionRight = styled.getInt(
					R.styleable.SwipeListView_swipeActionRight,
					SWIPE_ACTION_REVEAL);
			swipeOffsetLeft = styled.getDimension(
					R.styleable.SwipeListView_swipeOffsetLeft, 0);
			swipeOffsetRight = styled.getDimension(
					R.styleable.SwipeListView_swipeOffsetRight, 0);
			swipeOpenOnLongPress = styled.getBoolean(
					R.styleable.SwipeListView_swipeOpenOnLongPress, true);
			swipeAnimationTime = styled.getInteger(
					R.styleable.SwipeListView_swipeAnimationTime, 0);
			swipeCloseAllItemsWhenMoveList = styled.getBoolean(
					R.styleable.SwipeListView_swipeCloseAllItemsWhenMoveList,
					true);
			swipeFrontView = styled.getResourceId(
					R.styleable.SwipeListView_swipeFrontView, 0);
			swipeBackView = styled.getResourceId(
					R.styleable.SwipeListView_swipeBackView, 0);
		}

		if (swipeFrontView == 0 || swipeBackView == 0) {
			throw new RuntimeException(
					"Missed attribute swipeFrontView or swipeBackView");
		}

		final ViewConfiguration configuration = ViewConfiguration
				.get(getContext());
		touchSlop = ViewConfigurationCompat
				.getScaledPagingTouchSlop(configuration);
		touchListener = new StickyHeadersSwipeToDismissTouchListener(this,
				swipeFrontView, swipeBackView);
		if (swipeAnimationTime > 0) {
			touchListener.setAnimationTime(swipeAnimationTime);
		}
		touchListener.setRightOffset(swipeOffsetRight);
		touchListener.setLeftOffset(swipeOffsetLeft);
		touchListener.setSwipeActionLeft(swipeActionLeft);
		touchListener.setSwipeActionRight(swipeActionRight);
		touchListener.setSwipeMode(swipeMode);
		touchListener
				.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
		touchListener.setSwipeOpenOnLongPress(swipeOpenOnLongPress);
		setOnTouchListener(touchListener);

		setOnScrollListener(touchListener.makeScrollListener());

	}

	public StickyHeadersSwipeToDismissListView(Context context,
			AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		// super.setOnScrollListener(this);
		init(attrs);

		// null out divider, dividers are handled by adapter so they look good
		// with headers
		super.setDivider(null);
		super.setDividerHeight(0);
		setVerticalFadingEdgeEnabled(false);

		int[] attrsArray = new int[] { android.R.attr.drawSelectorOnTop };

		TypedArray a = context.obtainStyledAttributes(attrs, attrsArray,
				defStyle, 0);
		drawSelectorOnTop = a.getBoolean(0, false);
		a.recycle();

	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (frame == null) {
			ViewGroup parent = ((ViewGroup) getParent());
			int listIndex = parent.indexOfChild(this);
			parent.removeView(this);

			frame = new StickyHeadersSwipeToDismissListViewWrapper(getContext());
			frame.setSelector(getSelector());
			frame.setDrawSelectorOnTop(drawSelectorOnTop);

			ViewGroup.MarginLayoutParams p = (MarginLayoutParams) getLayoutParams();
			if (clippingToPadding) {
				frame.setPadding(0, getPaddingTop(), 0, getPaddingBottom());
				setPadding(getPaddingLeft(), 0, getPaddingRight(), 0);
			}

			ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT);
			setLayoutParams(params);
			frame.addView(this);
			frame.setBackgroundDrawable(getBackground());
			super.setBackgroundDrawable(null);

			frame.setLayoutParams(p);
			parent.addView(frame, listIndex);
		}
	}

	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background) {
		if (frame != null) {
			frame.setBackgroundDrawable(background);
		} else {
			super.setBackgroundDrawable(background);
		}
	}

	@Override
	public void setDrawSelectorOnTop(boolean onTop) {
		super.setDrawSelectorOnTop(onTop);
		drawSelectorOnTop = onTop;
		if (frame != null) {
			frame.setDrawSelectorOnTop(drawSelectorOnTop);
		}
	}

	@Override
	public boolean performItemClick(View view, int position, long id) {
		OnItemClickListener listener = getOnItemClickListener();
		int headerViewsCount = getHeaderViewsCount();
		final int viewType = adapter.getItemViewType(position
				- headerViewsCount);
		if (viewType == adapter.headerViewType) {
			if (onHeaderClickListener != null) {
				position = adapter.translateListViewPosition(position
						- headerViewsCount);
				onHeaderClickListener.onHeaderClick(this, view, position, id,
						false);
				return true;
			}
			return false;
		} else if (viewType == adapter.dividerViewType) {
			return false;
		} else {
			if (listener != null) {
				if (position >= adapter.getCount()) {
					position -= adapter.getHeaderCount();
				} else if (!(position < headerViewsCount)) {
					position = adapter.translateListViewPosition(position
							- headerViewsCount)
							+ headerViewsCount;
				}
				listener.onItemClick(this, view, position, id);
				return true;
			}
			return false;
		}
	}

	@Override
	public void setOnItemLongClickListener(OnItemLongClickListener listener) {
		onItemLongClickListenerDelegate = listener;
		if (listener == null) {
			super.setOnItemLongClickListener(null);
		} else {
			super.setOnItemLongClickListener(onItemLongClickListenerWrapper);
		}
	}

	@Override
	public Object getItemAtPosition(int position) {
		if (isCalledFromSuper()) {
			return super.getItemAtPosition(position);
		} else {
			return (adapter == null || position < 0) ? null : adapter.delegate
					.getItem(position);
		}
	}

	@Override
	public long getItemIdAtPosition(int position) {
		if (isCalledFromSuper()) {
			return super.getItemIdAtPosition(position);
		} else {
			return (adapter == null || position < 0) ? ListView.INVALID_ROW_ID
					: adapter.delegate.getItemId(position);
		}
	}

	private boolean isCalledFromSuper() {
		// i feel dirty...
		// could not think if better way, need to translate positions when not
		// called from super
		StackTraceElement callingFrame = Thread.currentThread().getStackTrace()[5];
		return callingFrame.getClassName().contains(
				"android.widget.AbsListView");
	}

	@Override
	public void setItemChecked(int position, boolean value) {
		if (!isCalledFromSuper()) {
			position = adapter.translateAdapterPosition(position);
		}
		// only real items are checkable
		int viewtype = adapter.getItemViewType(position);
		if (viewtype != adapter.dividerViewType
				&& viewtype != adapter.headerViewType) {
			super.setItemChecked(position, value);
		}
	}

	@Override
	public boolean isItemChecked(int position) {
		if (!isCalledFromSuper()) {
			position = adapter.translateAdapterPosition(position);
		}
		return super.isItemChecked(position);
	}

	@Override
	public int getCheckedItemPosition() {
		int position = super.getCheckedItemPosition();
		if (!isCalledFromSuper() && position != ListView.INVALID_POSITION) {
			position = adapter.translateAdapterPosition(position);
		}
		return position;
	}

	@Override
	public SparseBooleanArray getCheckedItemPositions() {
		SparseBooleanArray superCheckeditems = super.getCheckedItemPositions();
		if (!isCalledFromSuper() && superCheckeditems != null) {
			SparseBooleanArray checkeditems = new SparseBooleanArray(
					superCheckeditems.size());
			for (int i = 0; i < superCheckeditems.size(); i++) {
				int key = adapter.translateListViewPosition(superCheckeditems
						.keyAt(i));
				boolean value = superCheckeditems.valueAt(i);
				checkeditems.put(key, value);
			}
			return checkeditems;
		}
		return superCheckeditems;
	}

	/**
	 * can only be set to false if headers are sticky, not compatible with
	 * fading edges
	 */
	@Override
	public void setVerticalFadingEdgeEnabled(boolean verticalFadingEdgeEnabled) {
		if (areHeadersSticky) {
			super.setVerticalFadingEdgeEnabled(false);
		} else {
			super.setVerticalFadingEdgeEnabled(verticalFadingEdgeEnabled);
		}
	}

	@Override
	public void setDivider(Drawable divider) {
		this.divider = divider;
		if (divider != null) {
			int dividerDrawableHeight = divider.getIntrinsicHeight();
			if (dividerDrawableHeight >= 0) {
				setDividerHeight(dividerDrawableHeight);
			}
		}
		if (adapter != null) {
			adapter.setDivider(divider);
			requestLayout();
			invalidate();
		}
	}

	@Override
	public void setDividerHeight(int height) {
		dividerHeight = height;
		if (adapter != null) {
			adapter.setDividerHeight(height);
			requestLayout();
			invalidate();
		}
	}

	public void setAreHeadersSticky(boolean areHeadersSticky) {
		if (this.areHeadersSticky != areHeadersSticky) {
			if (areHeadersSticky) {
				super.setVerticalFadingEdgeEnabled(false);
			}
			requestLayout();
			this.areHeadersSticky = areHeadersSticky;
		}
	}

	public boolean getAreHeadersSticky() {
		return areHeadersSticky;
	}

	@Override
	public void setAdapter(ListAdapter adapter) {

		if (!clipToPaddingHasBeenSet) {
			clippingToPadding = true;
		}
		if (adapter != null
				&& !(adapter instanceof StickyHeadersSwipeToDismissAdapter)) {
			throw new IllegalArgumentException(
					"Adapter must implement StickyListHeadersAdapter");
		}

		if (this.adapter != null) {
			this.adapter.unregisterDataSetObserver(dataSetChangedObserver);
			this.adapter = null;
		}

		if (adapter != null) {
			if (adapter instanceof SectionIndexer) {
				this.adapter = new StickyHeadersSwipeToDismissSectionIndexerAdapterWrapper(
						getContext(),
						(StickyHeadersSwipeToDismissAdapter) adapter);
			} else {
				this.adapter = new StickyHeadersSwipeToDismissAdapterWrapper(
						getContext(),
						(StickyHeadersSwipeToDismissAdapter) adapter);
			}
			this.adapter.setDivider(divider);
			this.adapter.setDividerHeight(dividerHeight);
			this.adapter.registerDataSetObserver(dataSetChangedObserver);
		}

		currentHeaderId = null;
		super.setAdapter(this.adapter);
		// SWIPE
		// TODO
		touchListener.resetItems();
		this.adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				onListChanged();
				touchListener.resetItems();
			}
		});
		// END SWIPE
	}

	public StickyHeadersSwipeToDismissAdapter getWrappedAdapter() {
		if (adapter != null) {
			return adapter.getDelegate();
		}
		return null;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
			post(new Runnable() {

				@Override
				public void run() {
					scrollChanged(getFirstVisiblePosition());
				}
			});
		}
		if (!drawingListUnderStickyHeader) {
			canvas.clipRect(0, Math.max(frame.getHeaderBottomPosition(), 0),
					canvas.getWidth(), canvas.getHeight());
		}
		super.dispatchDraw(canvas);
	}

	@Override
	public void setClipToPadding(boolean clipToPadding) {
		super.setClipToPadding(clipToPadding);
		clippingToPadding = clipToPadding;
		clipToPaddingHasBeenSet = true;
	}

	void scrollChanged(int firstVisibleItem) {
		if (adapter == null) {
			return;
		}

		int adapterCount = adapter.getCount();

		if (adapterCount == 0 || !areHeadersSticky) {
			return;
		}

		final int listViewHeaderCount = getHeaderViewsCount();
		firstVisibleItem = getFixedFirstVisibleItem(firstVisibleItem)
				- listViewHeaderCount;

		if (firstVisibleItem < 0 || firstVisibleItem > adapterCount - 1) {
			if (currentHeaderId != null || dataChanged) {
				currentHeaderId = null;

				frame.removeHeader();
				updateHeaderVisibilities();
				invalidate();
				dataChanged = false;
			}
			return;
		}

		boolean headerHasChanged = false;
		long newHeaderId = adapter.getHeaderId(firstVisibleItem);
		if (currentHeaderId == null || currentHeaderId != newHeaderId) {
			// TODO

			headerPosition = firstVisibleItem;
			if (headerView == null) {
				headerView = adapter.getHeaderView(headerPosition,
						frame.removeHeader(), frame);
			} else {
				((TextView) headerView).setText(adapter
						.getStringHeader(headerPosition));
			}

			frame.setHeader(headerView);
			headerHasChanged = true;
		}
		currentHeaderId = newHeaderId;

		int childCount = getChildCount();

		if (childCount > 0) {
			View viewToWatch = null;
			int watchingChildDistance = Integer.MAX_VALUE;
			boolean viewToWatchIsFooter = false;

			for (int i = 0; i < childCount; i++) {
				View child = getChildAt(i);
				boolean childIsFooter = footerViews != null
						&& footerViews.contains(child);

				int childDistance;
				if (clippingToPadding) {
					childDistance = child.getTop() - getPaddingTop();
				} else {
					childDistance = child.getTop();
				}

				if (childDistance < 0) {
					continue;
				}

				if (viewToWatch == null
						|| (!viewToWatchIsFooter && !adapter
								.isHeader(viewToWatch))
						|| ((childIsFooter || adapter.isHeader(child)) && childDistance < watchingChildDistance)) {
					viewToWatch = child;
					viewToWatchIsFooter = childIsFooter;
					watchingChildDistance = childDistance;
				}
			}

			int headerHeight = frame.getHeaderHeight();
			int headerBottomPosition = 0;
			if (viewToWatch != null
					&& (viewToWatchIsFooter || adapter.isHeader(viewToWatch))) {

				if (firstVisibleItem == listViewHeaderCount
						&& getChildAt(0).getTop() > 0 && !clippingToPadding) {
					headerBottomPosition = 0;
				} else {
					if (clippingToPadding) {
						headerBottomPosition = Math.min(viewToWatch.getTop(),
								headerHeight + getPaddingTop());
						headerBottomPosition = headerBottomPosition < getPaddingTop() ? headerHeight
								+ getPaddingTop()
								: headerBottomPosition;
					} else {
						headerBottomPosition = Math.min(viewToWatch.getTop(),
								headerHeight);
						headerBottomPosition = headerBottomPosition < 0 ? headerHeight
								: headerBottomPosition;
					}
				}
			} else {
				headerBottomPosition = headerHeight;
				if (clippingToPadding) {
					headerBottomPosition += getPaddingTop();
				}
			}
			if (frame.getHeaderBottomPosition() != headerBottomPosition
					|| headerHasChanged) {
				frame.setHeaderBottomPosition(headerBottomPosition);
			}
			updateHeaderVisibilities();
		}
	}

	@Override
	public void setSelector(Drawable sel) {
		super.setSelector(sel);
		if (frame != null) {
			frame.setSelector(sel);
		}
	}

	@Override
	public void addFooterView(View v) {
		super.addFooterView(v);
		if (footerViews == null) {
			footerViews = new ArrayList<View>();
		}
		footerViews.add(v);
	}

	@Override
	public boolean removeFooterView(View v) {
		boolean removed = super.removeFooterView(v);
		if (removed) {
			footerViews.remove(v);
		}
		return removed;
	}

	private void updateHeaderVisibilities() {
		int top = clippingToPadding ? getPaddingTop() : 0;
		int childCount = getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = getChildAt(i);
			if (adapter.isHeader(child)) {
				if (child.getTop() < top) {
					if (child.getVisibility() != View.INVISIBLE) {
						child.setVisibility(View.INVISIBLE);
					}
				} else {
					if (child.getVisibility() != View.VISIBLE) {
						child.setVisibility(View.VISIBLE);
					}
				}
			}
		}
	}

	private int getFixedFirstVisibleItem(int firstVisibleItem) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			return firstVisibleItem;
		}

		for (int i = 0; i < getChildCount(); i++) {
			if (getChildAt(i).getBottom() >= 0) {
				firstVisibleItem += i;
				break;
			}
		}

		// work around to fix bug with firstVisibleItem being to high because
		// listview does not take clipToPadding=false into account
		if (!clippingToPadding && getPaddingTop() > 0) {
			if (super.getChildAt(0).getTop() > 0) {
				if (firstVisibleItem > 0) {
					firstVisibleItem -= 1;
				}
			}
		}
		return firstVisibleItem;
	}

	@Override
	public void setSelectionFromTop(int position, int y) {
		if (areHeadersSticky) {
			if (frame != null && frame.hasHeader()) {
				y += frame.getHeaderHeight();
			}
		}
		super.setSelectionFromTop(position, y);
	}

	public void setOnHeaderClickListener(
			OnHeaderClickListener onHeaderClickListener) {
		this.onHeaderClickListener = onHeaderClickListener;
	}

	@Override
	public void onClick(View v) {
		if (frame.isHeader(v)) {
			if (onHeaderClickListener != null) {
				onHeaderClickListener.onHeaderClick(this, v, headerPosition,
						currentHeaderId, true);
			}
		}
	}

	public boolean isDrawingListUnderStickyHeader() {
		return drawingListUnderStickyHeader;
	}

	public void setDrawingListUnderStickyHeader(
			boolean drawingListUnderStickyHeader) {
		this.drawingListUnderStickyHeader = drawingListUnderStickyHeader;
	}

	// METHODS for SWIPE

	/**
	 * Open ListView's item
	 * 
	 * @param position
	 *            Position that you want open
	 */
	public void openAnimate(int position) {
		touchListener.openAnimate(position);
	}

	/**
	 * Close ListView's item
	 * 
	 * @param position
	 *            Position that you want open
	 */
	public void closeAnimate(int position) {
		touchListener.closeAnimate(position);
	}

	/**
	 * Notifies onDismiss
	 * 
	 * @param reverseSortedPositions
	 *            All dismissed positions
	 */
	protected void onDismiss(int[] reverseSortedPositions) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onDismiss(reverseSortedPositions);
		}
	}

	/**
	 * Start open item
	 * 
	 * @param position
	 *            list item
	 * @param action
	 *            current action
	 * @param right
	 *            to right
	 */
	protected void onStartOpen(int position, int action, boolean right,
			View backView) {
		if (swipeListViewListener != null) {
			swipeListViewListener
					.onStartOpen(position, action, right, backView);
		}
	}

	/**
	 * Swipe is change from left to right and from right to left
	 * 
	 * @param position
	 *            list item
	 * @param action
	 *            current action
	 * @param right
	 *            to right
	 */
	protected void onChangeSwipe(boolean right, View backView) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onSwipeChanged(right, backView);
		}
	}

	/**
	 * Start close item
	 * 
	 * @param position
	 *            list item
	 * @param right
	 */
	protected void onStartClose(int position, boolean right) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onStartClose(position, right);
		}
	}

	/**
	 * Notifies onClickFrontView
	 * 
	 * @param position
	 *            item clicked
	 */
	protected void onClickFrontView(int position) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onClickFrontView(position);
		}
	}

	/**
	 * Notifies onClickBackView
	 * 
	 * @param position
	 *            back item clicked
	 */
	protected void onClickBackView(int position) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onClickBackView(position);
		}
	}

	/**
	 * Notifies onOpened
	 * 
	 * @param position
	 *            Item opened
	 * @param toRight
	 *            If should be opened toward the right
	 */
	protected void onOpened(int position, boolean toRight) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onOpened(position, toRight);
		}
	}

	/**
	 * Notifies onClosed
	 * 
	 * @param position
	 *            Item closed
	 * @param fromRight
	 *            If open from right
	 */
	protected void onClosed(int position, boolean fromRight) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onClosed(position, fromRight);
		}
	}

	/**
	 * Notifies onListChanged
	 */
	protected void onListChanged() {
		if (swipeListViewListener != null) {
			swipeListViewListener.onListChanged();
		}
	}

	/**
	 * Notifies onMove
	 * 
	 * @param position
	 *            Item moving
	 * @param x
	 *            Current position
	 */
	protected void onMove(int position, float x) {
		if (swipeListViewListener != null) {
			swipeListViewListener.onMove(position, x);
		}
	}

	protected int changeSwipeMode(int position) {
		if (swipeListViewListener != null) {
			return swipeListViewListener.onChangeSwipeMode(position);
		}
		return SWIPE_MODE_DEFAULT;
	}

	/**
	 * Sets the Listener
	 * 
	 * @param swipeListViewListener
	 *            Listener
	 */
	public void setSwipeListViewListener(
			BaseSwipeStickyListViewListener swipeListViewListener) {

		this.swipeListViewListener = swipeListViewListener;

	}

	public void resetOpened() {
		if (touchListener != null) {
			touchListener.resetOpened();
		}
	}

	public void closeOpenedItems() {
		if (touchListener != null) {
			touchListener.closeOpenedItems();
		}
	}

	public void resetItems() {
		if (touchListener != null) {
			touchListener.resetItems();
		}
	}

	public void setPositionForBlockedRightMode(int position) {
		if (touchListener != null) {
			touchListener.setPositionForBlockedRightMode(position);
		}
	}

	/**
	 * Resets scrolling
	 */
	public void resetScrolling() {
		touchState = TOUCH_STATE_REST;
	}

	/**
	 * Set offset on right
	 * 
	 * @param offsetRight
	 *            Offset
	 */
	public void setOffsetRight(float offsetRight) {
		touchListener.setRightOffset(offsetRight);
	}

	/**
	 * Set offset on left
	 * 
	 * @param offsetLeft
	 *            Offset
	 */
	public void setOffsetLeft(float offsetLeft) {
		touchListener.setLeftOffset(offsetLeft);
	}

	/**
	 * Set if all items opened will be closed when the user moves the ListView
	 * 
	 * @param swipeCloseAllItemsWhenMoveList
	 */
	public void setSwipeCloseAllItemsWhenMoveList(
			boolean swipeCloseAllItemsWhenMoveList) {
		touchListener
				.setSwipeClosesAllItemsWhenListMoves(swipeCloseAllItemsWhenMoveList);
	}

	/**
	 * Sets if the user can open an item with long pressing on cell
	 * 
	 * @param swipeOpenOnLongPress
	 */
	public void setSwipeOpenOnLongPress(boolean swipeOpenOnLongPress) {
		touchListener.setSwipeOpenOnLongPress(swipeOpenOnLongPress);
	}

	/**
	 * Set swipe mode
	 * 
	 * @param swipeMode
	 */
	public void setSwipeMode(int swipeMode) {
		touchListener.setSwipeMode(swipeMode);
	}

	/**
	 * Return action on left
	 * 
	 * @return Action
	 */
	public int getSwipeActionLeft() {
		return touchListener.getSwipeActionLeft();
	}

	/**
	 * Set action on left
	 * 
	 * @param swipeActionLeft
	 *            Action
	 */
	public void setSwipeActionLeft(int swipeActionLeft) {
		touchListener.setSwipeActionLeft(swipeActionLeft);
	}

	/**
	 * Return action on right
	 * 
	 * @return Action
	 */
	public int getSwipeActionRight() {
		return touchListener.getSwipeActionRight();
	}

	/**
	 * Set action on right
	 * 
	 * @param swipeActionRight
	 *            Action
	 */
	public void setSwipeActionRight(int swipeActionRight) {
		touchListener.setSwipeActionRight(swipeActionRight);
	}

	/**
	 * Sets animation time when user drops cell
	 * 
	 * @param animationTime
	 *            milliseconds
	 */
	public void setAnimationTime(long animationTime) {
		touchListener.setAnimationTime(animationTime);
	}

	/**
	 * @see ListView#onInterceptTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		int action = MotionEventCompat.getActionMasked(ev);
		final float x = ev.getX();
		final float y = ev.getY();

		if (touchState == TOUCH_STATE_SCROLLING_X) {
			return touchListener.onTouch(this, ev);
		}

		switch (action) {
		case MotionEvent.ACTION_MOVE:
			checkInMoving(x, y);
			return touchState == TOUCH_STATE_SCROLLING_Y;
		case MotionEvent.ACTION_DOWN:
			touchListener.onTouch(this, ev);
			touchState = TOUCH_STATE_REST;
			lastMotionX = x;
			lastMotionY = y;
			return false;
		case MotionEvent.ACTION_CANCEL:
			touchState = TOUCH_STATE_REST;
			break;
		case MotionEvent.ACTION_UP:
			touchListener.onTouch(this, ev);
			return touchState == TOUCH_STATE_SCROLLING_Y;
		default:
			break;
		}

		return super.onInterceptTouchEvent(ev);
	}

	/**
	 * Check if the user is moving the cell
	 * 
	 * @param x
	 *            Position X
	 * @param y
	 *            Position Y
	 */
	private void checkInMoving(float x, float y) {
		final int xDiff = (int) Math.abs(x - lastMotionX);
		final int yDiff = (int) Math.abs(y - lastMotionY);

		final int touchSlop = this.touchSlop;
		boolean xMoved = xDiff > touchSlop;
		boolean yMoved = yDiff > touchSlop;

		if (xMoved) {
			touchState = TOUCH_STATE_SCROLLING_X;
			lastMotionX = x;
			lastMotionY = y;
		}

		if (yMoved) {
			touchState = TOUCH_STATE_SCROLLING_Y;
			lastMotionX = x;
			lastMotionY = y;
		}
	}

}
