package aug.script.shared;

public interface ClientInterface {
    void init(ProfileInterface profileInterface);
    void shutdown();

    void handleLine(String line);
    void handleFragment(String fragment);
    void handleGmcp(String gmcp);
    void handleCommand(String cmd);

    void onConnect();
    void onDisconnect();
}
