package aug.script.shared;


import java.io.File;
import java.util.List;

public interface ProfileInterface {
    void send(String string);
    void sendSilently(String string);
    Boolean setWindowGraph(WindowReference windowReference);
    List<String> getWindowNames();
    TextWindowInterface createTextWindow(String name);
    TextWindowInterface getTextWindow(String name);
    File getConfigDir();
    void logText(Boolean log);
    void logColor(Boolean log);
}
