package org.esa.snap.grapheditor.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;

import com.bc.ceres.binding.dom.XppDomElement;
import com.thoughtworks.xstream.io.xml.xppdom.XppDom;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.graph.GraphException;
import org.esa.snap.core.gpf.graph.Node;
import org.esa.snap.core.gpf.graph.NodeSource;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.grapheditor.gpf.ui.OperatorUI;
import org.esa.snap.grapheditor.gpf.ui.UIValidation;
import org.esa.snap.grapheditor.ui.components.interfaces.NodeInterface;
import org.esa.snap.grapheditor.ui.components.interfaces.NodeListener;
import org.esa.snap.grapheditor.ui.components.utils.Constants;
import org.esa.snap.grapheditor.ui.components.utils.GraphManager;
import org.esa.snap.grapheditor.ui.components.utils.GraphicalUtils;
import org.esa.snap.grapheditor.ui.components.utils.NotificationManager;
import org.esa.snap.grapheditor.ui.components.utils.UnifiedMetadata;
import org.javatuples.Pair;

/**
 * NodeGui is the main component of the GraphBuilder and it represents a node
 * that is an instance of an Operator It can self-validate as well as notify the
 * connected nodes of its changes.
 *
 * @author Martino Ferrari (CS Group) and Florian Douziech(CS Group)
 */
public class NodeGui implements NodeListener, NodeInterface {

    public enum ValidationStatus {
        UNCHECKED, VALIDATED, ERROR, WARNING,
    }

    private static final int STATUS_MASK_OVER = 1 << 1;
    private static final int STATUS_MASK_SELECTED = 1 << 2;

    private static final int MAX_LINE_LENGTH = 45;

    private static final Color errorColor = new Color(255, 80, 80, 200);
    private static final Color validateColor = new Color(51, 153, 102, 200);
    private static final Color unknownColor = new Color(233, 229, 225, 230); // Color
    private static final Color activeColor = new Color(254, 223, 176, 180);

    private static final Color optionalColor = new Color(234, 201, 53, 255);

    private static final Color tooltipBackground = new Color(0, 0, 0, 180);
    private static final Color tooltipBorder = Color.white;
    private static final Color tooltipColor = Color.lightGray;

    static final private BasicStroke borderStroke = new BasicStroke(2);
    static final private BasicStroke tooltipStroke = new BasicStroke(1.5f);
    static final private BasicStroke textStroke = new BasicStroke(1);
    static final private BasicStroke activeStroke = new BasicStroke(6);

    static final private int connectionSize = 10;
    static final private int connectionHalfSize = connectionSize / 2;
    static final private int connectionOffset = 15;

    static final private Font textFont = new Font("Ariel", Font.BOLD, 11);

    static final private int minWidth = 60;

    private int x;
    private int y;
    private int width = 90;
    private int height = 30;

    private int textW = -1;
    private int textH = -1;

    private final String name;

    private int status = 0;
    private ValidationStatus validationStatus = ValidationStatus.UNCHECKED;

    private final UnifiedMetadata metadata;
    private final OperatorUI operatorUI;
    private final Operator operator;

    private final Node node;
    private Map<String, Object> configuration;
    private int numInputs;

    private JComponent preferencePanel = null;
    private String[] tooltipText_ = null;
    private boolean tooltipVisible_ = false;
    private int tooltipIndex_ = Constants.CONNECTION_NONE;

    private final ArrayList<NodeListener> nodeListeners = new ArrayList<>();
    private final HashMap<Integer, NodeInterface> incomingConnections = new HashMap<>();

    private boolean hasChanged = false;
    private Product output = null;
    private boolean recomputeOutputNeeded = true;

    /**
     * Create a new Node Gui.
     *
     * @param node          basic Node information
     * @param configuration current node configuration
     * @param metadata      unified metadata
     * @param operatorUI    operator properties ui
     * @param context       execution context
     */
    public NodeGui(Node node, Map<String, Object> configuration, UnifiedMetadata metadata,
            OperatorUI operatorUI, Operator operator) {
        this.x = 0;
        this.y = 0;
        this.metadata = metadata;
        this.operatorUI = operatorUI;

        this.node = node;
        this.name = this.node.getId();
        this.configuration = configuration;
        numInputs = metadata.getMinNumberOfInputs();
        height = Math.max(height, connectionOffset * (numInputs + 1));
        this.operator = operator;
    }

