package aug.script.framework;

import java.awt.*;

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
     */
    void setTextFont(Font font);

    /**
     * <p>Set line by lineNum.</p>
     */
    void setLine(long lineNum, String line);

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
