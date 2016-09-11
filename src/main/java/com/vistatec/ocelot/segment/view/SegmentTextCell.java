/*
 * Copyright (C) 2013-2015, VistaTEC or third-party contributors as indicated
 * by the @author tags or express copyright attribution statements applied by
 * the authors. All third-party contributions are distributed under license by
 * VistaTEC.
 *
 * This file is part of Ocelot.
 *
 * Ocelot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ocelot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, write to:
 *
 *     Free Software Foundation, Inc.
 *     51 Franklin Street, Fifth Floor
 *     Boston, MA 02110-1301
 *     USA
 *
 * Also, see the full LGPL text here: <http://www.gnu.org/copyleft/lesser.html>
 */
package com.vistatec.ocelot.segment.view;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.TransferHandler;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vistatec.ocelot.segment.model.CodeAtom;
import com.vistatec.ocelot.segment.model.SegmentAtom;
import com.vistatec.ocelot.segment.model.SegmentVariant;

/**
 * Representation of source/target segment text in segment table view.
 * Handles the style of the text with Inline tags and the link between
 * the editor behavior and the underlying data structure.
 */
public class SegmentTextCell extends JTextPane {
    private static final long serialVersionUID = 1L;

    private static Logger LOG = LoggerFactory.getLogger(SegmentTextCell.class);
    public static final String tagStyle = "tag", regularStyle = "regular",
            insertStyle = "insert", deleteStyle = "delete", enrichedStyle = "enriched", highlightStyle="highlight", currHighlightStyle="currHighlight";
    private int row = -1;
    private SegmentVariant vOrig;
    private SegmentVariant v;
    private boolean raw;
    
    private boolean inputMethodChanged;

    // Shared styles table
    private static final StyleContext styles = new StyleContext();
    static {
        Style style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style regular = styles.addStyle(regularStyle, style);

        Style s = styles.addStyle(tagStyle, regular);
        StyleConstants.setBackground(s, Color.LIGHT_GRAY);

        Style insert = styles.addStyle(insertStyle, s);
        StyleConstants.setForeground(insert, Color.BLUE);
        StyleConstants.setUnderline(insert, true);

        Style delete = styles.addStyle(deleteStyle, insert);
        StyleConstants.setForeground(delete, Color.RED);
        StyleConstants.setStrikeThrough(delete, true);
        StyleConstants.setUnderline(delete, false);
        
        Style highlight = styles.addStyle(highlightStyle, regular);
        StyleConstants.setBackground(highlight, Color.yellow);
        
        Style currHighlight = styles.addStyle(currHighlightStyle, regular);
        StyleConstants.setBackground(currHighlight, Color.green);
    }

    /**
     * Create a dummy cell for the purposes of cell sizing.  This cell
     * doesn't contain the style information and isn't linked to any of
     * the control logic.
     * @return dummy cell
     */
    public static SegmentTextCell createDummyCell() {
        return new SegmentTextCell();
    }

    /**
     * Create an empty cell for the purpose of holding live content. This
     * cell contains style information and is linked to the document.
     * @return real cell
     */
    public static SegmentTextCell createCell() {
        return new SegmentTextCell(styles);
    }

    /**
     * Create an empty cell holding the specified content. This
     * cell contains style information and is linked to the document.
     * @param v
     * @param raw
     * @param isBidi whether the cell contains bidi content
     * @return
     */
    public static SegmentTextCell createCell(int row, SegmentVariant v, boolean raw, boolean isBidi) {
        return new SegmentTextCell(row, v, raw, isBidi);
    }

    private SegmentTextCell(StyleContext styleContext) {
        super(new DefaultStyledDocument(styleContext));
        setEditController();
        addCaretListener(new TagSelectingCaretListener());
        setTransferHandler(new TagAwareTransferHandler());
        setDragEnabled(true);
        setDropMode(DropMode.INSERT);
        addMouseListener(new ContextMenuListener());
    }

    private SegmentTextCell() {
        super();
    }

    private SegmentTextCell(int row, SegmentVariant v, boolean raw, boolean isBidi) {
        this(styles);
        setVariant(row, v, raw);
        setBidi(isBidi);
    }

    public void setBidi(boolean isBidi) {
        if (isBidi) {
            setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        }
    }

