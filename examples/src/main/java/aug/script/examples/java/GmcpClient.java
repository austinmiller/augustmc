package aug.script.examples.java;

@SuppressWarnings("unused")
public class GmcpClient extends WindowClient {

    @Override
    public void handleGmcp(String gmcp) {
        super.handleGmcp(gmcp);

        com.echo(gmcp);
    }
}
