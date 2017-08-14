package aug.script.framework.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class Util {

    /**
     * <p>Return a string that can be used in a regex to match a telnet color code.  This is
     * still sensitive to the order of the colors that the server has sent.</p>
     */
    public static String matchColor(int ... codes) {
        StringBuilder s = new StringBuilder();

        s.append("" + (byte) 27);
        s.append("[");

        for (int code : codes) {
            if (s.length() > 2) {
                s.append(";");
            }
            s.append(code);

        }

        s.append("m");

        return Pattern.quote(s.toString());
    }


    /**
     * <p>Remove escaped color sequences from string.</p>
     */
    public static String removeColors(String input) {
        return ScalaUtils.removeColors(input);
    }

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
        return ScalaUtils.toString(throwable);
    }
}

