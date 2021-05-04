package org.esa.snap.grapheditor.ui.components.utils;


import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * Simple dispatcher to enable keyboard interaction with the GraphPanel.
 *
 * @author Martino Ferrari (CS Group)
 */
public class GraphKeyEventDispatcher implements KeyEventDispatcher {
    private final KeyListener defaultListener;

    /**
     * Create the dispatcher and set the default key listener.
     * @param listener default key listener
     */
    public GraphKeyEventDispatcher(KeyListener listener){
        defaultListener = listener;
    }

    /**
     * Dispatch a event to the default listener if the source of the event is the listener itself.
     *
     * @param event event to be dispatched
     * @return if the KeyEvent has been managed or not
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (defaultListener == event.getSource()) {
            switch(event.getID()) {
                case (KeyEvent.KEY_RELEASED):
                    defaultListener.keyReleased(event);
                    break;
                case (KeyEvent.KEY_PRESSED):
                case (KeyEvent.KEY_TYPED):
                    defaultListener.keyPressed(event);
                    break;
            }
            return true;
        }
        return false;
    }

}