    /**
     * A caret listener that detects selections that encompass
     * only part of tags and automatically expand the selection
     * to include full tags.  This produces cascading CaretUpdate
     * events, but the cycle should stop after a single additional
     * update.
     */
    class TagSelectingCaretListener implements CaretListener {
        @Override
        public void caretUpdate(CaretEvent e) {
            if (e.getDot() != e.getMark()) {
                int origStart = Math.min(e.getDot(), e.getMark());
                int origEnd = Math.max(e.getDot(), e.getMark());
                int start = v.findSelectionStart(origStart);
                int end = v.findSelectionEnd(origEnd);
                if (start != origStart) {
                    setSelectionStart(start);
                }
                if (end != origEnd) {
                    setSelectionEnd(end);
                }
            }
        }
    }

    public final void setEditController() {
        StyledDocument styledDoc = getStyledDocument();
        if (styledDoc instanceof AbstractDocument) {
            AbstractDocument doc = (AbstractDocument)styledDoc;
            doc.setDocumentFilter(new SegmentFilter());
        }
    }

    public final void setDisplayCategories() {
        Style style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        StyledDocument styleDoc = this.getStyledDocument();
        Style regular = styleDoc.addStyle(regularStyle, style);

        Style highlight = styleDoc.addStyle(highlightStyle, regular);
        StyleConstants.setBackground(highlight, Color.yellow);
        
        Style currHighlight = styleDoc.addStyle(currHighlightStyle, regular);
        StyleConstants.setBackground(currHighlight, Color.green);
        
        Style s = styleDoc.addStyle(tagStyle, regular);
        StyleConstants.setBackground(s, Color.LIGHT_GRAY);

        Style insert = styleDoc.addStyle(insertStyle, s);
        StyleConstants.setForeground(insert, Color.BLUE);
        StyleConstants.setUnderline(insert, true);

        Style delete = styleDoc.addStyle(deleteStyle, insert);
        StyleConstants.setForeground(delete, Color.RED);
        StyleConstants.setStrikeThrough(delete, true);
        StyleConstants.setUnderline(delete, false);
        
        Style enriched = styleDoc.addStyle(enrichedStyle, regular);
        StyleConstants.setForeground(enriched, Color.BLUE);
        StyleConstants.setUnderline(enriched, true);
        
    }

    public void setTextPane(List<String> styledText) {
        StyledDocument doc = this.getStyledDocument();
        try {
            for (int i = 0; i < styledText.size(); i += 2) {
                System.out.println("Inserting " + styledText.get(i) + " at " + doc.getLength() + " with style "
                        + styledText.get(i + 1));
                doc.insertString(doc.getLength(), styledText.get(i),
                        doc.getStyle(styledText.get(i + 1)));
            }
        } catch (BadLocationException ex) {
            LOG.error("Error rendering text", ex);
        }
    }

    public SegmentVariant getVariant() {
        return this.v;
    }

    public final void setVariant(int row, SegmentVariant v, boolean raw) {
        this.row = row;
        this.v = v;
        this.vOrig = v.createCopy();
        this.raw = raw;
        syncModelToView();
    }

    private void syncModelToView() {
        SegmentVariant tmp = v;
        try {
            // We temporarily set v to null here to get around the
            // SegmentFilter, which will prevent us from clearing the text if
            // there are tags.
            v = null;
            StyledDocument doc = getStyledDocument();
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            LOG.debug("", e);
        } finally {
            v = tmp;
        }
        if (v != null) {
            setTextPane(v.getStyleData(raw));
        }
        else {
            setTextPane(new ArrayList<String>());
        }
    }

