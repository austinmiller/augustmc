package aug.script.shared;

public interface ClientInterface {
    void init(ProfileInterface profileInterface);
    void shutdown();

    void handleLine(String line);
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
