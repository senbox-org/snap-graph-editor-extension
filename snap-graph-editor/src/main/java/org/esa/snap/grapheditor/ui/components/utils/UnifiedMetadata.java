package org.esa.snap.grapheditor.ui.components.utils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.ArrayList;

import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.SourceProducts;
import org.esa.snap.core.gpf.descriptor.OperatorDescriptor;

public class UnifiedMetadata {
    private String name;
    private final String name_lower;
    private final String description;
    private final String category;
    private final String category_lower;
    private final OperatorDescriptor descriptor;

    private final int minNInputs;
    private final int maxNInputs;
    private final boolean hasOutputProduct;
    private final HashMap<String, SourceProduct> sourceProductList = new HashMap<>();
    private final HashMap<String, SourceProducts> sourceProductsList = new HashMap<>();
    private final HashMap<Integer, String> indexNameMap = new HashMap<>();
    private final HashMap<String, Integer> nameIndexMap = new HashMap<>();
    private final ArrayList<String> mandatoryInputs = new ArrayList<>();


    public UnifiedMetadata(final OperatorMetadata opMetadata, final OperatorDescriptor opDescriptor, Field[] fields) {
        this.descriptor = opDescriptor;

        // if (descriptor.getSourceProductsDescriptor() != null) {
        //     minNInputs = descriptor.getSourceProductDescriptors().length + 1;
        //     maxNInputs = -1;
        // } else {
        //     minNInputs = descriptor.getSourceProductDescriptors().length;
        //     maxNInputs = minNInputs;
        // }
        hasOutputProduct = descriptor.getTargetProductDescriptor() != null;

        name = opMetadata.label();
        if (name.length() == 0) {
            name = opMetadata.alias();
        }

        description = opMetadata.description();
        category = opMetadata.category();

        category_lower = category.toLowerCase();
        name_lower = name.toLowerCase();

        for (int index = 0; index < opDescriptor.getSourceProductDescriptors().length; index ++) {
            indexNameMap.put(index, opDescriptor.getSourceProductDescriptors()[index].getName());
            nameIndexMap.put(opDescriptor.getSourceProductDescriptors()[index].getName(), index);
        }

        int minInput = 0;
        int maxInput = 0;
        // Retrieve the decoration of all source product and source proudcts 
        for (Field declaredField : fields) {
            SourceProduct sourceProductAnnotation = declaredField.getAnnotation(SourceProduct.class);
            if (sourceProductAnnotation != null) {
                this.sourceProductList.put(declaredField.getName(), sourceProductAnnotation);
                if (!sourceProductAnnotation.optional()) {
                    this.mandatoryInputs.add(declaredField.getName());
                }
                minInput += 1;
                if (maxInput >= 0) {
                    maxInput += 1;
                } 
            }
            SourceProducts sourceProductsAnnotation = declaredField.getAnnotation(SourceProducts.class);
            if (sourceProductsAnnotation != null) {
                this.sourceProductsList.put(declaredField.getName(), sourceProductsAnnotation);
                if (sourceProductsAnnotation.count() > 0) {
                    minInput += sourceProductsAnnotation.count();
                    if (maxInput >= 0)
                        maxInput += sourceProductsAnnotation.count();
                } else { 
                    minInput += 1;

                    maxInput = -1;
                }
            }
        }

        minNInputs = minInput;
        maxNInputs = maxInput;
        
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "<html>\n<b>"+name+"</b><br>\n"+category+"\n</html>";
    }

    /**
     * Operator description, unused due missing description.
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Operator category the same as in the menu.
     *
     * @return category
     */
    public String getCategory() {
        return category;
    }

    public double fuzzySearch(final String[] keywords) {
        double res = -1.0;
        for (String keyword: keywords) {
            if (name_lower.contains(keyword) && res < keyword.length()) {
                res = keyword.length();
            } else if (category_lower.contains(keyword) && res < 0) {
                res = 0;
            }
        }
        if (res > 0)
            return res / (double)name_lower.length();
        return res;
    }

    public int getMinNumberOfInputs() {
        return minNInputs;
    }

    public int getMaxNumberOfInputs() {
        return maxNInputs;
    }

    public boolean hasFixedInputs() {
        return maxNInputs == minNInputs;
    }

    public boolean hasInputs() {
        return (minNInputs > 0);
    }

    public boolean hasOutput() {
        return hasOutputProduct;
    }

    public String getOutputDescription() {
        if (hasOutput())
            return descriptor.getDescription(); // TODO or get label??
        return "";
    }

    public String getInputDescription(int index) {
        if (hasInputs()) {
            if (index <  descriptor.getSourceProductDescriptors().length) {
                return descriptor.getSourceProductDescriptors()[index].getDescription(); // TODO or label?
            } else if (descriptor.getSourceProductsDescriptor() != null) {
                return descriptor.getSourceProductsDescriptor().getDescription();
            }
        }
        return "";
    }

    public String getInputName(int index) {
        if (hasInputs()) {
            if (this.indexNameMap.containsKey(index)) {
                return this.indexNameMap.get(index);
            } else if (descriptor.getSourceProductsDescriptor() != null) {
                String name =  "sourceProduct";
                if (index > this.getMinNumberOfInputs() - 1) {
                    int localIndex = index - this.getMinNumberOfInputs() + 1;
                    name += "." + localIndex;
                }
                return name;
            } 
        } 
        return "sourceProduct";
    }

    public int getInputIndex(String name) {
        if (this.nameIndexMap.containsKey(name)) {
            return this.nameIndexMap.get(name);
        }
        return -1;
    }

    public boolean isInputOptional(String name){
        if (sourceProductList.containsKey(name)) {
            return sourceProductList.get(name).optional();
        }
        return true;
    }


    public boolean isInputOptional(int index) {
        if (this.indexNameMap.containsKey(index)) {
            return isInputOptional(this.indexNameMap.get(index));
        }
        return true;
    }

    public ArrayList<String> getMandatoryInputs() {
        return this.mandatoryInputs;
    }

    public OperatorDescriptor getDescriptor() {
        return descriptor;
    }


    public boolean hasSourceProducts(){
        return descriptor.getSourceProductsDescriptor() != null;
    }
}
