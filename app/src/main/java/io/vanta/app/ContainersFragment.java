package io.vanta.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.vanta.app.container.Container;
import io.vanta.app.container.ContainerManager;
import io.vanta.app.container.DXWrappers;
import io.vanta.app.container.GraphicsDrivers;
import io.vanta.app.contentdialog.ContentDialog;
import io.vanta.app.contentdialog.StorageInfoDialog;
import io.vanta.app.core.DefaultVersion;
import io.vanta.app.core.KeyValueSet;
import io.vanta.app.core.PreloaderDialog;
import io.vanta.app.core.WineInfo;
import io.vanta.app.xenvironment.RootFS;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ContainersFragment extends Fragment {
    private static final String FILTER_ALL = "all";
    private static final String FILTER_FAVORITES = "favorites";
    private static final String FILTER_RECENT = "recent";
    private RecyclerView recyclerView;
    private View emptyState;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;
    private SharedPreferences preferences;
    private TextView emptyTitle;
    private TextView emptyDescription;
    private String activeFilter = FILTER_ALL;
    private String searchQuery = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadContainersList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.app_name);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.containers_fragment, container, false);
        recyclerView = view.findViewById(R.id.RecyclerView);
        Context context = recyclerView.getContext();
        emptyState = view.findViewById(R.id.TVEmptyText);
        emptyTitle = view.findViewById(R.id.TVEmptyTitle);
        emptyDescription = view.findViewById(R.id.TVEmptyDescription);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        view.findViewById(R.id.BTNewContainer).setOnClickListener((button) -> createContainer());
        setFilter(view.findViewById(R.id.BTFilterAll), FILTER_ALL);
        setFilter(view.findViewById(R.id.BTFilterFavorites), FILTER_FAVORITES);
        setFilter(view.findViewById(R.id.BTFilterRecent), FILTER_RECENT);
        EditText search = view.findViewById(R.id.ETSearchContainers);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchQuery = text.toString().trim().toLowerCase(Locale.ROOT);
                loadContainersList();
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (manager != null && recyclerView != null) loadContainersList();
    }

    private void setFilter(TextView view, String filter) {
        view.setSelected(filter.equals(activeFilter));
        view.setOnClickListener((button) -> {
            activeFilter = filter;
            View root = getView();
            if (root != null) {
                root.findViewById(R.id.BTFilterAll).setSelected(FILTER_ALL.equals(filter));
                root.findViewById(R.id.BTFilterFavorites).setSelected(FILTER_FAVORITES.equals(filter));
                root.findViewById(R.id.BTFilterRecent).setSelected(FILTER_RECENT.equals(filter));
            }
            loadContainersList();
        });
    }

    private void loadContainersList() {
        ArrayList<Container> allContainers = manager.getContainers();
        Set<String> favorites = new HashSet<>(preferences.getStringSet("vanta_container_favorites", Collections.emptySet()));
        ArrayList<Container> containers = new ArrayList<>();
        for (Container container : allContainers) {
            if (FILTER_FAVORITES.equals(activeFilter) && !favorites.contains(Integer.toString(container.id))) continue;
            if (FILTER_RECENT.equals(activeFilter) && preferences.getLong(recentKey(container.id), 0L) == 0L) continue;
            if (!searchQuery.isEmpty() && (container.getName() == null ||
                    !container.getName().toLowerCase(Locale.ROOT).contains(searchQuery))) continue;
            containers.add(container);
        }
        if (FILTER_RECENT.equals(activeFilter)) {
            containers.sort((first, second) -> Long.compare(
                    preferences.getLong(recentKey(second.id), 0L),
                    preferences.getLong(recentKey(first.id), 0L)));
        }
        recyclerView.setAdapter(new ContainersAdapter(containers));
        boolean isEmpty = containers.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty && !allContainers.isEmpty()) {
            emptyTitle.setText(R.string.no_matching_containers_title);
            int description = FILTER_FAVORITES.equals(activeFilter) ? R.string.no_favorite_containers :
                    FILTER_RECENT.equals(activeFilter) ? R.string.no_recent_containers : R.string.no_matching_containers;
            emptyDescription.setText(description);
        }
        else {
            emptyTitle.setText(R.string.empty_containers_title);
            emptyDescription.setText(R.string.empty_containers_description);
        }
    }

    private static String recentKey(int containerId) {
        return "vanta_container_recent_"+containerId;
    }

    private boolean isFavorite(Container container) {
        return preferences.getStringSet("vanta_container_favorites", Collections.emptySet())
                .contains(Integer.toString(container.id));
    }

    private void toggleFavorite(Container container) {
        Set<String> favorites = new HashSet<>(preferences.getStringSet("vanta_container_favorites", Collections.emptySet()));
        String id = Integer.toString(container.id);
        if (!favorites.add(id)) favorites.remove(id);
        preferences.edit().putStringSet("vanta_container_favorites", favorites).apply();
        loadContainersList();
    }

    private void createContainer() {
        if (!RootFS.find(getContext()).isValid()) return;
        FragmentManager fragmentManager = getParentFragmentManager();
        fragmentManager.beginTransaction()
            .addToBackStack(null)
            .replace(R.id.FLFragmentContainer, new ContainerDetailFragment())
            .commit();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.containers_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_item_add) {
            createContainer();
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private class ContainersAdapter extends RecyclerView.Adapter<ContainersAdapter.ViewHolder> {
        private final List<Container> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView runButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView summary;
            private final TextView graphics;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.summary = view.findViewById(R.id.TVSummary);
                this.graphics = view.findViewById(R.id.TVGraphics);
                this.runButton = view.findViewById(R.id.BTRun);
                this.menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        public ContainersAdapter(List<Container> data) {
            this.data = data;
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.container_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Container item = data.get(position);
            holder.imageView.setImageResource(R.drawable.icon_container);
            holder.title.setText(item.getName());
            Context context = holder.itemView.getContext();
            WineInfo wineInfo = WineInfo.fromIdentifier(context, item.getWineVersion());
            holder.summary.setText(wineInfo+"  ·  "+item.getScreenSize());
            String[] graphicsDrivers = GraphicsDrivers.parseIdentifiers(item.getGraphicsDriver());
            KeyValueSet[] graphicsConfig = GraphicsDrivers.parseConfigs(item.getGraphicsDriver(), item.getGraphicsDriverConfig());
            String vulkanVersion = graphicsDrivers[0].equals(GraphicsDrivers.TURNIP) ?
                    graphicsConfig[0].get("version", DefaultVersion.TURNIP) : DefaultVersion.valueOf(graphicsDrivers[0]);
            String openGLVersion = graphicsConfig[1].get("version", DefaultVersion.valueOf(graphicsDrivers[1]));

            KeyValueSet[] dxwrapperConfig = DXWrappers.parseConfigs(item.getDXWrapper(), item.getDXWrapperConfig());
            String wrapper = DXWrappers.getName(item.getDXWrapper());
            if (item.getDXWrapper().equals(DXWrappers.DXVK)) {
                wrapper += " "+dxwrapperConfig[0].get("version", DefaultVersion.DXVK(graphicsDrivers[0]));
            }
            else if (item.getDXWrapper().equals(DXWrappers.WINED3D)) {
                wrapper += " "+dxwrapperConfig[0].get("version", DefaultVersion.WINED3D);
            }
            String vkd3dVersion = dxwrapperConfig[1].get("version", DefaultVersion.VKD3D);
            holder.graphics.setText(GraphicsDrivers.getName(graphicsDrivers[0])+" "+vulkanVersion+"  ·  "+
                    GraphicsDrivers.getName(graphicsDrivers[1])+" "+openGLVersion+"\n"+wrapper+"  ·  VKD3D "+vkd3dVersion);
            holder.runButton.setOnClickListener((view) -> runContainer(item));
            holder.menuButton.setOnClickListener((view) -> showListItemMenu(view, item));
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, Container container) {
            MainActivity activity = (MainActivity)getActivity();
            PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
            listItemMenu.inflate(R.menu.container_popup_menu);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);
            listItemMenu.getMenu().findItem(R.id.menu_item_toggle_favorite).setTitle(
                    isFavorite(container) ? R.string.remove_from_favorites : R.string.add_to_favorites);

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                switch (menuItem.getItemId()) {
                    case R.id.menu_item_toggle_favorite:
                        toggleFavorite(container);
                        break;
                    case R.id.menu_item_file_manager:
                        activity.showFragment(new ContainerFileManagerFragment(container.id));
                        break;
                    case R.id.menu_item_edit:
                        activity.showFragment(new ContainerDetailFragment(container.id));
                        break;
                    case R.id.menu_item_duplicate:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_duplicate_this_container, () -> {
                            preloaderDialog.show(R.string.duplicating_container);
                            manager.duplicateContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_remove:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_container, () -> {
                            preloaderDialog.show(R.string.removing_container);
                            manager.removeContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_info:
                        (new StorageInfoDialog(activity, container)).show();
                        break;
                }
                return true;
            });
            listItemMenu.show();
        }

        private void runContainer(Container container) {
            preferences.edit().putLong(recentKey(container.id), System.currentTimeMillis()).apply();
            Activity activity = getActivity();
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            activity.startActivity(intent);
        }
    }
}