    @Override
    public void drawNode(Graphics2D g) {
        g.setFont(textFont);

        if (textW <= 0) {
            FontMetrics fontMetrics = g.getFontMetrics();

            textH = fontMetrics.getHeight();
            textW = fontMetrics.stringWidth(name);

            width = Math.max(GraphicalUtils.normalizeDimension(textW + 30), minWidth);
        }

        if ((this.status & STATUS_MASK_SELECTED) > 0) {
            Graphics2D gactive = (Graphics2D) g.create();
            gactive.setColor(activeColor);
            gactive.setStroke(activeStroke);
            gactive.drawRoundRect(x - 2, y - 2, width + 4, height + 4, 8, 8);
            gactive.dispose();
        }

        g.setColor(this.color());
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setStroke(borderStroke);
        g.setColor(this.borderColor());
        g.drawRoundRect(x, y, width, height, 8, 8);

        g.setStroke(textStroke);
        if (this.validationStatus == ValidationStatus.ERROR || this.validationStatus == ValidationStatus.VALIDATED) {
            g.setColor(Color.white);
        } else {
            g.setColor(Color.darkGray);
        }

        g.drawString(name, x + (width - textW) / 2, y + (textH + 5));

        paintInputs(g);
        paintOutput(g);
    }

    /**
     * Draw incoming connections (if any).
     * @param g renderer
     */
    public void paintConnections(Graphics2D g) {
        for (int i : incomingConnections.keySet()) {
            Point end = getInputPosition(i);
            Point start = incomingConnections.get(i).getOutputPosition();
            GraphicalUtils.drawConnection(g, start, end, GraphicalUtils.connectionConnectedColor);
        }
    }

    static private ArrayList<String> splitLine(String line) {
        ArrayList<String> result = new ArrayList<>();

        if (line.length() <= MAX_LINE_LENGTH) {
            result.add(line);
        } else {
            int start = 0;
            int N = (int) Math.ceil((double) line.length() / MAX_LINE_LENGTH);
            for (int i = 0; i < N; i++) {
                int end = Math.min(start + MAX_LINE_LENGTH, line.length());
                String subline = line.substring(start, end);
                if (end < line.length() && Character.isLetter(line.charAt(end - 1))
                        && Character.isLetter(line.charAt(end))) {
                    subline += "-";
                }
                result.add(subline);
                start = end;
            }
        }
        return result;
    }

    static private String[] split_text(String input) {
        if (input == null)
            return null;
        ArrayList<String> result = new ArrayList<>();
        for (String line : input.split("\n")) {
            result.addAll(splitLine(line));
        }
        return result.toArray(new String[0]);
    }

    /**
     * Draw tooltip if visible.
     * @param g renderer
     */
    public void drawTooltip(Graphics2D g) {
        if (tooltipVisible_ && tooltipText_ != null) {
            FontMetrics fontMetrics = g.getFontMetrics();

            int textH = fontMetrics.getHeight();
            int tooltipH = textH + (textH + 4) * (tooltipText_.length - 1) + 8;

            int tooltipW = fontMetrics.stringWidth(tooltipText_[0]) + 8;
            for (int i = 1; i < tooltipText_.length; i++) {
                tooltipW = Math.max(tooltipW, fontMetrics.stringWidth(tooltipText_[i]) + 8);
            }

            int tx;
            int ty;
            if (tooltipIndex_ == Constants.CONNECTION_OUTPUT) {
                tx = x + width + connectionSize;
                ty = y + connectionOffset - (tooltipH / 2);
            } else {
                tx = x - tooltipW - connectionSize;
                ty = y + (tooltipIndex_ + 1) * connectionOffset - (tooltipH / 2);
            }
            g.setColor(tooltipBackground);
            g.fillRoundRect(tx, ty, tooltipW, tooltipH, 8, 8);
            g.setStroke(tooltipStroke);
            g.setColor(tooltipBorder);
            g.drawRoundRect(tx, ty, tooltipW, tooltipH, 8, 8);
            g.setStroke(textStroke);
            g.setColor(tooltipColor);
            int stringY = ty + 8 + (textH / 2);
            for (String line : tooltipText_) {
                g.drawString(line, tx + 4, stringY);
                stringY += textH + 4;
            }
        }
    }

