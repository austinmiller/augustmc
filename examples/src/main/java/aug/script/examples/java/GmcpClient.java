package aug.script.examples.java;

@SuppressWarnings("unused")
public class GmcpClient extends WindowClient {

    @Override
    public void handleGmcp(String gmcp) {
        super.handleGmcp(gmcp);

        com.echo(gmcp);
    }

    @Override
    public boolean handleCommand(String command) {

        if (command.equals("#help")) {
            console.echo("Help! I need somebody.");
            return true;
        }

        return false;
    }
}
