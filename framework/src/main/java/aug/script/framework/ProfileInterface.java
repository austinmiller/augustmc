package aug.script.framework;


import java.io.File;
import java.util.List;

/**
 * <p>This is the set of methods that the client can call against the profile.  An implementation
 * will be passed to the client on init which the client must save if it wishes to manipulate the
 * profile throughout its lifecycle.</p>
 *
 * <p>Refer {@link ClientInterface} to understand the client thread.  It is not required that the
 * client thread invoke these methods and they're designed to be thread safe.  Where noted, the
 * methods that create an event cannot cause an effect until they're processed by the event
 * thread, thus if these methods are called by the client thread, the created events will wait
 * until the client thread returns control to the event thread or times out.</p>
 */
@SuppressWarnings("unused")
public interface ProfileInterface {

    /**
     * <p>Send a string to the server.</p>
     *
     * <p>A newline will be automatically appended.  Any existing newlines will be sent as well.</p>
     *
     * <p>This generates a high-priority event.</p>
     */
    void send(String string);

    /**
     * <p>Send a string to the server without echoing to the console.</p>
     *
     * <p>A newline will be automatically appended.  Any existing newlines will be sent as well.</p>
     *
     * <p>This generates a high-priority event.</p>
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
     *
     * <p>This generates an event.</p>
     */
    void logText(Boolean log);

    /**
     * <p>Turn color logging on or off.  The logging exists in the profile config directory.
     * If the log is already the state that is desired, then nothing happens.</p>
     *
     * <p>The log should reflect exactly what was sent by the server.  On a unix-like terminal,
     * reading the file should also be colored.</p>
     *
     * <p>This generates an event.</p>
     */
    void logColor(Boolean log);

    /**
     * <p>Ask profile to connect to server.  This will do nothing if profile is already connected.</p>
     *
     * <p>This generates a high-priority event.</p>
     */
    void connect();

    /**
     * <p>Ask profile to disconnect form server.  This will do nothing if profile is not connected.</p>
     *
     * <p>This generates a high-priority event.</p>
     */
    void disconnect();

    /**
     * <p>Ask profile to reconnect to the server.  If the server is not already connected, this behaves
     * like a connect request.</p>
     *
     * <p>This generates a high-priority event.</p>
     */
    void reconnect();

    /**
     * <p>Close the profile.  This will cause all resources, including windows, to be cleaned up. The
     * client will be asked to shutdown, all threads will be shutdown, etc.  It has the same effect as
     * closing the profile from the GUI.</p>
     *
     * <p>This is safe to repeat many times, although only the first call will have an effect.</p>
     *
     * <p>Closing of the profile is deferred and done by the event thread.  If the event thread is waiting
     * for the client to respond and hasn't timed out, closing the profile will be delayed until the event
     * thread regains control.</p>
     *
     * <p>All subsequent events will not be processed, closing the profile takes precedence and will discard
     * any unprocessed server lines, connection events, or any other events.</p>
     */
    void closeProfile();

    /**
     * <p>Send the exception to be printed by the system log.  This is useful if you've caught an exception
     * and want to see it in the system log without letting it propagate out to the event thread.</p>
     */
    void printException(Throwable t);

    /**
     * <p>Ask the profile to stop the client.</p>
     *
     * <p>The client should expect to receive a shutdown request.  The client will not automatically
     * be restarted.  A GUI operation would be needed to subsequently start the client.</p>
     *
     * <p>This generates a high-priority event.</p>
     */
    void clientStop();

    /**
     * <p>Ask the profile to restart the client.</p>
     *
     * <p>The client should expect to receive a shutdown request.  A new client will be instantiated
     * and have it's init client called only after the first has been shut down (excepting where
     * the first is hanging and times out).</p>
     *
     * <p>This generates a high-priority event.</p>
     */
    void clientRestart();
}
