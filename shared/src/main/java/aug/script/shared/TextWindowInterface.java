package aug.script.shared;

public interface TextWindowInterface {

    /**
     * <p>Send a full line to the window.  It is not necessary to add a
     * newline.</p>
     * @param line
     */
    void echo(String line);
}
