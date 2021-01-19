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
package org.esa.snap.grapheditor.gpf.ui;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;

import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.gpf.descriptor.ParameterDescriptor;
import org.esa.snap.ui.AppContext;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import java.util.Map;

/**
 * Default OperatorUI for operators using @parameter
 */
public class DefaultUI extends BaseOperatorUI {

    @Override
    public JComponent CreateOpTab(final String operatorName, final Map<String, Object> parameterMap,
            final AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final BindingContext context = new BindingContext(propertySet);

        initParameters();

        final PropertyPane parametersPane = new PropertyPane(context);
        return new JScrollPane(parametersPane.createPanel());
    }

    @Override
    public void initParameters() {
        updateSourceBands();
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {
        updateSourceBands();

    }

    private void updateSourceBands() {
        if (propertySet == null)
            return;

        final Property[] properties = propertySet.getProperties();
        for (Property p : properties) {
            final PropertyDescriptor descriptor = p.getDescriptor();
            if (unifiedMetadataMap.containsKey(p.getName())) {
                if (sourceProducts != null && unifiedMetadataMap.get(p.getName())
                        .getRasterDataNodeClass() == org.esa.snap.core.datamodel.Band.class) {
                    final String[] bandNames = getBandNames();
                    if (bandNames.length > 0) {
                        final ValueSet valueSet = new ValueSet(bandNames);
                        descriptor.setValueSet(valueSet);
                        try {
                            //check if the updated bandnames contains a previous selected band
                            boolean contains = ArrayUtils.contains(bandNames, p.getValue());
                            if (p.getValue() == null || !contains)
                                p.setValue(bandNames[0]);
                        } catch (ValidationException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            new ValidationException("Band names are not consistents.").printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public Map<String, Object> getParameters() {
        return paramMap;
    }
}
