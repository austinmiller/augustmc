package aug.script.framework;

import java.awt.*;
import java.util.Optional;

@SuppressWarnings("unused")
public interface TextWindowInterface {

    /**
     * <p>Send a full line to the window.  It is not necessary to add a
     * newline.  This does not involve the event thread.</p>
     */
    void echo(String line);

    /**
     * <p>Completely clear the text buffer.</p>
     */
    void clear();

    /**
     * <p>Set whether the text window can be split.</p>
     */
    void setSplittable(boolean splittable);

    /**
     * <p>Set font for the window (if not console).</p>
     *
     * <p>Throw exception if fontName doesn't exist in {@link #getFonts()} or
     * size doesn't exist in {@link #getFontSizes()}.</p>
     */
    void setTextFont(String fontName, int size);

     /**
     * <p>Return an array of acceptable font sizes.</p>
     */
    int[] getFontSizes();

    /**
     * <p>Return an array of acceptable font names.</p>
     */
    String[] getFonts();

    /**
     * <p>Set line by lineNum.</p>
     */
    void setLine(LineWithNum lineWithNum);

    /**
     * <p>Get line by number.  Will include commands.</p>
     */
    Optional<LineEvent> getLine(long lineNum);

    /**
     * <p>Set many lines in one call.</p>
     *
     * <p>If setting many lines, this can be higher performance, as the GUI call to repaint will
     * be guaranteed to happen only once.  Nice for also eliminating any chance of "draw tearing".</p>
     *
     * <p>Ignores commands field on LineWithNum</p>
     */
    void setLines(LineWithNum []lines);

    /**
     * <p>Set whether the text area can be highlighted for copying text.</p>
     */
    void setHighlightable(boolean highlightable);

    /**
     * <p>Set the colorscheme by name for the text window.</p>
     */
    void setColorScheme(String colorSchemeName);

    /**
     * <p>Set the colorscheme by name for the top split pane.</p>
     */
    void setTopColorScheme(String colorSchemeName);

    /**
     * <p>Set the colorscheme by name for the bottom split pane.</p>
     */
    void setBottomColorScheme(String colorSchemeName);

    /**
     * <p>Unsplit the window, if it is split.</p>
     */
    void unsplit();

    /**
     * <p>Split the window, if it isn't split.</p>
     */
    void split();
}