    private void paintInputs(Graphics2D g) {
        if (metadata.hasInputs()) {
            int xc = x - connectionHalfSize;
            int yc = y + connectionOffset - connectionHalfSize;
            for (int i = 0; i < numInputs(); i++) {
                boolean optional = metadata.isInputOptional(i);
                if (i > 0 && optional) {
                    g.setColor(optionalColor);
                } else {
                    g.setColor(Color.white);
                }
                g.fillOval(xc, yc, connectionSize, connectionSize);
                g.setStroke(borderStroke);
                g.setColor(borderColor());
                g.drawOval(xc, yc, connectionSize, connectionSize);
                if (i >= metadata.getMinNumberOfInputs()) {
                    g.setColor(Color.lightGray);
                    g.setStroke(textStroke);
                    g.fillOval(xc + 3, yc + 3, connectionSize - 6, connectionSize - 6);
                    g.drawOval(xc + 3, yc + 3, connectionSize - 6, connectionSize - 6);
                }
                yc += connectionOffset;
            }
        }
    }

    private int numInputs() {
        return numInputs;
    }

    private void paintOutput(Graphics2D g) {
        if (metadata.hasOutput()) {
            int xc = x + width - connectionHalfSize;
            int yc = y + connectionOffset - connectionHalfSize;
            g.setColor(Color.white);
            g.fillRect(xc, yc, connectionSize, connectionSize);
            g.setStroke(borderStroke);
            g.setColor(borderColor());
            g.drawRect(xc, yc, connectionSize, connectionSize);
        }
    }

    private Color color() {
        Color c;
        switch (validationStatus) {
            case ERROR:
                c = errorColor;
                break;
            case VALIDATED:
                c = validateColor;
                break;
            case WARNING:
            default:
                c = unknownColor;
                break;
        }
        if ((this.status & STATUS_MASK_OVER) > 0) {
            return c.brighter();
        }
        return c;
    }

