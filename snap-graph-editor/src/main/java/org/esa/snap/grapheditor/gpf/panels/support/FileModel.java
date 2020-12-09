/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.snap.grapheditor.gpf.panels.support;

import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.productlibrary.db.ProductEntry;

import java.io.File;

public class FileModel extends BaseFileModel implements FileTableModel {

    protected void setColumnData() {
        titles = new String[]{
                "File Name", "Type", "Acquisition", "Track", "Orbit"
        };

        types = new Class[]{
                String.class, String.class, String.class, String.class, String.class
        };

        widths = new int[]{
                75, 10, 20, 3, 5
        };
    }

    protected TableData createFileStats(final File file) {
        return new FileStats(file);
    }

    protected TableData createFileStats(final ProductEntry entry) {
        return new FileStats(entry);
    }

    private class FileStats extends TableData {

        FileStats(final File file) {
            super(file);
        }

        FileStats(final ProductEntry entry) {
            super(entry);
        }

        protected void updateData() {
            if (file != null) {
                data[0] = file.getName();

            } else if (entry != null) {
                data[0] = entry.getName();
                data[1] = entry.getProductType();
                data[2] = entry.getFirstLineTime().format();

                final MetadataElement meta = entry.getMetadata();
                if (meta != null) {
                    data[3] = String.valueOf(meta.getAttributeInt(AbstractMetadata.REL_ORBIT, 0));
                    data[4] = String.valueOf(meta.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0));
                }
            }
        }

        protected void updateData(final Product product) {

            if (product != null) {
                data[0] = product.getName();
                data[1] = product.getProductType();

                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
                if (absRoot != null) {
                    data[2] = OperatorUtils.getAcquisitionDate(absRoot);
                    data[3] = String.valueOf(absRoot.getAttributeInt(AbstractMetadata.REL_ORBIT, 0));
                    data[4] = String.valueOf(absRoot.getAttributeInt(AbstractMetadata.ABS_ORBIT, 0));
                }
            }
        }
    }
}
