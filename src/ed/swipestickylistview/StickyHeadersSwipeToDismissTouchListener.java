/*
 * Copyright (C) 2013 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Copyright 2012 Roman Nurik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ed.swipestickylistview;

import static com.nineoldandroids.view.ViewHelper.setAlpha;
import static com.nineoldandroids.view.ViewHelper.setTranslationX;
import static com.nineoldandroids.view.ViewPropertyAnimator.animate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;

/**
 * Touch listener impl for the StickyHeadersSwipeToDismissListView
 */
public class StickyHeadersSwipeToDismissTouchListener implements
		View.OnTouchListener {

	private int swipeMode = StickyHeadersSwipeToDismissListView.SWIPE_MODE_BOTH;
	private boolean swipeOpenOnLongPress = true;
	boolean swipeClosesAllItemsWhenListMoves = true;

	private int swipeFrontView = 0;
	private int swipeBackView = 0;
	private boolean isOpening;
	private boolean nowRightSwipe;
	private Rect rect = new Rect();

	// Cached ViewConfiguration and system-wide constant values
	private int slop;
	private int minFlingVelocity;
	private int maxFlingVelocity;
	private long configShortAnimationTime;
	private long animationTime;

	private float leftOffset = 0;
	private float rightOffset = 0;

	// Fixed properties
	StickyHeadersSwipeToDismissListView swipeListView;
	private int viewWidth = 1; // 1 and not 0 to prevent dividing by zero

	private List<PendingDismissData> pendingDismisses = new ArrayList<PendingDismissData>();
	private int dismissAnimationRefCount = 0;

	private float downX;
	private boolean swiping;
	private VelocityTracker velocityTracker;
	private int downPosition;
	private View parentView;
	private View frontView;
	private View backView;
	private boolean paused;

	private int swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_NONE;

	private int swipeActionLeft = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_REVEAL;
	private int swipeActionRight = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_REVEAL;

	private List<Boolean> opened;
	private List<Boolean> abilityRightOpen = new ArrayList<Boolean>();
	private List<Boolean> openedRight = new ArrayList<Boolean>();
	boolean listViewMoving;
	private List<Integer> positionDisable = new LinkedList<Integer>();

	/**
	 * Constructor
	 * 
	 * @param swipeListView
	 *            SwipeListView
	 * @param swipeFrontView
	 *            front view Identifier
	 * @param swipeBackView
	 *            back view Identifier
	 */
	public StickyHeadersSwipeToDismissTouchListener(
			StickyHeadersSwipeToDismissListView swipeListView,
			int swipeFrontView, int swipeBackView) {
		this.swipeFrontView = swipeFrontView;
		opened = new ArrayList<Boolean>();
		this.swipeBackView = swipeBackView;
		ViewConfiguration vc = ViewConfiguration
				.get(swipeListView.getContext());
		slop = vc.getScaledTouchSlop();
		minFlingVelocity = vc.getScaledMinimumFlingVelocity();
		maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
		configShortAnimationTime = swipeListView.getContext().getResources()
				.getInteger(android.R.integer.config_shortAnimTime);
		animationTime = configShortAnimationTime;
		this.swipeListView = swipeListView;
	}

	/**
	 * Sets current item's parent view
	 * 
	 * @param parentView
	 *            Parent view
	 */
	private void setParentView(View parentView) {
		this.parentView = parentView;
	}

	/**
	 * Sets current item's front view
	 * 
	 * @param frontView
	 *            Front view
	 */
	private void setFrontView(View frontView) {
		if (frontView != null) {
			this.frontView = frontView;
			frontView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					swipeListView.onClickFrontView(downPosition);
				}
			});
			if (swipeOpenOnLongPress) {
				frontView
						.setOnLongClickListener(new View.OnLongClickListener() {
							@Override
							public boolean onLongClick(View v) {
								openAnimate(downPosition);
								return false;
							}
						});
			}
		}
	}

	/**
	 * Set current item's back view
	 * 
	 * @param backView
	 */
	private void setBackView(View backView) {

		this.backView = backView;
		if (backView != null) {
			backView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					swipeListView.onClickBackView(downPosition);
				}
			});
		}
	}

	/**
	 * @return true if the list is in motion
	 */
	public boolean isListViewMoving() {
		return listViewMoving;
	}

	/**
	 * Sets animation time when the user drops the cell
	 * 
	 * @param animationTime
	 *            milliseconds
	 */
	public void setAnimationTime(long animationTime) {
		if (animationTime > 0) {
			this.animationTime = animationTime;
		} else {
			this.animationTime = configShortAnimationTime;
		}
	}

	/**
	 * Sets the right offset
	 * 
	 * @param rightOffset
	 *            Offset
	 */
	public void setRightOffset(float rightOffset) {
		this.rightOffset = rightOffset;
	}

	/**
	 * Set the left offset
	 * 
	 * @param leftOffset
	 *            Offset
	 */
	public void setLeftOffset(float leftOffset) {
		this.leftOffset = leftOffset;
	}

	/**
	 * Set if all item opened will be close when the user move ListView
	 * 
	 * @param swipeClosesAllItemsWhenListMoves
	 */
	public void setSwipeClosesAllItemsWhenListMoves(
			boolean swipeClosesAllItemsWhenListMoves) {
		this.swipeClosesAllItemsWhenListMoves = swipeClosesAllItemsWhenListMoves;
	}

	/**
	 * Set if the user can open an item with long press on cell
	 * 
	 * @param swipeOpenOnLongPress
	 */
	public void setSwipeOpenOnLongPress(boolean swipeOpenOnLongPress) {
		this.swipeOpenOnLongPress = swipeOpenOnLongPress;
	}

	/**
	 * Sets the swipe mode
	 * 
	 * @param swipeMode
	 */
	public void setSwipeMode(int swipeMode) {
		this.swipeMode = swipeMode;
	}

	/**
	 * Return action on left
	 * 
	 * @return Action
	 */
	public int getSwipeActionLeft() {
		return swipeActionLeft;
	}

	/**
	 * Set action on left
	 * 
	 * @param swipeActionLeft
	 *            Action
	 */
	public void setSwipeActionLeft(int swipeActionLeft) {
		this.swipeActionLeft = swipeActionLeft;
	}

	/**
	 * Return action on right
	 * 
	 * @return Action
	 */
	public int getSwipeActionRight() {
		return swipeActionRight;
	}

	/**
	 * Set action on right
	 * 
	 * @param swipeActionRight
	 *            Action
	 */
	public void setSwipeActionRight(int swipeActionRight) {
		this.swipeActionRight = swipeActionRight;
	}

	public void resetOpened() {
		opened = new LinkedList<Boolean>();
	}

	/**
	 * Adds new items when adapter is modified
	 */
	public void resetItems() {

		if (swipeListView.getAdapter() != null) {
			createNewPositionList();
			int count = swipeListView.getAdapter().getCount();
			for (int i = opened.size(); i <= count; i++) {
				opened.add(false);
				openedRight.add(false);
				abilityRightOpen.add(true);
			}
		}
	}

	private void createNewPositionList() {
		positionDisable = new LinkedList<Integer>();
	}

	/**
	 * TODO
	 */
	public void setPositionForBlockedRightMode(int pos) {
		if (positionDisable == null) {
			createNewPositionList();
		}
		positionDisable.add(pos);
	}

	/**
	 * Open item
	 * 
	 * @param position
	 *            Position of list
	 */
	protected void openAnimate(int position) {
		openAnimate(
				swipeListView.getChildAt(
						position - swipeListView.getFirstVisiblePosition())
						.findViewById(swipeFrontView), position);
	}

	/**
	 * Close item
	 * 
	 * @param position
	 *            Position of list
	 */
	protected void closeAnimate(int position) {
		closeAnimate(
				swipeListView.getChildAt(
						position - swipeListView.getFirstVisiblePosition())
						.findViewById(swipeFrontView), position);
	}

	/**
	 * Open item
	 * 
	 * @param view
	 *            affected view
	 * @param position
	 *            Position of list
	 */
	private void openAnimate(View view, int position) {
		if (!opened.get(position)) {
			generateRevealAnimate(view, true, false, position);
		}
	}

	/**
	 * Close item
	 * 
	 * @param view
	 *            affected view
	 * @param position
	 *            Position of list
	 */
	private void closeAnimate(View view, int position) {
		if (opened.get(position)) {
			generateRevealAnimate(view, true, false, position);
		}
	}

	/**
	 * Create animation
	 * 
	 * @param view
	 *            affected view
	 * @param swap
	 *            If state should change. If "false" returns to the original
	 *            position
	 * @param swapRight
	 *            If swap is true, this parameter tells if move is to the right
	 *            or left
	 * @param position
	 *            Position of list
	 */
	private void generateAnimate(final View view, final boolean swap,
			final boolean swapRight, final int position) {
		if (swipeCurrentAction == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_REVEAL) {
			generateRevealAnimate(view, swap, swapRight, position);

		}
		if (swipeCurrentAction == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_DISMISS) {
			generateDismissAnimate(parentView, swap, swapRight, position);
		}
	}

	/**
	 * Create dismiss animation
	 * 
	 * @param view
	 *            affected view
	 * @param swap
	 *            If will change state. If is "false" returns to the original
	 *            position
	 * @param swapRight
	 *            If swap is true, this parameter tells if move is to the right
	 *            or left
	 * @param position
	 *            Position of list
	 */
	private void generateDismissAnimate(final View view, final boolean swap,
			final boolean swapRight, final int position) {
		int moveTo = 0;
		if (opened.get(position)) {
			if (!swap) {
				moveTo = openedRight.get(position) ? (int) (viewWidth - rightOffset)
						: (int) (-viewWidth + leftOffset);
			}
		} else {
			if (swap) {
				moveTo = swapRight ? (int) (viewWidth - rightOffset)
						: (int) (-viewWidth + leftOffset);
			}
		}
		int alpha = 1;
		if (swap) {
			++dismissAnimationRefCount;
			alpha = 0;
		}
		animate(view).translationX(moveTo).alpha(alpha)
				.setDuration(animationTime)
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						if (swap) {
							closeOpenedItems();
							performDismiss(view, position);
						}
					}
				});
	}

	/**
	 * Create reveal animation
	 * 
	 * @param view
	 *            affected view
	 * @param swap
	 *            If will change state. If "false" returns to the original
	 *            position
	 * @param swapRight
	 *            If swap is true, this parameter tells if movement is toward
	 *            right or left
	 * @param position
	 *            list position
	 */
	private void generateRevealAnimate(final View view, final boolean swap,
			final boolean swapRight, final int position) {
		if (view == null) {
			return;
		}
		int moveTo = 0;
		if (opened.get(position)) {
			if (!swap) {
				moveTo = openedRight.get(position) ? (int) (viewWidth - rightOffset)
						: (int) (-viewWidth + leftOffset);
			}
		} else {
			if (swap) {
				moveTo = swapRight ? (int) (viewWidth - rightOffset)
						: (int) (-viewWidth + leftOffset);
			}
		}

		animate(view).translationX(moveTo).setDuration(animationTime)
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						swipeListView.resetScrolling();
						if (swap) {
							boolean aux = !opened.get(position);
							opened.set(position, aux);
							if (aux) {
								isOpening = false;
								swipeListView.onOpened(position, swapRight);
								openedRight.set(position, swapRight);
							} else {
								swipeListView.onClosed(position,
										openedRight.get(position));
							}
						}
					}
				});
	}

	/**
	 * Return ScrollListener for ListView
	 * 
	 * @return OnScrollListener
	 */
	public AbsListView.OnScrollListener makeScrollListener() {
		return new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView,
					int scrollState) {

				// swipe
				setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
				if (swipeClosesAllItemsWhenListMoves
						&& scrollState == SCROLL_STATE_TOUCH_SCROLL) {
					closeOpenedItems();
				}
				if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
					listViewMoving = true;
					setEnabled(false);
				}
				if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_FLING
						&& scrollState != SCROLL_STATE_TOUCH_SCROLL) {
					listViewMoving = false;
					swipeListView.resetScrolling();
					new Handler().postDelayed(new Runnable() {
						public void run() {
							setEnabled(true);
						}
					}, 500);
				}
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				swipeListView.scrollChanged(firstVisibleItem);
			}

		};
	}

	/**
	 * Set enabled
	 * 
	 * @param enabled
	 */
	public void setEnabled(boolean enabled) {
		paused = !enabled;
	}

	/**
	 * Close all opened items
	 */
	void closeOpenedItems() {
		if (opened != null) {
			int start = swipeListView.getFirstVisiblePosition();
			int end = swipeListView.getLastVisiblePosition();

			for (int i = start; i <= end; i++) {

				if (i < opened.size() && opened.get(i)) {
					closeAnimate(swipeListView.getChildAt(i - start)
							.findViewById(swipeFrontView), i);
				}
			}
		}

	}

	private boolean checkOnlyLeftModeByPosition() {
		if (positionDisable != null && positionDisable.contains(downPosition)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @see View.OnTouchListener#onTouch(android.view.View,
	 *      android.view.MotionEvent)
	 */
	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {

		if (viewWidth < 2) {
			viewWidth = swipeListView.getWidth();
		}

		switch (motionEvent.getActionMasked()) {
		case MotionEvent.ACTION_DOWN: {
			if (paused) {
				return false;
			}
			swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_NONE;

			int childCount = swipeListView.getChildCount();
			int[] listViewCoords = new int[2];
			swipeListView.getLocationOnScreen(listViewCoords);
			int x = (int) motionEvent.getRawX() - listViewCoords[0];
			int y = (int) motionEvent.getRawY() - listViewCoords[1];

			View child;
			for (int i = 0; i < childCount; i++) {
				child = swipeListView.getChildAt(i);
				child.getHitRect(rect);
				if (rect.contains(x, y)
						&& swipeListView.getAdapter().isEnabled(
								swipeListView.getFirstVisiblePosition() + i)) {
					setParentView(child);
					setFrontView(child.findViewById(swipeFrontView));
					downX = motionEvent.getRawX();
					downPosition = swipeListView.getPositionForView(child);
					if (opened.size() < downPosition) {
						resetItems();
					}
					if (frontView != null && opened.get(downPosition) != null) {
						frontView.setClickable(!opened.get(downPosition));
						frontView.setLongClickable(!opened.get(downPosition));
					}
					velocityTracker = VelocityTracker.obtain();
					velocityTracker.addMovement(motionEvent);
					if (swipeBackView > 0) {
						setBackView(child.findViewById(swipeBackView));
					}
					break;
				}
			}
			view.onTouchEvent(motionEvent);
			return true;
		}

		case MotionEvent.ACTION_UP: {
			if (velocityTracker == null || !swiping) {
				break;
			}

			float deltaX = motionEvent.getRawX() - downX;
			velocityTracker.addMovement(motionEvent);
			velocityTracker.computeCurrentVelocity(1000);
			float velocityX = Math.abs(velocityTracker.getXVelocity());
			if (!opened.get(downPosition)) {
				if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_LEFT
						&& velocityTracker.getXVelocity() > 0) {
					velocityX = 0;
				}
				if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_RIGHT
						&& velocityTracker.getXVelocity() < 0) {
					velocityX = 0;
				}
			}
			float velocityY = Math.abs(velocityTracker.getYVelocity());
			boolean swap = false;
			boolean swapRight = false;
			if (minFlingVelocity <= velocityX && velocityX <= maxFlingVelocity
					&& velocityY < velocityX) {
				swapRight = velocityTracker.getXVelocity() > 0;
				if (opened.get(downPosition) && openedRight.get(downPosition)
						&& swapRight) {
					swap = false;
				} else if (opened.get(downPosition)
						&& !openedRight.get(downPosition) && !swapRight) {
					swap = false;
				} else {

					if (opened.get(downPosition)) {

						swap = true;
					} else {
						if (nowRightSwipe == swapRight) {
							swap = true;
						} else {
							swap = false;
						}
					}

				}
			} else if (Math.abs(deltaX) > viewWidth / 2) {

				swap = true;
				swapRight = deltaX > 0;

			}
			if (frontView != null) {
				generateAnimate(frontView, swap, swapRight, downPosition);
			}
			velocityTracker.recycle();
			velocityTracker = null;
			downX = 0;
			// change clickable front view
			if (swap && frontView != null) {
				frontView.setClickable(opened.get(downPosition));
				frontView.setLongClickable(opened.get(downPosition));
			}
			frontView = null;
			backView = null;
			this.downPosition = ListView.INVALID_POSITION;
			swiping = false;

			break;
		}

		case MotionEvent.ACTION_MOVE: {

			if (velocityTracker == null || paused) {
				break;
			}

			velocityTracker.addMovement(motionEvent);
			velocityTracker.computeCurrentVelocity(1000);
			float velocityX = Math.abs(velocityTracker.getXVelocity());
			float velocityY = Math.abs(velocityTracker.getYVelocity());

			float deltaX = motionEvent.getRawX() - downX;
			float deltaMode = Math.abs(deltaX);

			if (isOpening && backView != null) {

				boolean onChangeSwipe = (deltaX > 0);
				if (checkOnlyLeftModeByPosition()) {
					// TODO
					// only left mode for some positions
					if (deltaX > 0) {
						deltaMode = 0;
						deltaX = 0;
					}
				} else {
					if (onChangeSwipe != nowRightSwipe) {
						nowRightSwipe = onChangeSwipe;
						swipeListView.onChangeSwipe(onChangeSwipe, backView);
					}
				}

			}
			int swipeMode = this.swipeMode;
			int changeSwipeMode = swipeListView.changeSwipeMode(downPosition);
			if (changeSwipeMode >= 0) {
				swipeMode = changeSwipeMode;
			}

			if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_NONE) {
				deltaMode = 0;
			} else if (swipeMode != StickyHeadersSwipeToDismissListView.SWIPE_MODE_BOTH) {
				if (opened.get(downPosition)) {
					if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_LEFT
							&& deltaX < 0) {
						deltaMode = 0;
					} else if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_RIGHT
							&& deltaX > 0) {
						deltaMode = 0;
					}
				} else {
					if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_LEFT
							&& deltaX > 0) {
						deltaMode = 0;
					} else if (swipeMode == StickyHeadersSwipeToDismissListView.SWIPE_MODE_RIGHT
							&& deltaX < 0) {
						deltaMode = 0;
					}
				}
			} else {
				// TODO
				// only left mode for some positions
				if (checkOnlyLeftModeByPosition()) {

					if (opened.get(downPosition)) {
						if (deltaX < 0)
							deltaMode = 0;
					} else {
						if (deltaX > 0)
							deltaMode = 0;
					}

				}
			}
			if (deltaMode > slop
					&& swipeCurrentAction == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_NONE
					&& velocityY < velocityX) {
				swiping = true;
				boolean swipingRight = (deltaX > 0);

				if (opened.get(downPosition)) {
					swipeListView.onStartClose(downPosition, swipingRight);
					swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_REVEAL;
				} else {
					if (swipingRight
							&& swipeActionRight == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_DISMISS) {
						swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_DISMISS;
					} else if (!swipingRight
							&& swipeActionLeft == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_DISMISS) {
						swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_DISMISS;
					} else if (swipingRight
							&& swipeActionRight == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_CHECK) {
						swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_CHECK;
					} else if (!swipingRight
							&& swipeActionLeft == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_CHECK) {
						swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_CHECK;
					} else {
						swipeCurrentAction = StickyHeadersSwipeToDismissListView.SWIPE_ACTION_REVEAL;
					}

					nowRightSwipe = swipingRight;
					if (backView != null) {
						isOpening = true;
						swipeListView.onStartOpen(downPosition,
								swipeCurrentAction, swipingRight, backView);
					}
				}
				swipeListView.requestDisallowInterceptTouchEvent(true);
				MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
				cancelEvent
						.setAction(MotionEvent.ACTION_CANCEL
								| (motionEvent.getActionIndex() << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
				swipeListView.onTouchEvent(cancelEvent);
			}

			if (swiping) {
				if (opened.get(downPosition)) {
					deltaX += openedRight.get(downPosition) ? viewWidth
							- rightOffset : -viewWidth + leftOffset;
				}
				move(deltaX);
				return true;
			}
			break;
		}
		}
		return false;
	}

	/**
	 * Moves the view
	 * 
	 * @param deltaX
	 *            delta
	 */
	public void move(float deltaX) {
		swipeListView.onMove(downPosition, deltaX);
		if (swipeCurrentAction == StickyHeadersSwipeToDismissListView.SWIPE_ACTION_DISMISS) {
			setTranslationX(parentView, deltaX);
			setAlpha(
					parentView,
					Math.max(0f, Math.min(1f, 1f - 2f * Math.abs(deltaX)
							/ viewWidth)));
		} else {
			if (frontView != null) {
				setTranslationX(frontView, deltaX);
			}
		}
	}

	/**
	 * Class that saves pending dismiss data
	 */
	class PendingDismissData implements Comparable<PendingDismissData> {
		public int position;
		public View view;

		public PendingDismissData(int position, View view) {
			this.position = position;
			this.view = view;
		}

		@Override
		public int compareTo(PendingDismissData other) {
			// Sort by descending position
			return other.position - position;
		}
	}

	/**
	 * Perform dismiss action
	 * 
	 * @param dismissView
	 *            View
	 * @param dismissPosition
	 *            Position of list
	 */
	private void performDismiss(final View dismissView,
			final int dismissPosition) {
		final ViewGroup.LayoutParams lp = dismissView.getLayoutParams();
		final int originalHeight = dismissView.getHeight();

		ValueAnimator animator = ValueAnimator.ofInt(originalHeight, 1)
				.setDuration(animationTime);

		animator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				--dismissAnimationRefCount;
				if (dismissAnimationRefCount == 0) {
					// No active animations, process all pending dismisses.
					// Sort by descending position
					Collections.sort(pendingDismisses);

					int[] dismissPositions = new int[pendingDismisses.size()];
					for (int i = pendingDismisses.size() - 1; i >= 0; i--) {
						dismissPositions[i] = pendingDismisses.get(i).position;
					}
					swipeListView.onDismiss(dismissPositions);

					ViewGroup.LayoutParams lp;
					for (PendingDismissData pendingDismiss : pendingDismisses) {
						// Reset view presentation
						setAlpha(pendingDismiss.view, 1f);
						setTranslationX(pendingDismiss.view, 0);
						lp = pendingDismiss.view.getLayoutParams();
						lp.height = originalHeight;
						pendingDismiss.view.setLayoutParams(lp);
					}

					pendingDismisses.clear();
				}
			}
		});

		animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator valueAnimator) {
				lp.height = (Integer) valueAnimator.getAnimatedValue();
				dismissView.setLayoutParams(lp);
			}
		});

		pendingDismisses.add(new PendingDismissData(dismissPosition,
				dismissView));
		animator.start();
	}

}
