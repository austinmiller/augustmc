package aug.script.framework;

/**
 * <p>A scheduler that guarantees execution of the runnables on the client thread.</p>
 *
 * <p>Also supports serializing runnables across reboots, see {@link RunnableReloader}.</p>
 */
@SuppressWarnings("unused")
public interface SchedulerInterface {
    /**
     * <p>Run the runnable after the timeout.  Timeout is based in milliseconds.</p>
     */
    FutureEvent in(long timeout, Runnable runnable);

    /**
     * <p>Run the runnable every period, starting after initialDelay.  These
     * runnables are not serializable.</p>
     */
    FutureEvent every(long initialDelay, long period, Runnable runnable);
}
