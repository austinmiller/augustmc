package aug.script.shared;

public interface ClientInterface {
    /**
     * <p>Called exactly once, when the script is loaded. profileInterface can be used to
     * manipulate the profile and discover other supported interactions between the client
     * and the profile.  reloadData is guaranteed to be not null and will contain any
     * data from returned from a previous invocation ClientInterface.shutdown even if
     * the classpath changed.  This does not persist if the mud client is closed, so this
     * is not a good solution for persisting data.  The ideal use case is to persist
     * transient data across script reloads.</p>
     */
    void init(ProfileInterface profileInterface, ReloadData reloadData);

    /**
     * <p>Called exactly once when the java client is being shutdown.  The return value
     * will be passed to the .init of the next loading of the java client if that occurs
     * while the mud client is open and the profile hasn't been restarted.  This will not
     * persist across rebooting the mud client.</p>
     * @return
     */
    ReloadData shutdown();

    /**
     * <p>Handle incoming line from the game.</p>
     *
     * <p>The line will not contain \n but it will contain all color codes as sent by the server.</p>
     *
     * <p>If return value is true, line will not be printed on the console, in which case it is
     * likely that lineNum will be reused.</p>
     *
     * <p>This line may have the last prompt the server sent prepended if the server does not send
     * newlines after prompts.</p>
     *
     * @param lineNum
     * @param line
     */
    boolean handleLine(long lineNum, String line);

    /**
     * <p>Handle a fragment from the server.  Fragments are text sent by the server when it didn't send a new
     * line.  Eventually, the fragment will be sent again as part of a handleLine.</p>
     *
     * <p>The most common reason for getting a fragment is that the server sent a prompt.  Some muds will not send a
     * new line between the prompt and the next line.  In which case, the fragment will be prepended to the next
     * real line sent to handleLine.</p>
     *
     * <p>It's possible to receive multiple fragments before a handleLine.</p>
     *
     * @param fragment
     */
    void handleFragment(String fragment);

    void handleGmcp(String gmcp);

    /**
     * <p>Handle user input sent from the command line interface.  This cmd may include \n but
     * generally will not.</p>
     *
     * <p>Return true to swallow the cmd and not send it to game, or return false to swallow
     * the command.</p>
     *
     * @param cmd
     * @return
     */
    boolean handleCommand(String cmd);

    void onConnect();
    void onDisconnect();
}