    private Color borderColor() {
        return color().darker().darker();
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setPosition(Point p) {
        this.x = p.x;
        this.y = p.y;
    }

    @Override
    public Point getPosition() {
        return new Point(x, y);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }

    private int getInputIndex(Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        if (Math.abs(dx) <= connectionHalfSize && dy > 0) {
            int iy = Math.round((float) dy / connectionOffset);
            if (iy - 1 < numInputs()) {
                int cy = iy * connectionOffset;
                if (Math.abs(dy - cy) <= connectionHalfSize) {
                    return iy - 1;
                }
            }
        }
        return -1;
    }

    private boolean isOverOutput(Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        return (metadata.hasOutput() && Math.abs(dx - width) <= connectionHalfSize
                && Math.abs(dy - connectionOffset) <= connectionHalfSize);
    }

    /**
     * Checks if a point is inside the NodeGui body and connectors
     * 
     * @param p point
     * @return if the point is inside the NodeGui body
     */
    public boolean contains(Point p) {
        int dx = p.x - x;
        int dy = p.y - y;
        boolean inside = (dx >= 0 && dy >= 0 && dx <= width && dy <= height);
        if (inside)
            return true;
        // check if is over a connection input
        if (getInputIndex(p) >= 0)
            return true;
        // check if is over a connection output
        return isOverOutput(p);
    }

    /**
     * Adds the STATUS_OVER_MASK to the NodeGui and show tooltip if needed
     * 
     * @param p mouse position
     * @return if the status changed
     */
    public boolean over(Point p) {
        boolean changed;
        if ((status & STATUS_MASK_OVER) == 0) {
            status += STATUS_MASK_OVER;
        }
        int iy = getConnectionAt(p);
        if (iy != Constants.CONNECTION_NONE) {
            changed = iy != tooltipIndex_;
            show_tooltip(iy);
            return changed;
        }
        changed = tooltipVisible_;
        hide_tooltip();
        return changed;
    }

    /**
     * Remove status mask
     * @return if the status changed
     */
    public boolean none() {
        hide_tooltip();
        if ((status & STATUS_MASK_OVER) > 0) {
            status -= STATUS_MASK_OVER;
            return true;
        }
        return false;
    }

    /**
     * Select nodes and update the operatorUI if needed.
     */
    public void select() {
        if ((status & STATUS_MASK_SELECTED) == 0)
            status += STATUS_MASK_SELECTED;
        updateSources();
    }

    /**
     * Update sources products.
     */
    public void updateSources() {
        if (operatorUI == null) {
            getPreferencePanel();
        }
        if (hasChanged && operatorUI != null) {
            Product[] products = new Product[incomingConnections.size()];
            boolean isComplete = true;
            int i = 0;
            for (int index : incomingConnections.keySet()) {
                products[i] = incomingConnections.get(index).getProduct();
                if (products[i] == null) {
                    isComplete = false;
                }
                i += 1;
            }
            if (products.length > 0 && isComplete) {
                operatorUI.setSourceProducts(products);
            }
            operatorUI.updateParameters();
            hasChanged = false;
            recomputeOutputNeeded = true;

            // initialize panel
            getPreferencePanel();
        }
    }

    private void incomplete() {
        output = null;
        NotificationManager.getInstance().warning(this.getName(),
                "Some input products are missing. Node can not be validated");
        validationStatus = ValidationStatus.WARNING;
    }

    private boolean isComplete() {
        if (this.metadata.getMinNumberOfInputs() == 0)
            return true;
        if (this.incomingConnections.size() == 0 && this.metadata.hasSourceProducts())
            return false;
        // check that all mandatory inputs are connected
        for (String inputName : this.metadata.getMandatoryInputs()) {
            int index = this.metadata.getInputIndex(inputName);
            if (!this.incomingConnections.containsKey(index))
                return false;
        }
        return true;
    }

    private void recomputeOutput() {
        // Check completude
        if (!isComplete()) {
            incomplete();
            return;
        }

        // setting inputs

        for (int i : incomingConnections.keySet()) {
            String sourceName = metadata.getInputName(i);
            Product p = incomingConnections.get(i).getProduct();
            if (p == null) {
                incomplete();
                return;
            }
            NotificationManager.getInstance().info(this.getName(), "source: " + sourceName);
            setOperatorSourceProduct(sourceName,p);

        }

        operatorUI.updateParameters();

        recomputeOutputNeeded = false;
        UIValidation.State state = operatorUI.validateParameters().getState();

        if (state == UIValidation.State.OK) {
            final XppDomElement config = new XppDomElement("parameters");
            try {
                this.operatorUI.convertToDOM(config);
                node.setConfiguration(config);
            } catch (GraphException e) {
                NotificationManager.getInstance().error(this.getName(),
                        "could not retrieve configuration `" + e.getMessage() + "`");
                validationStatus = ValidationStatus.ERROR;
            }

            NotificationManager.getInstance().info(this.getName(), "setting parameters");
            for (String param : configuration.keySet()) {
                setOperatorParameter(param,configuration.get(param));
            }
            try {
                NotificationManager.getInstance().info(this.getName(), "validating");
                output = (Product)getTargetProduct();
                NotificationManager.getInstance().ok(this.getName(), "Validated");
                validationStatus = ValidationStatus.VALIDATED;
            } catch (Exception e) {
                NotificationManager.getInstance().error(this.getName(), e.getMessage());
                output = null;
                validationStatus = ValidationStatus.ERROR;
            }
        } else {
            output = null;
            if (state == UIValidation.State.ERROR) {
                String msg = operatorUI.validateParameters().getMsg();
                NotificationManager.getInstance().error(this.getName(), "Operator UI could not be validated `" + msg + "`" );
                validationStatus = ValidationStatus.ERROR;
            } else if (state == UIValidation.State.WARNING) {
                String msg = operatorUI.validateParameters().getMsg();
                NotificationManager.getInstance().warning(this.getName(), "Operator UI could not be validated `" +msg + "`");
                validationStatus = ValidationStatus.WARNING;
            }
        }

    }

    /**
     * set Operator context parameter with java reflectivity
    */
    private void setOperatorParameter(String name, Object param) {
        try {
            Method contextMethod = operator.getClass().getMethod("setParameter",String.class,Object.class);
            contextMethod.setAccessible(true);
            contextMethod.invoke(operator,name, param);
        } catch (Exception e) {
            NotificationManager.getInstance().error(this.getName(), "Unable to set parameter");
        }
    }

    /**
     * set Operator context source product with java reflectivity
    */
    private void setOperatorSourceProduct(String name, Product product) {

        try {
            Method contextMethod = operator.getClass().getMethod("setSourceProduct",String.class,Product.class);
            contextMethod.setAccessible(true);
            contextMethod.invoke(operator,name, product);
        } catch (Exception e) {
            String msg = "Unable to set source product ";
            NotificationManager.getInstance().error(this.getName(), msg);
        }
    }

    /**
     * set Operator target product with java reflectivity
    */
    private Object getTargetProduct() throws Exception {
        try {
            Method contextMethod = operator.getClass().getMethod("getTargetProduct");
            contextMethod.setAccessible(true);
            return contextMethod.invoke(operator);
        }catch (Exception e) {
            String msg = "Unable to get Target Product";
            throw new Exception(msg);
        }
    }

    @Override
    public Product getProduct() {
        return output;
    }

    /**
     * Deselect a node and revalidate the node if changes have been detected.
     */
    public void deselect() {
        if ((status & STATUS_MASK_SELECTED) > 0)
            status -= STATUS_MASK_SELECTED;
        if (recomputeOutputNeeded || output == null || check_changes()) {
            for (NodeListener l : nodeListeners) {
                l.validateNode(this);
            }

        }
    }

    /**
     * Check the equality between to Node configurations, validating the existence of the same keys and checking the
     * values using the toString method (a better way could be found or implemented!).
     * @param a first node configuration
     * @param b second node configuration
     * @return equality between the two configurations
     */
    private boolean equals(Map<String, Object> a,Map<String, Object> b){
        Set<String> aset = a.keySet();
        Set<String> bset = b.keySet();
        if (aset.size() == bset.size() && bset.containsAll(aset)) {
            for (String key: aset) {
                Object aobj = a.get(key);
                Object bobj = b.get(key);
                if (aobj == null && bobj == null) // if both are null
                    continue;
                if (aobj == null || bobj == null) // if only one of the two is null
                    return false;
                // As not all object implement a correct equality I try to use the toString method and comapring
                // the string... To be see if it actually improve the situation.
                String astr = aobj.toString();
                String bstr = bobj.toString();
                if (!astr.equals(bstr))
                    return false;

            }
            return true;
        }
        return false;
    }

    /**
     * Check if any changes in the node configuration has been detected.
     * @return if a change has been detected
     */
    private boolean check_changes() {
        operatorUI.updateParameters();
        Map<String, Object> update = operatorUI.getParameters();
        boolean res = equals(update, this.configuration);
        this.configuration = new HashMap<>(update);
        return !res;
    }

    /**
     * Disconnect an input connection.
     * @param index input index
     */
    private void disconnect(int index) {
        boolean indexChange = false;
        if (index >= metadata.getMinNumberOfInputs() -1) {
            // change number of inputs only if a dynamic node has been removed
            // else mantain same structure for mandatory inputs 
            if (index != numInputs - 2) {
                // if disconnected node is not the last one
                // trigger recomputation of connection indexes.
                indexChange = true;
            }
            numInputs = Math.max(metadata.getMinNumberOfInputs(), numInputs - 1);
            height = (numInputs + 1) * connectionOffset;
            
        }
        NodeInterface connection = this.incomingConnections.get(index);
        NodeSource[] sources = this.node.getSources();
        NodeSource sourceToDiscconect = null;
        for (NodeSource source: sources) {
            if (source.getSourceNodeId() == connection.getName()) {
                sourceToDiscconect = source;
                break;
            }
        }
        if (sourceToDiscconect != null){
            this.node.removeSource(sourceToDiscconect);
        } else {
            NotificationManager.getInstance().error(this.getName(), "Something wrong....");
        }
        connection.removeNodeListener(this);
        this.incomingConnections.remove(index);
        if (indexChange) {
            HashMap<Integer, NodeInterface> oldConnections = (HashMap<Integer, NodeInterface>) this.incomingConnections.clone();
            this.incomingConnections.clear();
            for (Map.Entry<Integer, NodeInterface> entry : oldConnections.entrySet()) {
                if (entry.getKey() >= index) {
                    this.incomingConnections.put(entry.getKey() - 1, entry.getValue());
                } else {
                    this.incomingConnections.put(entry.getKey(), entry.getValue());
                }
            }
        }
        hasChanged = true;
    }

    /**
     * Delete a node and notifies all connected node.
     */
    public void delete() {
        // To avoid co-modifaction of the nodeListeners arraylist
        ArrayList<NodeListener> listeners = new ArrayList<>(nodeListeners);
        for (NodeListener l: listeners) {
            l.sourceDeleted(this);
        }
        incomingConnections.clear();
    }

    /**
     * Starts a new NodeDragAction when the drag of the node starts.
     * Depending on the mouse position (p), the drag action can be a NodeGui drag, a new connection or a disconnection.
     * @param p mouse position
     * @return new drag action
     */
    public Pair<NodeInterface, Integer> drag(Point p) {
        int iy = getInputIndex(p);
        if (iy >= 0) {
            if (this.incomingConnections.containsKey(iy)) {
                NodeInterface c = this.incomingConnections.get(iy);
                if (c != null) {
                    disconnect(iy);
                    return new Pair<> (c, Constants.CONNECTION_OUTPUT);
                }
            }
            tooltipVisible_ = false;
            return new Pair<> (this, iy);
        }
        if (isOverOutput(p)) {
            tooltipVisible_ = false;
            return new Pair<> (this, Constants.CONNECTION_OUTPUT);
        }
        tooltipVisible_ = false;
        return new Pair<>(this, Constants.CONNECTION_NONE);
    }

    /**
     * Hide the input/output tooltip
     */
    private void hide_tooltip() {
        tooltipVisible_ = false;
        tooltipText_ = null;
        tooltipIndex_ = Constants.CONNECTION_NONE;
    }

    /**
     * Show the tooltip for the given input/output connector.
     * @param connectionIndex input/output connector index
     */
    private void show_tooltip(int connectionIndex) {
        if (connectionIndex == Constants.CONNECTION_OUTPUT && metadata.hasOutput()) {
            // OUTPUT
            tooltipVisible_ = true;
            tooltipText_ = split_text(metadata.getOutputDescription());
            tooltipIndex_ = connectionIndex;
        } else if (connectionIndex >= 0 && metadata.hasInputs()) {
            // INPUT
            tooltipVisible_ = true;
            tooltipText_ = split_text(metadata.getInputName(connectionIndex)+": "+metadata.getInputDescription(connectionIndex));
            tooltipIndex_ = connectionIndex;
        } else {
            hide_tooltip();
        }
    }

    @Override
    public int getActiveConnector(){
        if (hasTooltip()){
            return tooltipIndex_;
        }
        return Constants.CONNECTION_NONE;
    }

    @Override
    public JComponent getPreferencePanel(){
        if (preferencePanel == null) {
            try {
                preferencePanel = operatorUI.CreateOpTab(this.metadata.getName(), configuration , GraphManager.getInstance().getContext(), metadata);
                operatorUI.initParameters(); 
            } catch (Exception e) {
                SystemUtils.LOG.info(e.getMessage());
                preferencePanel = null;
                return null;
            }
        }
        return preferencePanel;
    }

    @Override
    public Point getInputPosition(int index) {
        return new Point(x, y + connectionOffset * (index + 1));
    }

    @Override
    public Point getOutputPosition() {
        return new Point(x + width, y + connectionOffset);
    }

    /**
     * Informs if the tool-tip is visible or not.
     * @return if tool-tip is visible
     */
    public boolean hasTooltip() {
        return tooltipVisible_;
    }

    /**
     * Computes the input or output index at a certain position.
     * @param p position
     * @return input index (>=0), output (NodeGui.CONNECTION_OUTPUT), none (NodeGui.CONNECTION_NONE)
     */
    int getConnectionAt(Point p) {
        int iy = getInputIndex(p);
        if (iy >= 0) {
            return iy;
        }
        if (isOverOutput(p)) {
            return Constants.CONNECTION_OUTPUT;
        }
        return Constants.CONNECTION_NONE;
    }

    @Override
    public boolean isConnectionAvailable(NodeInterface other, int index) {
        if (index == Constants.CONNECTION_OUTPUT)
            return true;
        if (index == Constants.CONNECTION_NONE)
            return false;
        for (NodeInterface c: incomingConnections.values()) {
            if (c != null && other == c) {
                return false;
            }
        }
        return (!incomingConnections.containsKey(index) && (metadata.getMaxNumberOfInputs() <0 || index < metadata.getMaxNumberOfInputs()));
    }

    /**
     * Internal function to add a new input connection
     * @param c connection to be add
     * @param index connection index
     */
    private void connect(NodeInterface c, int index){
        incomingConnections.put(index, c);
        c.addNodeListener(this);
        for (NodeListener listener: this.nodeListeners) {
            listener.connectionAdded(this);
        }
        String name = this.metadata.getInputName(index);
        NodeSource nodeSource =  new NodeSource(name, c.getName());
        this.node.addSource(nodeSource);
        hasChanged = true;
    }

    @Override
    public void addConnection(NodeInterface source, int index) {
        connect(source, index);
        if (!metadata.hasFixedInputs() && index >= metadata.getMinNumberOfInputs() - 1) {
            numInputs += 1;
            height = (numInputs + 1) * connectionOffset;
        }
    }

    @Override
    public Point getConnectorPosition(int connectorIndex) {
        if (connectorIndex >= 0)
            return getInputPosition(connectorIndex);
        if (connectorIndex == Constants.CONNECTION_OUTPUT)
            return getOutputPosition();
        return null;
    }

    @Override
    public void sourceDeleted(Object source) {
        for (int i = 0; i < incomingConnections.size();i ++) {
            NodeInterface c = incomingConnections.get(i);
            if (c == source) {
                // as only one input connection from a node is permitted we can safely break the loop.
                disconnect(i);
                break;
            }
        }

    }

    @Override
    public void connectionAdded(Object source) {
        hasChanged = true;
    }

    @Override
    public void validateNode(Object source) {}

    @Override
    public void addNodeListener(NodeListener l) {
        nodeListeners.add(l);
    }

    @Override
    public void removeNodeListener(NodeListener l) {
        nodeListeners.remove(l);
    }

    @Override
    public Rectangle getBoundingBox(){
        Rectangle r;
        if (tooltipVisible_) {
            int tx = tooltipIndex_ == Constants.CONNECTION_OUTPUT ? x - 8 : x - 8 - 80;
            int ty = y - 8;
            int w = width + 16 + 80;
            int h = height + 16;
            r = new Rectangle(tx, ty, w, h);
        } else {
            r = new Rectangle(x - 8, y - 8, width + 16, height + 16);
        }
        return r;
    }

    @Override
    public int distance(NodeInterface n) {
        if (n == this) {
            return 0;
        }
        int max_d = -1;
        for (NodeInterface c: incomingConnections.values()) {
            int d = c.distance(n);
            if (d > max_d) {
                max_d = d + 1;
            }
        }
        return max_d;
    }

    /**
     * Loads parameters from stored XML element.
     * @param parameters xml presentation root
     */
    public void loadParameters(final XppDom parameters) {
        //displayParameters = params;
        final XppDom dpElem = parameters.getChild("displayPosition");
        if (dpElem != null) {
            this.x = (int) Float.parseFloat(dpElem.getAttribute("x"));
            this.y = (int) Float.parseFloat(dpElem.getAttribute("y"));
        }
    }

    /**
     * Save parameters as XML element.
     * @return Xml node containing the display informations.
     */
    public XppDom saveParameters() {
        XppDom elem = new XppDom("node");
        elem.setAttribute("id", node.getId());
        XppDom dpElem = new XppDom("displayPosition");
        elem.addChild(dpElem);

        dpElem.setAttribute("y", String.valueOf(this.getY()));
        dpElem.setAttribute("x", String.valueOf(this.getX()));

        return elem;
    }

    /**
     * Validate node using the internal recomputeOutput methdo.
     */
    public void validate() {
        recomputeOutput();
    }

    /**
     * Fast invalidation of the node, used when a some previousnode in the graph has an error status.
     */
    public void invalidate() {
        this.validationStatus = ValidationStatus.WARNING;
    }

    /**
     * Get current validation status.
     * @return current status
     */
    public  ValidationStatus getValidationStatus() {
        return this.validationStatus;
    }

    /**
     * Get graph Node
     * @return the node
     */
    public Node getNode() {
        return this.node;
    }

    /**
     * Check if the node is a source node or not (if it does not have any inputs is a source node)
     * @return if it is a source node
     */
    public boolean isSource() {
        return !this.metadata.hasInputs();
    }

    /**
     * Check if the node is a target node or not (if it does not have any outputs or is a Write node)
     * @return if it is a target node
     */
    public boolean isTarget() {
        // NOTE That one node listener is the GraphManager and one is the GraphPanel
        return !this.metadata.hasOutput() || this.getNode().getOperatorName().equals("Write");
    }

}