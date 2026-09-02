package org.schabi.newpipe.fragments;

import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.BELOW;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_BOTTOM;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_TOP;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {
    private FragmentMainBinding binding;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;
    private SharedPreferences prefs;
    private boolean mainTabsPositionBottom;
    private String mainTabsPositionKey;
    private boolean collapseHeader = true;
    private String collapseHeaderKey;


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mainTabsPositionKey = getString(R.string.main_tabs_position_key);
        mainTabsPositionBottom = prefs.getBoolean(mainTabsPositionKey, false);
        collapseHeaderKey = getString(R.string.collapse_header_on_scroll_key);
        collapseHeader = prefs.getBoolean(collapseHeaderKey, true);

    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);
        binding.mainTabLayout.setTabRippleColor(binding.mainTabLayout.getTabRippleColor()
                .withAlpha(32));

        setupTabs();
        updateHeaderLayout();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (hasTabsChanged) {
            setupTabs();
        }

        final boolean newMainTabsPosition = prefs.getBoolean(mainTabsPositionKey, false);
        final boolean newCollapseHeader = prefs.getBoolean(collapseHeaderKey, true);
        if (mainTabsPositionBottom != newMainTabsPosition || collapseHeader != newCollapseHeader) {
            mainTabsPositionBottom = newMainTabsPosition;
            collapseHeader = newCollapseHeader;
            updateHeaderLayout();
        }

        // The activity nails the header down for every other fragment, so claim it back here
        final MainActivity mainActivity = getMainActivity();
        if (mainActivity != null) {
            mainActivity.setHeaderCollapseEnabled(collapseHeader);
        }
    }

    @Override
    public void onDestroyView() {
        // The tab strip is a child of the activity's app bar while the header collapses, so it has
        // to be handed back before this fragment's view hierarchy goes away
        moveTabLayoutToFragmentRoot();
        final MainActivity mainActivity = getMainActivity();
        if (mainActivity != null) {
            mainActivity.setHeaderCollapseEnabled(false);
        }

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            try {
                final int selectedServiceId = ServiceHelper.getSelectedServiceId(activity);
                NavigationHelper.openSearchFragment(getFM(),
                        SearchFragment.getPersistedSearchServiceId(activity, selectedServiceId),
                        "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setOffscreenPageLimit(tabsList.size());
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                tabToSet.setIcon(tab.getTabIconRes(requireContext()));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
    }

    private void updateTitleForTab(final int tabPosition) {
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    /**
     * Places the tab strip and the pager according to the "main tabs position" and the "collapse
     * header on scroll" preferences. When the tabs sit on top and the header may collapse, the tab
     * strip is moved into the activity's app bar, so that it scrolls away together with the
     * toolbar. In every other case it stays inside this fragment, exactly where the layout file
     * puts it. Calling this repeatedly is harmless.
     */
    private void updateHeaderLayout() {
        final MainActivity mainActivity = getMainActivity();
        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final boolean bottom = mainTabsPositionBottom;
        // Tabs which are pinned to the bottom of the screen cannot be part of the header
        final boolean tabsInAppBar = collapseHeader && !bottom && mainActivity != null;

        final RelativeLayout.LayoutParams pagerParams =
                (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        if (tabsInAppBar) {
            moveTabLayoutToAppBar(mainActivity.getMainAppBar());
            // The pager fills the whole fragment, the header floats above it
            pagerParams.removeRule(ABOVE);
            pagerParams.removeRule(BELOW);
        } else {
            moveTabLayoutToFragmentRoot();

            final RelativeLayout.LayoutParams tabParams =
                    (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
            tabParams.removeRule(bottom ? ALIGN_PARENT_TOP : ALIGN_PARENT_BOTTOM);
            tabParams.addRule(bottom ? ALIGN_PARENT_BOTTOM : ALIGN_PARENT_TOP);
            pagerParams.removeRule(bottom ? BELOW : ABOVE);
            pagerParams.addRule(bottom ? ABOVE : BELOW, R.id.main_tab_layout);
            tabLayout.setLayoutParams(tabParams);
        }

        tabLayout.setSelectedTabIndicatorGravity(
                bottom ? INDICATOR_GRAVITY_TOP : INDICATOR_GRAVITY_BOTTOM);
        viewPager.setLayoutParams(pagerParams);

        tabLayout.setBackgroundColor(ThemeHelper.resolveColorFromAttr(requireContext(),
                bottom ? android.R.attr.windowBackground : R.attr.colorPrimary));

        final int iconColor = bottom
                ? ThemeHelper.resolveColorFromAttr(requireContext(), android.R.attr.colorAccent)
                : Color.WHITE;
        tabLayout.setTabRippleColor(ColorStateList.valueOf(iconColor).withAlpha(32));
        tabLayout.setTabIconTint(ColorStateList.valueOf(iconColor));
        tabLayout.setSelectedTabIndicatorColor(iconColor);

        if (mainActivity != null) {
            mainActivity.setHeaderCollapseEnabled(collapseHeader);
        }
    }

    private void moveTabLayoutToAppBar(final AppBarLayout appBar) {
        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        if (tabLayout.getParent() == appBar) {
            return;
        }
        detachTabLayout();

        final AppBarLayout.LayoutParams params = new AppBarLayout.LayoutParams(
                AppBarLayout.LayoutParams.MATCH_PARENT,
                AppBarLayout.LayoutParams.WRAP_CONTENT);
        params.setScrollFlags(MainActivity.HEADER_SCROLL_FLAGS);
        appBar.addView(tabLayout, appBar.getChildCount(), params);
    }

    private void moveTabLayoutToFragmentRoot() {
        if (binding == null) {
            return;
        }
        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final RelativeLayout root = binding.getRoot();
        if (tabLayout.getParent() == root) {
            return;
        }
        detachTabLayout();

        root.addView(tabLayout, 0, new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT));
    }

    private void detachTabLayout() {
        final ViewParent parent = binding.mainTabLayout.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(binding.mainTabLayout);
        }
    }

    @Nullable
    private MainActivity getMainActivity() {
        return getActivity() instanceof MainActivity ? (MainActivity) getActivity() : null;
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());

        // Every page starts with a visible header, no matter how far the previous one was scrolled
        final MainActivity mainActivity = getMainActivity();
        if (mainActivity != null) {
            mainActivity.getMainAppBar().setExpanded(true, false);
        }
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) { }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
    }

    private static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final ExtractionException e) {
                ErrorUtil.showUiErrorSnackbar(context, "Getting fragment item", e);
                return new BlankFragment();
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            return fragment;
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
