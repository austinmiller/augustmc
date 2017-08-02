package aug.script.framework;

public interface TextWindowInterface {

    /**
     * <p>Send a full line to the window.  It is not necessary to add a
     * newline.  This does not involve the event thread.</p>
     */
    void echo(String line);
}
