package aug.script.framework;

public interface FutureEvent {
    /**
     * <p>Cancel the event, unless it is not possible.  Returns true iff the
     * event was cancelled.</p>
     */
    Boolean cancel();
}
