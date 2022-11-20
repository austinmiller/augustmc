package aug.script.framework;

/**
 * <p>All clients must have a class with a parameterless constructor which implements this
 * interface.  When a client is started, the class is found by configuring the FQN of
 * the class and the classpath in the GUI.  It is instantiated using reflection and
 * then init is called.</p>
 *
 * <p><STRONG>The Client Classloader</STRONG></p>
 *
 * <p>All clients are started in a separate classloader.  Classes in this package,
 * subpackages, the java package, and the scala package are shared.  This means they
 * will be loaded by the application's classloader.  After the client's shutdown
 * call, all references to the classes loaded in the client's classloader are removed.
 * If the client removes all global references (such as those in threads), the client's
 * classloader, and all the classes and objects in it, should unload.</p>
 *
 * <p>To prevent classloader leaks, it's recommended that the client rigorously control
 * library references to it's own classes and to also shutdown any started threads.  If
 * the client keeps creating classloader leaks, the internet is profuse with guides for
 * debugging where the leak is coming from.</p>
 *
 * <p><STRONG>The Client Thread & The Event Thread</STRONG></p>
 *
 * <p>All profiles have an event thread which process client events, connection events,
 * server lines and fragments, etc.  The event thread uses a client thread to call into
 * the ClientInterface, even for the init call.  If the client thread takes too long to
 * respond, as configured in the profile, the event thread will stop waiting for the
 * response and call shutdown.  If shutdown takes too long, the event thread also will
 * stop waiting for the response.</p>
 *
 * <p>Since all calls into ClientInterface are on the client thread, it is effectively
 * a single threaded model.  If the thread is never returned, the shutdown call will
 * also never fire and this should result in a classloader leak (see above).  This is
 * considered a better result than letting the event thread hang indefinitely waiting
 * for the client thread to return.</p>
 *
 * <p>Since the event thread waits for the client thread, and the event thread is
 * responsible for text processing, the client thread should always try to return
 * reasonably quickly.  Clients should manage their own threads for handling long-lived
 * processes.</p>
 *
 * <p>Calls to profile that create events for the event thread will not be processed
 * right away.</p>
 *
 * <p>If the event thread decides to stop waiting for the client thread, it will interrupt
 * the client thread.  In Java, this means the thread may stop waiting on IO or otherwise
 * wake up if it's waiting on a lock.  It is up to the client to choose to respect
 * the interruption and return control of the client thread to the application.  It is not
 * recommended to take a long time on the client thread, so doing IO on the thread is
 * not ideal, unless it is very fast.</p>
 *
 * <p>While these methods are not re-entrant for the client, it is possible for a new client
 * to start before the old client finishes, if the old client is not returning control
 * fast enough.</p>
 *
 * <p><STRONG>Exception Handling</STRONG></p>
 *
 * <p>If an exception escapes init, the client will be shut down.  On all other calls
 * the exception is printed to the system log and nothing else happens.  If the client
 * wants to print an exception to the system log, it can do so using
 * {@link ProfileInterface#printException(Throwable)}.  Exceptions which propagate to
 * out of the client after the event thread has stopped waiting for the client thread
 * will be silently swallowed.</p>
 *
 * <p><STRONG>Initial Performance</STRONG></p>
 *
 * <p>Generally, clients load slowly and respond slowly as the application is first
 * booting or as the JIT is processing the bytecode for the first time.  It's not
 * unexpected for something that takes 100 ms to balloon to 2 seconds on the first
 * load.  Configuration of clients should keep this in mind.</p>
 */
@SuppressWarnings("unused")
public interface ClientInterface {
    /**
     * <p>Called exactly once, when the script is loaded. {@link ProfileInterface} can be used to
     * manipulate the profile and discover other supported interactions between the client
     * and the profile.  {@link ReloadData} is guaranteed to be not null and will contain any
     * data from returned from a previous invocation {@link ClientInterface#shutdown()} even if
     * the classpath changed.  This does not persist if the application is closed, so this
     * is not a good solution for persisting data.  The ideal use case is to persist
     * transient data across script reloads in a seamless way.</p>
     */
    void init(ProfileInterface profileInterface, ReloadData reloadData);

