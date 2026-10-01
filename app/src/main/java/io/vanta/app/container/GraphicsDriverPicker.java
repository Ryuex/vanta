package io.vanta.app.container;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import io.vanta.app.contentdialog.TurnipConfigDialog;
import io.vanta.app.contentdialog.VirGLConfigDialog;
import io.vanta.app.contentdialog.VortekConfigDialog;
import io.vanta.app.core.DefaultVersion;
import io.vanta.app.core.KeyValueSet;
import io.vanta.app.core.StringUtils;
import io.vanta.app.widget.TaggedSelectionBox;

public class GraphicsDriverPicker {
    private final LinearLayout container;

    public GraphicsDriverPicker(LinearLayout container, String selectedGraphicsDriver, String graphicsDriverConfig) {
        this.container = container;
        final Context context = container.getContext();
        container.removeAllViews();
        String[] identifiers = GraphicsDrivers.parseIdentifiers(selectedGraphicsDriver);
        KeyValueSet[] configs = GraphicsDrivers.parseConfigs(selectedGraphicsDriver, graphicsDriverConfig);

        final String[] apiNames = {"Vulkan", "OpenGL"};
        for (int i = 0; i < apiNames.length; i++) {
            final TaggedSelectionBox taggedSelectionBox = new TaggedSelectionBox(context);
            taggedSelectionBox.setLabel(apiNames[i]);
            taggedSelectionBox.setItems(GraphicsDrivers.getItems(apiNames[i]));
            taggedSelectionBox.setSelectedItem(GraphicsDrivers.getName(identifiers[i]));
            taggedSelectionBox.setTag(configs[i].toString());
            taggedSelectionBox.setOnButtonClickListener(() -> {
                final String graphicsDriver = StringUtils.parseIdentifier(taggedSelectionBox.getSelectedItem());
                showGraphicsDriverConfigDialog(graphicsDriver, taggedSelectionBox);
            });
            container.addView(taggedSelectionBox);
        }
    }

    public String getGraphicsDriver() {
        StringBuilder graphicsDriver = new StringBuilder();
        for (int i = 0; i < container.getChildCount(); i++) {
            TaggedSelectionBox taggedSelectionBox = (TaggedSelectionBox)container.getChildAt(i);
            if (graphicsDriver.length() > 0) graphicsDriver.append(',');
            graphicsDriver.append(StringUtils.parseIdentifier(taggedSelectionBox.getSelectedItem()));
        }
        return graphicsDriver.toString();
    }

    public String getGraphicsDriverConfig() {
        StringBuilder graphicsDriverConfig = new StringBuilder();
        for (int i = 0; i < container.getChildCount(); i++) {
            TaggedSelectionBox taggedSelectionBox = (TaggedSelectionBox)container.getChildAt(i);
            if (graphicsDriverConfig.length() > 0) graphicsDriverConfig.append('|');
            graphicsDriverConfig.append(taggedSelectionBox.getTag().toString());
        }
        return graphicsDriverConfig.toString();
    }

    public void applyRecommendedDriver(String selectedGraphicsDriver) {
        String[] identifiers = GraphicsDrivers.parseIdentifiers(selectedGraphicsDriver);
        for (int i = 0; i < container.getChildCount(); i++) {
            TaggedSelectionBox selectionBox = (TaggedSelectionBox)container.getChildAt(i);
            String currentIdentifier = StringUtils.parseIdentifier(selectionBox.getSelectedItem());
            KeyValueSet config = new KeyValueSet(currentIdentifier.equals(identifiers[i]) ? selectionBox.getTag() : "");
            selectionBox.setSelectedItem(GraphicsDrivers.getName(identifiers[i]));
            config.put("version", DefaultVersion.valueOf(identifiers[i]));
            selectionBox.setTag(config.toString());
        }
    }

    private static void showGraphicsDriverConfigDialog(String graphicsDriver, View anchor) {
        switch (graphicsDriver) {
            case GraphicsDrivers.TURNIP:
                (new TurnipConfigDialog(anchor)).show();
                break;
            case GraphicsDrivers.VORTEK:
                (new VortekConfigDialog(anchor)).show();
                break;
            case GraphicsDrivers.VIRGL:
                (new VirGLConfigDialog(anchor)).show();
                break;
        }
    }
}
