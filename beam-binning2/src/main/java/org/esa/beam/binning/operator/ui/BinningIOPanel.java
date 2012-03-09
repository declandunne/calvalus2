/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.binning.operator.ui;

import com.bc.ceres.swing.TableLayout;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.io.FileArrayEditor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.io.File;

/**
 * The panel in the binning operator UI which allows for setting input products and the path of the output product.
 *
 * @author Olaf Danne
 * @author Thomas Storm
 */
class BinningIOPanel extends JPanel {

    private final AppContext appContext;
    private final BinningModel binningModel;
    private FileArrayEditor sourceFileEditor;
    private TargetProductSelector targetProductSelectorPanel;

    BinningIOPanel(AppContext appContext, BinningModel binningModel, TargetProductSelector targetProductSelectorPanel) {
        this.appContext = appContext;
        this.binningModel = binningModel;
        final FileArrayEditor.EditorParent context = new FilePathContext(appContext);
        sourceFileEditor = new FileArrayEditor(context, "Source products") {
            @Override
            protected JFileChooser createFileChooserDialog() {
                final JFileChooser fileChooser = super.createFileChooserDialog();
                fileChooser.setDialogTitle("Binning - Open Source Product(s)");
                return fileChooser;
            }
        };
        this.targetProductSelectorPanel = targetProductSelectorPanel;
        final SourceProductSelector sourceProductSelectorPanel = new SourceProductSelector(appContext);
        sourceProductSelectorPanel.setProductFilter(null); // todo -- set product filter
        init();
    }

    private void init() {
        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableWeightY(0.0);
        tableLayout.setTablePadding(3, 3);
        setLayout(tableLayout);
        tableLayout.setRowWeightY(0, 1.0);
        add(createSourceProductsPanel());
        add(createTargetProductPanel());
    }

    private JPanel createSourceProductsPanel() {
        final FileArrayEditor.FileArrayEditorListener listener = new FileArrayEditor.FileArrayEditorListener() {
            @Override
            public void updatedList(final File[] files) {
                final SwingWorker worker = new SwingWorker() {
                    @Override
                    protected Object doInBackground() throws Exception {
                        binningModel.setProperty(BinningModel.PROPERTY_KEY_SOURCE_PRODUCTS, files);
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                        } catch (Exception e) {
                            final String msg = String.format("Cannot display source products.\n%s", e.getMessage());
                            appContext.handleError(msg, e);
                        }
                    }
                };
                worker.execute();
            }
        };
        sourceFileEditor.setListener(listener);


        JButton addFileButton = sourceFileEditor.createAddFileButton();
        JButton removeFileButton = sourceFileEditor.createRemoveFileButton();
        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTablePadding(4, 4);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableWeightY(0.0);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);

        final JPanel sourceProductPanel = new JPanel(tableLayout);
        sourceProductPanel.setBorder(BorderFactory.createTitledBorder("Source Products"));
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addFileButton);
        buttonPanel.add(removeFileButton);
        tableLayout.setRowPadding(0, new Insets(1, 4, 1, 4));
        sourceProductPanel.add(buttonPanel);

        final JComponent fileArrayComponent = sourceFileEditor.createFileArrayComponent();
        tableLayout.setRowWeightY(1, 1.0);
        sourceProductPanel.add(fileArrayComponent);


        return sourceProductPanel;
    }

    private JPanel createTargetProductPanel() {
        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableFill(TableLayout.Fill.BOTH);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableWeightY(1.0);
        tableLayout.setTablePadding(3, 3);

        final JPanel targetProductPanel = new JPanel(tableLayout);
        targetProductPanel.setBorder(BorderFactory.createTitledBorder("Target Product"));
        targetProductPanel.add(targetProductSelectorPanel.createDefaultPanel());

        return targetProductPanel;
    }

    private static class FilePathContext implements FileArrayEditor.EditorParent {

        private final AppContext applicationContext;
        private static final String INPUT_PRODUCT_DIR_KEY = "gpf.binning.input.product.dir";

        private FilePathContext(AppContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        public File getUserInputDir() {
            return getInputProductDir();
        }

        @Override
        public void setUserInputDir(File newDir) {
            setInputProductDir(newDir);
        }

        private void setInputProductDir(final File currentDirectory) {
            applicationContext.getPreferences().setPropertyString(INPUT_PRODUCT_DIR_KEY,
                                                                  currentDirectory.getAbsolutePath());
        }

        private File getInputProductDir() {
            final String path = applicationContext.getPreferences().getPropertyString(INPUT_PRODUCT_DIR_KEY);
            final File inputProductDir;
            if (path != null) {
                inputProductDir = new File(path);
            } else {
                inputProductDir = null;
            }
            return inputProductDir;
        }
    }

}
