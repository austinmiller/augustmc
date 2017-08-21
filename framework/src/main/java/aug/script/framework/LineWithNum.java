package aug.script.framework;

@SuppressWarnings("all")
public class LineWithNum {

    public final long lineNum;
    public final String line;

    public LineWithNum(long lineNum, String line) {
        if (line == null) throw new RuntimeException("line cannot be null");
        if (lineNum < 0 ) throw new RuntimeException("line num cannot be negative");
        this.lineNum = lineNum;
        this.line = line;
    }
}
