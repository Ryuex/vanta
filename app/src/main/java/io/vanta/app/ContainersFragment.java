package io.vanta.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import java.util.ArrayList;
import java.util.List;

public class ContainersFragment extends Fragment {
    private RecyclerView recyclerView;
    private View emptyState;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;

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
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        view.findViewById(R.id.BTNewContainer).setOnClickListener((button) -> createContainer());
        return view;
    }

    private void loadContainersList() {
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        emptyState.setVisibility(containers.isEmpty() ? View.VISIBLE : View.GONE);
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

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                switch (menuItem.getItemId()) {
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
            Activity activity = getActivity();
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            activity.startActivity(intent);
        }
    }
}
