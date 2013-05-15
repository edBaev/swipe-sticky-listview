package ed.swipestickylistview;

import android.view.View;

public class BaseSwipeStickyListViewListener implements
		StickyHeadersSwipeToDismissListViewListener {
	@Override
	public void onOpened(int position, boolean toRight) {
	}

	@Override
	public void onClosed(int position, boolean fromRight) {
	}

	@Override
	public void onListChanged() {
	}

	@Override
	public void onMove(int position, float x) {
	}

	@Override
	public void onStartOpen(int position, int action, boolean right,
			View backView) {
	}

	@Override
	public void onStartClose(int position, boolean right) {
	}

	@Override
	public void onClickFrontView(int position) {
	}

	@Override
	public void onClickBackView(int position) {
	}

	@Override
	public void onDismiss(int[] reverseSortedPositions) {
	}

	@Override
	public int onChangeSwipeMode(int position) {
		return StickyHeadersSwipeToDismissListView.SWIPE_MODE_DEFAULT;
	}

	@Override
	public void onSwipeChanged(boolean right, View backView) {

	}
}
