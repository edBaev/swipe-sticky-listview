package ed.swipestickylistview;

import android.content.Context;
import android.widget.SectionIndexer;

public class StickyHeadersSwipeToDismissSectionIndexerAdapterWrapper extends
		StickyHeadersSwipeToDismissAdapterWrapper implements SectionIndexer {

	private final SectionIndexer delegate;

	StickyHeadersSwipeToDismissSectionIndexerAdapterWrapper(Context context,
			StickyHeadersSwipeToDismissAdapter delegate) {
		super(context, delegate);
		this.delegate = (SectionIndexer) delegate;
	}

	@Override
	public int getPositionForSection(int section) {
		int position = delegate.getPositionForSection(section);
		position = translateAdapterPosition(position);
		return position;
	}

	@Override
	public int getSectionForPosition(int position) {
		position = translateListViewPosition(position);
		return delegate.getSectionForPosition(position);
	}

	@Override
	public Object[] getSections() {
		return delegate.getSections();
	}

}
