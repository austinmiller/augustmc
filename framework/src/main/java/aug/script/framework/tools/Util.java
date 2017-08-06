package aug.script.framework.tools;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class Util {

    /**
     * <p>Convert all objects to strings and then use a framing format to encode them into
     * string.  When paired with {@link Util#decode(String)}, the array of strings should
     * always exactly match.</p>
     */
    public static String encode(Object ... objects) {
        List<String> list = new ArrayList<>();
        for(Object object : objects) {
            list.add(object.toString());
        }
        return ScalaUtils.encodeArray(list.toArray(new String[0]));
    }

    /**
     * <p>Convert an encoded string to the original string of arrays.</p>
     */
    public static List<String> decode(String string) {
        return Arrays.asList(ScalaUtils.decodeArray(string));
    }

    /**
     * <p>Convert an exception's stack trace to a string.</p>
     */
    public static String stackTraceToString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }
}

