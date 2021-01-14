package org.esa.snap.grapheditor.ui.components;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.Cursor;

import javax.swing.*;

import org.javatuples.Pair;
import org.esa.snap.grapheditor.ui.components.interfaces.AddNodeListener;
import org.esa.snap.grapheditor.ui.components.interfaces.NodeInterface;
import org.esa.snap.grapheditor.ui.components.utils.*;
import org.esa.snap.grapheditor.ui.components.interfaces.GraphListener;
import org.esa.snap.grapheditor.ui.components.interfaces.NodeListener;
import org.esa.snap.grapheditor.ui.components.interfaces.NotificationListener;
import org.esa.snap.grapheditor.ui.components.interfaces.RefreshListener;

/**
 * The GraphPanel is the swing component that visualize the whole graph and enable interaction within it.
 * Most of the interaction is mouse based (drag and drop, pop-up menus) but as well with the keyboard (AddNodeDialog,
 * delete...).
 *
 * @author Martino Ferrari (CS Group)
 */
public class GraphPanel extends JPanel
        implements KeyListener, MouseListener, MouseMotionListener,
        ActionListener, RefreshListener, NodeListener, AddNodeListener, NotificationListener {

    /**
     * Generated UID
     */
    private static final long serialVersionUID = -8787328074424783352L;

    private static final Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
    private static final Cursor overCursor = new Cursor(Cursor.HAND_CURSOR);
    private static final Cursor dragCursor = new Cursor(Cursor.MOVE_CURSOR);
    private static final Cursor connectionCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);

    private BufferedImage gridPattern = null;

    private Point lastMousePosition = new Point(0, 0);

    private NodeGui selectedNode = null;
    private DragAction dragAction = null;

    private final GraphManager graphManager = GraphManager.getInstance();

    private final ArrayList<GraphListener> graphListeners = new ArrayList<>();

    private final JPopupMenu addMenu;

    GraphPanel() {
        super();
        this.setFocusable(true);

        // set event listener for refreshing UI when needed
        GraphManager.getInstance().addEventListener(this);
        NotificationManager.getInstance().addNotificationListener(this);

        this.setBackground(Color.lightGray);
        this.addMenu = new JPopupMenu();
        addMenu.add(graphManager.createOperatorMenu(this));

        this.addMouseMotionListener(this);
        this.addMouseListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new GraphKeyEventDispatcher(this));

        for (NodeGui n: GraphManager.getInstance().getNodes()){
            n.addNodeListener(this);
        }
    }

    private void addNode(NodeGui node) {
        if (node != null) {
            node.setPosition(GraphicalUtils.normalize(lastMousePosition));
            node.addNodeListener(this);
            for (GraphListener listener : graphListeners) {
                listener.created(node);
            }
        }
    }

    /**
     * Paints the panel component
     *
     * @param g The Graphics
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        drawGrid(g2);
        drawNodes(g2);
        drawConnections(g2);
        drawDrag(g2);
        drawTooltip(g2);
       // this.addNodeWidget.paint(g2);
    }

    private void drawGrid(Graphics2D g) {
        if (SettingManager.getInstance().isBgGridVisible()) {
            int width = getWidth();
            int height = getHeight();
            if (gridPattern == null || gridPattern.getWidth() != width || gridPattern.getHeight() != height) {
                // initalize gridPattern image buffer
                gridPattern = GraphicalUtils.gridPattern(width, height);
            }
            // render gridPattern buffer image
            g.drawImage(gridPattern, 0, 0, null);
        }
    }

    private void drawNodes(Graphics2D g) {
        Graphics2D gNode = (Graphics2D) g.create();
        for (NodeGui node : graphManager.getNodes()) {
            node.drawNode(gNode);
        }
        gNode.dispose();
    }

    private void drawConnections(Graphics2D g) {
        Graphics2D gNode = (Graphics2D) g.create();
        for (NodeGui node : graphManager.getNodes()) {
            node.paintConnections(gNode);
        }
        gNode.dispose();
    }

    private void drawDrag(Graphics2D g) {
        if (dragAction != null) {
            Graphics2D gdrag = (Graphics2D)g.create();
            dragAction.draw(gdrag);
            gdrag.dispose();
        }
    }

    private void drawTooltip(Graphics2D g) {
        if (SettingManager.getInstance().isShowToolipEnabled()) {
            Graphics2D gNode = (Graphics2D) g.create();
            for (NodeGui node : graphManager.getNodes()) {
                node.drawTooltip(gNode);
            }
            gNode.dispose();
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
    }

    @Override
    public void keyReleased(KeyEvent event) {
        int key = event.getKeyCode();

        if (key == SettingManager.getInstance().getCommandPanelKey()
                && SettingManager.getInstance().isCommandPanelEnabled()) {
            AddNodeDialog addNodeDialog = new AddNodeDialog(this);
            addNodeDialog.addListener(this);

            this.repaint();// this.addNodeWidget.getBoundingRect(getWidth(), getHeight())); //
                           // this.addNodeWidget.getBoundingRect(getWidth(),
                           // getHeight()));
            return;
        }

        if (key == KeyEvent.VK_SPACE) {
            if (selectedNode != null) {
                deselectNode(selectedNode);
                selectedNode = null;
            }
        } else if (key == KeyEvent.VK_DELETE) { // 127) {
            // DELETE KEY
            removeSelectedNode();
        }
    }

    private void removeSelectedNode() {
        if (selectedNode != null) {
            selectedNode.delete();
            for (GraphListener listener : graphListeners) {
                listener.deleted(selectedNode);
            }
            selectedNode = null;
            repaint();
        }
    }

    @Override
    public void keyTyped(KeyEvent event) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragAction != null) {
            Rectangle r = dragAction.getBoundingBox();
            dragAction.move(e.getPoint());
            Rectangle u = dragAction.getBoundingBox();
            boolean repainted = false;
            if (dragAction.getType() == DragAction.Type.DRAG) {
                repaint(GraphicalUtils.union(r, u));
            } else {
                // reverse loop to get correct node
                for (int i = graphManager.getNodes().size() - 1; i >= 0; i--) {
                    NodeGui node = graphManager.getNodes().get(i);
                    if (node.contains(e.getPoint())) {
                        if (node.over(e.getPoint())) {
                            repainted = true;
                        }
                    } else if (node.none())
                        repainted = true;
                }
                if (repainted)
                    repaint();
                else
                    repaint(GraphicalUtils.union(r, u));
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        lastMousePosition = e.getPoint();
        // reverse loop to get correct node
        boolean isOver = false;
        for (int i = graphManager.getNodes().size() - 1; i >= 0; i--) {
            NodeGui node = graphManager.getNodes().get(i);
            if (node.contains(lastMousePosition)) {
                isOver = true;
                node.over(lastMousePosition);
            } else {
                node.none();
            }
        }
        if (isOver) {
            this.setCursor(overCursor);
        } else {
            this.setCursor(defaultCursor);;
        }
        this.repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        this.requestFocus();
        if (e.getButton() != MouseEvent.BUTTON1) {
            // addNodeWidget.hide();
            if (e.getButton() == MouseEvent.BUTTON3) {
                addMenu.show(this, e.getX(), e.getY());
            }
            return;
        }
        boolean somethingSelected = false;
        // reverse loop to get correct node
        for (int i = graphManager.getNodes().size() - 1; i >= 0; i--) {
            NodeGui node = graphManager.getNodes().get(i);
            if (node.contains(lastMousePosition)) {
                selectNode(node);
                somethingSelected = true;
            } else {
                node.none();
            }
        }
        if (!somethingSelected) {
            deselectNode(selectedNode);
            selectedNode = null;
        }
        repaint();

    }

    @Override
    public void mousePressed(MouseEvent e) {
        this.requestFocus();
        if (e.getButton() == MouseEvent.BUTTON1) {
            Point p = e.getPoint();
            // reverse loop to get correct node
            for (int i = graphManager.getNodes().size() - 1; i >= 0; i--) {
                NodeGui node = graphManager.getNodes().get(i);
                if (node.contains(p)) {
                    Pair<NodeInterface, Integer> action =  node.drag(e.getPoint());
                    int connector = action.getValue1();
                    if (connector == Constants.CONNECTION_NONE) {
                        dragAction = new DragAction(action.getValue0(), p);
                    }
                    else {
                        dragAction = new DragAction(action.getValue0(), connector, p);
                    }

                    break;
                }
            }
        } else {
            dragAction = null;
        }
        if (dragAction != null) {
            if (dragAction.getType() == DragAction.Type.DRAG) {
                this.setCursor(dragCursor);
            } else {
                this.setCursor(connectionCursor);
            }
        }
    }

    private void endDrag() {
        if (dragAction != null) {
            dragAction.drop();
            if (dragAction.getType() == DragAction.Type.DRAG) {
                Point p = GraphicalUtils.normalize(dragAction.getNode().getPosition());
                moveNode(dragAction.getNode(), p.x, p.y);
                this.setCursor(overCursor);
            } else {
                for (NodeGui node: graphManager.getNodes()) {
                    if (node != dragAction.getNode() && node.hasTooltip()) {
                        // means is over a connection point
                        dragAction.connect(node);
                        this.setCursor(overCursor);
                        break;
                    } else {
                        this.setCursor(defaultCursor);
                    }
                }
            }
            dragAction = null;
            repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        endDrag();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Nothing to do...
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Nothing to do...
    }

    private void selectNode(NodeGui node) {
        if (selectedNode != null && selectedNode != node) {
            deselectNode(selectedNode);
        }
        node.select();
        selectedNode = node;
        for (GraphListener listener : graphListeners) {
            listener.selected(node);
        }
    }

    private void deselectNode(NodeGui node) {
        if (node == null)
            return;
        node.deselect();
        for (GraphListener listener : graphListeners) {
            listener.deselected(node);
        }
    }

    private void moveNode(NodeInterface node, int x, int y) {
        node.setPosition(x, y);
        for (GraphListener listener : graphListeners) {
            listener.updated(node);
        }
    }

    public void addGraphListener(GraphListener listener) {
        graphListeners.add(listener);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String opName = e.getActionCommand();
        this.addNode(graphManager.newNode(opName));
    }

    @Override
    public void refresh() {
        this.repaint();
    }


    @Override
    public void sourceDeleted(Object source) {
        // nothing to do, already notified.
    }

    @Override
    public void connectionAdded(Object source) {
        NodeGui srcNode = (NodeGui) source;
        for (GraphListener l: graphListeners) {
            l.updated(srcNode);
        }
    }

    @Override
    public void validateNode(Object node) {
        // nothing to do..
    }

    @Override
    public void newNodeAdded(NodeGui node) {
        addNode(node);
        repaint();
    }

    @Override
    public void newNodeAddedAtCurrentPosition(NodeGui node) {
        if (node == null)
            return;
        node.setPosition(GraphicalUtils.normalize(node.getPosition()));
        node.addNodeListener(this);
        for (GraphListener listener : graphListeners) {
            listener.created(node);
        }
        repaint();
    }

    @Override
    public void newNodeAddedStartDrag(NodeGui node) {
        if (node == null)
            return;
        Point p = new Point(node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() /2);
        node.setPosition(GraphicalUtils.normalize(node.getPosition()));
        node.addNodeListener(this);
        for (GraphListener listener : graphListeners) {
            listener.created(node);
        }
        this.dragAction = new DragAction(node, p);
        repaint();
    }

    @Override
    public void notificationIncoming(Notification n) {
        // Nothing to do...
    }

    @Override
    public void processStart() {
        if (SettingManager.getInstance().isWaitVerificationEnabled()){
            // block interaction while verification is executed
            this.setEnabled(false);
        }
    }

    @Override
    public void processEnd() {
        if (SettingManager.getInstance().isWaitVerificationEnabled()){
            // block interaction while verification is executed
            this.setEnabled(true);
        }
    }

    @Override
    public void progress(int value) {
        // Nothing to do...
    }
}