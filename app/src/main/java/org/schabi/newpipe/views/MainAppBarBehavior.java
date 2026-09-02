package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.appbar.AppBarLayout;

import org.schabi.newpipe.R;

/**
 * An {@link AppBarLayout.Behavior} which only follows nested scrolls that started inside the main
 * fragment holder.
 *
 * <p>The player bottom sheet is a sibling of the app bar inside the same
 * {@link CoordinatorLayout}, so without this restriction scrolling the video description or the
 * queue would drag the main page header around as well.</p>
 */
public class MainAppBarBehavior extends AppBarLayout.Behavior {
    public MainAppBarBehavior() {
        super();
    }

    public MainAppBarBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull final CoordinatorLayout parent,
                                       @NonNull final AppBarLayout child,
                                       @NonNull final View directTargetChild,
                                       final View target,
                                       final int nestedScrollAxes,
                                       final int type) {
        return directTargetChild.getId() == R.id.fragment_holder
                && super.onStartNestedScroll(parent, child, directTargetChild, target,
                nestedScrollAxes, type);
    }
}
