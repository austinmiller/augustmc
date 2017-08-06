package aug.script.examples.java;

import aug.script.framework.ClientInterface;
import aug.script.framework.ProfileInterface;
import aug.script.framework.ReloadData;

public class AbstractClient implements ClientInterface {

    protected static ProfileInterface profile;

    @Override
    public void init(ProfileInterface profileInterface, ReloadData reloadData) {
        profile = profileInterface;
    }

    @Override
    public ReloadData shutdown() {
        return null;
    }

    @Override
    public boolean handleLine(long lineNum, String line) {
        return false;
    }

    @Override
    public void handleFragment(String fragment) {

    }

    @Override
    public void handleGmcp(String gmcp) {

    }

    @Override
    public boolean handleCommand(String cmd) {
        return false;
    }

    @Override
    public void onConnect(long id, String url, int port) {

    }

    @Override
    public void onDisconnect(long id) {

    }
}
