package net.sf.jaer.graphics;

// A simple class that searches for a word in
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.View;

/**
 * Takes a document and highlights occurrences of that word
 */
public class WordSearcher {

    int lastOffset = 0;
    private String word = null;
    private ArrayList<Integer> locations = new ArrayList();

    /**
     * Make a new instance using a JTextComponent
     *
     * @param comp - the Document is obtained from the component
     */
    public WordSearcher(JTextComponent comp) {
        this.comp = comp;
        this.painter = new UnderlineHighlighter.UnderlineHighlightPainter(
                Color.blue);
    }

    private int search(String word, int offset, boolean forwards) {
        this.word = word;  // for next and prev to use
        Highlighter highlighter = comp.getHighlighter();

        removeExistingHighlights(highlighter);

        if (word == null || word.equals("")) {
            return -1;
        }

        // Look for the word we are given - case insensitive search
        String content = null;
        try {
            Document d = comp.getDocument();
            content = d.getText(0, d.getLength()).toLowerCase();
        } catch (BadLocationException e) {
            // Cannot happen
            return -1;
        }

        word = word.toLowerCase();
        int firstOffset = -1;
        int lastIndex = offset;
        int wordSize = word.length();
        if (forwards) {
            while ((lastIndex = content.indexOf(word, lastIndex)) != -1) { // find next word starting at lastIndex
                int endIndex = lastIndex + wordSize;
                try {
                    highlighter.addHighlight(lastIndex, endIndex, painter);
                } catch (BadLocationException e) {
                    // Nothing to do
                }
                if (firstOffset == -1) {
                    firstOffset = lastIndex;
                }
                lastIndex = endIndex;
            }
        } else { // backwards
            lastIndex = content.lastIndexOf(word, lastIndex - word.length()); // find last one in doc before current offset (next one backwards)

            try {
                highlighter.addHighlight(lastIndex, lastIndex + word.length(), painter);
            } catch (BadLocationException e) {
                // Nothing to do
            }
            if (firstOffset == -1) {
                firstOffset = lastIndex;
            }

        }
//        System.out.println(String.format("word=%s dir=%s, offset=%,d firstOffset=%,d", word, forwards ? "forwards" : "backwards", offset, firstOffset));
        return firstOffset;

    }

    private void removeExistingHighlights(Highlighter highlighter) {
        // Remove any existing highlights for last word
        Highlighter.Highlight[] highlights = highlighter.getHighlights();
        for (int i = 0; i < highlights.length; i++) {
            Highlighter.Highlight h = highlights[i];
            if (h.getPainter() instanceof UnderlineHighlighter.UnderlineHighlightPainter) {
                highlighter.removeHighlight(h);
            }
        }
    }

    /**
     * Search for a word and return the offset of the first occurrence.
     * Highlights are added for all occurrences found.
     *
     * @param word word to search for (case ignored)
     * @param offset characters in document
     * @param forwards true to go forwards
     * @return first word offset in characters from start of document
     */
    public int searchFirst(String word, int offset, boolean forwards) {
        lastOffset = search(word, offset, forwards);
        return lastOffset;
    }

    protected JTextComponent comp;

    protected Highlighter.HighlightPainter painter;

    public int searchNext() {
        if (word == null || word.isBlank()) {
            return lastOffset;
        }
        lastOffset += word.length();
        lastOffset = search(word, lastOffset, true);
        Highlighter highlighter = comp.getHighlighter();
        removeExistingHighlights(highlighter);
        try {
            highlighter.addHighlight(lastOffset, lastOffset + word.length(), painter);
        } catch (BadLocationException e) {
            // Nothing to do
        }

        return lastOffset;
    }

    public int searchPrevious() {
        lastOffset = search(word, lastOffset, false);
        Highlighter highlighter = comp.getHighlighter();
        removeExistingHighlights(highlighter);
        try {
            highlighter.addHighlight(lastOffset, lastOffset + word.length(), painter);
        } catch (BadLocationException e) {
            // Nothing to do
        }
        return lastOffset;

    }

}

class UnderlineHighlighter extends DefaultHighlighter {

    public UnderlineHighlighter(Color c) {
        painter = (c == null ? sharedPainter : new UnderlineHighlightPainter(c));
    }

    // Convenience method to add a highlight with
    // the default painter.
    public Object addHighlight(int p0, int p1) throws BadLocationException {
        return addHighlight(p0, p1, painter);
    }

    public void setDrawsLayeredHighlights(boolean newValue) {
        // Illegal if false - we only support layered highlights
        if (newValue == false) {
            throw new IllegalArgumentException(
                    "UnderlineHighlighter only draws layered highlights");
        }
        super.setDrawsLayeredHighlights(true);
    }

    // Painter for underlined highlights
    public static class UnderlineHighlightPainter extends
            LayeredHighlighter.LayerPainter {

        public UnderlineHighlightPainter(Color c) {
            color = c;
        }

        public void paint(Graphics g, int offs0, int offs1, Shape bounds,
                JTextComponent c) {
            // Do nothing: this method will never be called
        }

        public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds,
                JTextComponent c, View view) {
            g.setColor(color == null ? c.getSelectionColor() : color);

            Rectangle alloc = null;
            if (offs0 == view.getStartOffset() && offs1 == view.getEndOffset()) {
                if (bounds instanceof Rectangle) {
                    alloc = (Rectangle) bounds;
                } else {
                    alloc = bounds.getBounds();
                }
            } else {
                try {
                    Shape shape = view.modelToView(offs0,
                            Position.Bias.Forward, offs1,
                            Position.Bias.Backward, bounds);
                    alloc = (shape instanceof Rectangle) ? (Rectangle) shape
                            : shape.getBounds();
                } catch (BadLocationException e) {
                    return null;
                }
            }

            FontMetrics fm = c.getFontMetrics(c.getFont());
            int baseline = alloc.y + alloc.height - fm.getDescent() + 1;
            g.drawLine(alloc.x, baseline, alloc.x + alloc.width, baseline);
            g.drawLine(alloc.x, baseline + 1, alloc.x + alloc.width,
                    baseline + 1);
           g.drawLine(alloc.x, baseline + 2, alloc.x + alloc.width,
                    baseline + 2);
           g.drawLine(alloc.x, baseline + 3, alloc.x + alloc.width,
                    baseline + 3);

            return alloc;
        }

        protected Color color; // The color for the underline
    }

    // Shared painter used for default highlighting
    protected static final Highlighter.HighlightPainter sharedPainter = new UnderlineHighlightPainter(
            null);

    // Painter used for this highlighter
    protected Highlighter.HighlightPainter painter;
}
