package aug.script.examples.java;

import aug.script.framework.*;

/**
 * <p>This abstract class demonstrates creating a window graph.  This is useful for
 * capturing communication and for the client to print to the screen.</p>
 */
@SuppressWarnings("all")
public class WindowClient extends AbstractClient {

    protected static TextWindowInterface com;
    protected static TextWindowInterface console;
    protected static TextWindowInterface metric;

    @Override
    public void init(ProfileInterface profileInterface, ReloadData reloadData) {
        super.init(profileInterface, reloadData);

        com = profile.createTextWindow("com");
        console = profile.getTextWindow("console");
        metric = profile.createTextWindow("metric");

        SplitWindow graph = new SplitWindow(
                new WindowReference("console"),
                new SplitWindow(
                        new WindowReference("com"),
                        new WindowReference("metric"),
                        false, 0.8f
                ),
                true);
        profile.setWindowGraph(graph);
    }
}
