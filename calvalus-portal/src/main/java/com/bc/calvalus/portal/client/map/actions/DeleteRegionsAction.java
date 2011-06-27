package com.bc.calvalus.portal.client.map.actions;

import com.bc.calvalus.portal.client.Dialog;
import com.bc.calvalus.portal.client.map.AbstractMapAction;
import com.bc.calvalus.portal.client.map.Region;
import com.bc.calvalus.portal.client.map.RegionMap;
import com.google.gwt.user.client.ui.Label;

/**
 * todo - add api doc
 *
 * @author Norman Fomferra
 */
public class DeleteRegionsAction extends AbstractMapAction {

    private static final String TITLE = "Delete Regions";

    public DeleteRegionsAction() {
        super("D", "Delete selected region(s)");
    }

    @Override
    public void run(final RegionMap regionMap) {
        final Region[] selectedRegions = regionMap.getRegionSelectionModel().getSelectedRegions();
        if (selectedRegions.length == 0) {
            Dialog.showMessage(TITLE, "No regions selected.");
        } else if (selectedRegions.length == 1) {
            final Region selectedRegion = selectedRegions[0];
            if (!selectedRegion.isUserRegion()) {
                Dialog.showMessage(TITLE, "You can only delete your own regions.");
                return;
            }
            Dialog dialog = new Dialog(TITLE,
                                       new Label("Really delete region '" + selectedRegion.getName() + "'?"),
                                       Dialog.ButtonType.OK, Dialog.ButtonType.CANCEL) {
                @Override
                protected void onOk() {
                    regionMap.removeRegion(selectedRegion);
                    super.onOk();
                }
            };
            dialog.show();
        } else {
            Dialog dialog = new Dialog(TITLE,
                                       new Label("Really delete " + selectedRegions.length + " regions?"),
                                       Dialog.ButtonType.OK, Dialog.ButtonType.CANCEL) {
                @Override
                protected void onOk() {
                    int n = 0;
                    for (Region selectedRegion : selectedRegions) {
                        if (selectedRegion.isUserRegion()) {
                            regionMap.removeRegion(selectedRegion);
                            n++;
                        }
                    }
                    super.onOk();
                    if (n == 0) {
                        Dialog.showMessage(TITLE, "The selected regions could not be deleted.");
                    } else if (n < selectedRegions.length) {
                        Dialog.showMessage(TITLE, "Some of the selected regions could not be deleted.");
                    }
                }
            };
            dialog.show();
        }
    }
}
