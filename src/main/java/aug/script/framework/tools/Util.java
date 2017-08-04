package aug.script.framework.tools;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Util {
    public static String stackTraceToString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }
}
