package aug.script.shared;


import java.io.File;
import java.util.List;

/**
 * <p>This is the set of methods that the client can call against the profile.  An implementation
 * will be passed to the client on init which the client must save if it wishes to manipulate the
 * profile throughout its lifecycle.</p>
 */
@SuppressWarnings("unused")
public interface ProfileInterface {

    /**
     * <p>Send a string to the server.</p>
     *
     * <p>A newline will be automatically appended.  Any existing newlines will be sent as well.</p>
     */
    void send(String string);

    /**
     * <p>Send a string to the server without echoing to the console.</p>
     *
     * <p>A newline will be automatically appended.  Any existing newlines will be sent as well.</p>
     */
    void sendSilently(String string);

    /**
     * <p>Set the window layout.</p>
     *
     * <p>All {@link WindowReference} must be valid and one must refer to the console.  It is not necessary to
     * refer to every window.  If a window is not included, and thus not drawn, it still exists and will still
     * receive updates.</p>
     */
    Boolean setWindowGraph(WindowReference windowReference);

    /**
     * <p>Return a list of existing window names.</p>
     */
    List<String> getWindowNames();

    /**
     * <p>Create a text window with the given name.</p>
     *
     * <p>The name must not already refer to a window.</p>
     */
    TextWindowInterface createTextWindow(String name);

    /**
     * <p>Get a text window by name.</p>
     *
     * <p>This can also acquire the console.</p>
     */
    TextWindowInterface getTextWindow(String name);

    /**
     * <p>This returns a directory that the client can use to persist files.  It is recommended
     * to persist here as the profile will not attempt to read or write to this directory.</p>
     */
    File getClientDir();

    /**
     * <p>Turn colorless logging on or off.  The logging exists in the profile config directory.
     * If the log is already the state that is desired, then nothing happens.</p>
     */
    void logText(Boolean log);

    /**
     * <p>Turn color logging on or off.  The logging exists in the profile config directory.
     * If the log is already the state that is desired, then nothing happens.</p>
     *
     * <p>The log should reflect exactly what was sent by the server.  On a unix-like terminal,
     * reading the file should also be colored.</p>
     */
    void logColor(Boolean log);
}
