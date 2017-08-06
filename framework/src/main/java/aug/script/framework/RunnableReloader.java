package aug.script.framework;

@SuppressWarnings("all")
public abstract class RunnableReloader<T extends Runnable> {
    public abstract Class<T> runnableType();
    public abstract String runnableToString(T runnable);
    public abstract Runnable stringToRunnable(String string);

    public final String convertToString(Runnable o) {
        return runnableToString((T) o);
    }
}