    public void setTargetDiff(List<String> targetDiff) {
        setTextPane(targetDiff);
    }
    
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.swing.text.JTextComponent#processInputMethodEvent(java.awt.event
     * .InputMethodEvent)
     */
    @Override
    protected void processInputMethodEvent(InputMethodEvent e) {
        /*
         * Some keyboards, such as Traditional Chinese keyboard, trigger the
         * INPUT_METHOD_TEXT_CHANGED event while typing text. This event causes
         * the remove method in the DocumentFilter to be invoked, resulting in
         * some characters erroneously deleted. The inputMethodChanged field
         * value is set to true in case this event is triggered. This field is
         * then checked within the remove method, and the characters are
         * actually removed only if this field is false.
         */
        inputMethodChanged = e.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED;
        super.processInputMethodEvent(e);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Point p = event.getPoint();
        int offset = viewToModel(p);
        if (v != null && v.containsTag(offset, 0)) {
            SegmentAtom atom = v.getAtomAt(offset);
            if (atom instanceof CodeAtom) {
                return ((CodeAtom) atom).getVerboseData();
            }
        }
        return super.getToolTipText(event);
    }


	/**
     * Handles edit behavior in segment text cell.
     */
    public class SegmentFilter extends DocumentFilter {

        // This is also called when initially populating the table,
        // as swing will try to "remove" the old contents.
        @Override
        public void remove(FilterBypass fb, int offset, int length)
                throws BadLocationException {

            if (v != null) {
                // Allow atomic tag deletions
                if (v.containsTag(offset, length)) {
                    int start = v.findSelectionStart(offset);
                    int end = v.findSelectionEnd(offset + length);
                    v.clearSelection(start, end);
                    super.remove(fb, start, end - start);
                } else {
                    // Remove from cell editor
                    super.remove(fb, offset, length);
    
                    if(!inputMethodChanged){
	                    // Remove from underlying segment structure
	                    deleteChars(offset, length);
                    }
                }
            }
            else {
                // TODO: why does this correct the spacing issue?
                super.remove(fb, offset, length);
            }
            inputMethodChanged = false;
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String str,
                AttributeSet a) throws BadLocationException {
            if (length > 0) {
                if (v.containsTag(offset, length)) {
                    int start = v.findSelectionStart(offset);
                    int end = v.findSelectionEnd(offset + length);
                    v.clearSelection(start, end);
                    v.modifyChars(offset, 0, str);
                    super.replace(fb, start, end - start, str, a);
                } else {
                    // Remove from cell editor
                    super.replace(fb, offset, length, str, a);

                    // Remove from underlying segment structure
                    v.modifyChars(offset, length, str);
                }
            } else {
                if (v.canInsertAt(offset)) {
                    // Insert string into cell editor.
                    super.replace(fb, offset, length, str, a);

                    insertChars(str, offset);
                }
            }
            inputMethodChanged = false;

        }

        public void deleteChars(int offset, int charsToRemove) {
            v.modifyChars(offset, charsToRemove, null);
        }

        public void insertChars(String insertText, int offset) {
            v.modifyChars(offset, 0, insertText);
        }
    }

    static class TagAwareTransferHandler extends TransferHandler {

        private static final long serialVersionUID = 1L;
        private boolean didClearSelection = false;

        @Override
        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY_OR_MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            SegmentTextCell cell = (SegmentTextCell) c;
            SegmentVariantSelection selection = new SegmentVariantSelection(cell.row, cell.v.createCopy(),
                    cell.getSelectionStart(), cell.getSelectionEnd());
            System.out.println("Made transferable: " + selection.getDisplayText());
            return new SegmentVariantTransferable(selection);
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            System.out.println("exportDone: " + data);

            try {
                SegmentTextCell cell = (SegmentTextCell) source;
                SegmentVariantSelection sel = (SegmentVariantSelection) data.getTransferData(SELECTION_FLAVOR);
                if (action == TransferHandler.MOVE && !didClearSelection) {
                    // Only clear the original selection here if we didn't
                    // already handle it in importData().
                    clearSelection(cell, sel);
                    cell.syncModelToView();
                    didClearSelection = true;
                }
            } catch (UnsupportedFlavorException | IOException e) {
                LOG.debug("", e);
            }
        }

        @Override
        public boolean canImport(TransferSupport support) {
            System.out.println("canImport: " + support);
            return support.isDataFlavorSupported(SELECTION_FLAVOR)
                    || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            didClearSelection = false;
            System.out.println("importData: " + support);
            SegmentTextCell cell = (SegmentTextCell) support.getComponent();
            if (support.isDataFlavorSupported(SELECTION_FLAVOR)) {
                return importSegmentVariantSelection(cell, support);
            } else if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return importString(cell, support);
            }
            return false;
        }

        void clearSelection(SegmentTextCell cell, SegmentVariantSelection selection) {
            cell.v.clearSelection(selection.getSelectionStart(), selection.getSelectionEnd());
        }

        private boolean importSegmentVariantSelection(SegmentTextCell cell, TransferSupport support) {
            try {
                Transferable trfr = support.getTransferable();
                SegmentVariantSelection sel = (SegmentVariantSelection) trfr.getTransferData(SELECTION_FLAVOR);
                // Check to make sure we're pasting from the same row.
                if (sel.getRow() == cell.row) {
                    int start, end;
                    if (support.isDrop()) {
                        Point p = support.getDropLocation().getDropPoint();
                        start = end = cell.viewToModel(p);
                    } else {
                        start = cell.getSelectionStart();
                        end = cell.getSelectionEnd();
                    }
                    // Check to make sure we're not pasting into any tags
                    if (cell.v.containsTag(start, end - start)) {
                        return false;
                    }

                    boolean isDragMove = support.isDrop() && support.getDropAction() == TransferHandler.MOVE;
                    boolean dragWillInvalidateOriginalOffsets = isDragMove && end < sel.getSelectionStart();
                    if (isDragMove && dragWillInvalidateOriginalOffsets) {
                        // If we are moving by DnD to a location before the
                        // selection, delete the origin selection first so that
                        // the offsets aren't invalidated by inserting the
                        // selection. The "real" way to do this is to have a way
                        // of tracking how the offsets move in the underlying
                        // model, like javax.swing.text.Position, and always
                        // handle clearing the selection in exportDone().
                        // However such an abstraction doesn't appear to be
                        // possible given how e.g. TextContainerVariant stores
                        // and retrieves its atoms.
                        clearSelection(cell, sel);
                        didClearSelection = true;
                    }
                    cell.v.replaceSelection(start, end, sel);
                    cell.syncModelToView();
                    return true;
                } else if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    // We're not pasting from the same row so it's not safe to
                    // import tags. We can import plain text instead.
                    return importString(cell, support);
                }
            } catch (UnsupportedFlavorException | IOException e) {
                LOG.info("", e);
            }
            return false;
        }

        private boolean importString(SegmentTextCell cell, TransferSupport support) {
            try {
                Transferable trfr = support.getTransferable();
                String str = trfr.getTransferData(DataFlavor.stringFlavor).toString();
                System.out.println("Replacing " + cell.getSelectedText() + " with string " + str);
                // Rely on SegmentFilter to protect existing tags
                cell.replaceSelection(str);
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                LOG.debug("", e);
            }
            return false;
        }
    }

    static final DataFlavor SELECTION_FLAVOR = new DataFlavor(SegmentVariantSelection.class,
            SegmentVariantSelection.class.getSimpleName());

    static class SegmentVariantTransferable implements Transferable {

        private static final DataFlavor[] FLAVORS = { SELECTION_FLAVOR, DataFlavor.stringFlavor };

        private final SegmentVariantSelection selection;

        public SegmentVariantTransferable(SegmentVariantSelection selection) {
            this.selection = selection;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return FLAVORS;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return Arrays.asList(FLAVORS).contains(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (SELECTION_FLAVOR.equals(flavor)) {
                return selection;
            } else if (DataFlavor.stringFlavor.equals(flavor)) {
                return selection.getDisplayText();
            }
            throw new UnsupportedFlavorException(flavor);
        }

    }

    public boolean canStopEditing() {
        return v == null || !v.needsValidation() || v.validateAgainst(vOrig);
    }

    class ContextMenuListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                doContextPopup(e.getPoint());
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.isPopupTrigger()) {
                doContextPopup(e.getPoint());
            }
        }

        void doContextPopup(Point p) {
            JPopupMenu menu = makeContextPopup(viewToModel(p));
            menu.show(SegmentTextCell.this, p.x, p.y);
        }
    }

    JPopupMenu makeContextPopup(final int insertionPoint) {
        final List<CodeAtom> missing = v.getMissingTags(vOrig);
        JPopupMenu menu = new JPopupMenu();
        for (final CodeAtom atom : missing) {
            JMenuItem restoreOneItem = menu.add("Restore Missing Tag: " + atom.getData());
            restoreOneItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    v.replaceSelection(insertionPoint, insertionPoint, Arrays.asList(atom));
                    syncModelToView();
                }
            });
        }
        if (missing.size() != 1) {
            // Only offer Restore All if there are zero tags (to make the
            // feature more visible) or if there are multiple tags. The
            // single-tag case is handled above.
            JMenuItem restoreAllItem = menu.add("Restore All Missing Tags");
            restoreAllItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    v.replaceSelection(insertionPoint, insertionPoint, missing);
                    syncModelToView();
                }
            });
            restoreAllItem.setEnabled(!missing.isEmpty());
        }
        return menu;
    }
}