package aug.script.shared;

public class SplitWindow extends WindowReference {
    private final WindowReference topLeft;
    private final WindowReference botRight;
    private final float dividerLocation;
    private final boolean horizontal;

    public SplitWindow(WindowReference topLeft, WindowReference botRight, boolean horizontal) {
        this(topLeft, botRight, horizontal, 0.5f);
    }

    public SplitWindow(WindowReference topLeft, WindowReference botRight, boolean horizontal, float dividerLocation) {
        super();

        if(topLeft == null || botRight == null) throw new RuntimeException("neither window reference can be null.");
        if(dividerLocation <= 0 || dividerLocation >= 1)
            throw new RuntimeException("divider location must be between 0 and 1");

        this.topLeft = topLeft;
        this.botRight = botRight;
        this.dividerLocation = dividerLocation;
        this.horizontal = horizontal;
    }

    public WindowReference getTopLeft() {
        return topLeft;
    }

    public WindowReference getBotRight() {
        return botRight;
    }

    public float getDividerLocation() {
        return dividerLocation;
    }

    public boolean isHorizontal() {
        return horizontal;
    }
}