    /**
     * <p>Called exactly once when the java client is being shutdown.  The return value
     * will be passed to the .init of the next loading of the java client if that occurs
     * while the application is open and the profile hasn't been restarted.  This will not
     * persist across rebooting the application.</p>
     */
    ReloadData shutdown();

    /**
     * <p>Handle incoming line from the game.</p>
     *
     * <p>The line will not contain \n but it will contain all color codes as sent by the server.</p>
     *
     * <p>If return value is true, line will not be printed on the console, in which case it is
     * possible that lineNum will be reused.</p>
     *
     * <p>This line may have the last prompt the server sent prepended if the server does not send
     * newlines after prompts.</p>
     */
    boolean handleLine(LineEvent lineEvent);

    /**
     * <p>Handle a fragment from the server.  Fragments are text sent by the server when the application has read all
     * it can out of the OS buffer and has yet to see the server send a newline.  Empty lines will not be sent as
     * fragments.  Eventually, the fragment will be sent again as part of a handleLine, after the server sends a
     * newline.</p>
     *
     * <p>The most common reason for getting a fragment is that the server sent a prompt.  Some muds will not send a
     * new line between the prompt and the next line.  In which case, the fragment will be prepended to the next
     * real line sent to handleLine.</p>
     *
     * <p>The 2nd most common reason is that the server sent a lot of lines on a laggy network and the application
     * was unable to read the entire text out of the OS buffer, so some line is interrupted.</p>
     *
     * <p>It's possible to receive multiple fragments before a handleLine, in which case the subsequent fragments
     * will start with the previous fragment.</p>
     *
     * <p>Fragments, like lines, contain all the color codes.  It's possible the application has only read part
     * of a color code, in which case, the fragment would end on this partial color code.</p>
     *
     * <p>It is possible that a prompt which normally gets sent without a newline is instead sent only as a line if
     * the server sent newlines before the application could read the prompt.  In other words, it is not guaranteed
     * that a prompt without a newline will show up in a handleFragment call.  To catch all prompts, it is ideal to
     * also trigger prompts off of handleLine.  It is guaranteed that every prompt will be sent via handleLine as
     * eventually the server will send a newline.</p>
     */
    void handleFragment(LineEvent lineEvent);

    /**
     * <p>Handle a GMCP message sent by the server.  If GMCP was not enabled, this will never be
     * called.</p>
     */
    void handleGmcp(String gmcp);

    /**
     * <p>Handle user input sent from the command line interface.  This cmd may include \n but
     * generally will not.</p>
     *
     * <p>Return true to swallow the cmd and not send it to game, or return false to swallow
     * the command.</p>
     */
    boolean handleCommand(String cmd);

    /**
     * <p>This is called when the event handler is told that we connected to the server.  Since this
     * is all asynchronous, it is possible that it is no longer true by the time to client is
     * receiving this call.</p>
     *
     * <p>The id is a monotonically increasing number which is uniquely correlated with each telnet
     * attept.  The id cannot be the same on subsequent onConnects unless the application was restarted.</p>
     */
    void onConnect(long id, String url, int port);

    /**
     * <p>This is called when the event handler is told that a connection was terminated.  This could
     * be for many reasons, including that the user willfully shut down the connection, that the server
     * closed the connection, that the application timed out waiting for an ACK on the connection, etc.</p>
     *
     * <p>It's also possible that the disconnect was for an older connection and that the application is
     * still currently connected to the server.</p>
     *
     * <p>The id is a monotonically increasing number which is uniquely correlated with each telnet
     * attempt.  The id cannot be the same on subsequent onDisconnects unless the application was restarted.
     * If an onDisconnect is called with id == a and onConnect was previously called with id == b, where b>a,
     * then it's possible that the application is still connected to the server.</p>
     */
    void onDisconnect(long id);
}
