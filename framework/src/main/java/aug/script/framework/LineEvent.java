package aug.script.framework;

import aug.script.framework.tools.ScalaUtils;

@SuppressWarnings("all")
public class LineEvent {
    public final String raw;
    public final String withoutColors;
    public final long lineNum;

    public LineEvent(long lineNum, String raw) {
        this.lineNum = lineNum;
        this.raw = raw;
        this.withoutColors = ScalaUtils.removeColors(raw);
    }

    @Override
    public String toString() {
        return "[" + lineNum + ", " + withoutColors + "]";
    }
}
