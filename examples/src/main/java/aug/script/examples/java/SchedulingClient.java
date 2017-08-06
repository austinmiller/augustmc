package aug.script.examples.java;

import aug.script.framework.*;
import aug.script.framework.tools.Util;

import java.util.List;

/**
 * <p>This example demonstrates the framework's scheduling capabilities.</p>
 *
 * <p>If using the framework's scheduler, it will always use the client thread to
 * call the runnable.  If the client does not setup any of its own threads, thread
 * safety is not a concern.  It is safe to call the scheduler from a thread that the
 * client started, however the runnable will still be executed on the client thread.</p>
 *
 * <p>This example also demonstrates the framework's ability to serialize the runnables
 * so that they can survive reload.  This feature requires that the runnable be serializable
 * to string and back and that it have it's own class, i.e. it cannot be anonymous. To
 * handle serialization, an instance of {@link RunnableReloader} parameterized by the
 * runnable's implementation class, must be passed to the profile when asking for the
 * scheduler.</p>
 *
 * <p>In this example, we are able to serialize EchoRunnable using EchoRunnableReloader
 * by converting the entire runnable to a string and back.  Before asking the profile
 * to give us a reference to the scheduler, we construct an array that contains a
 * copy of EchoRunnableReloader which we pass to the scheduler.  This array is used
 * to hydrate previously saved Runnables and to later save the runnables when the client
 * is shutdown.</p>
 *
 * <p>It is also entirely reasonable to use the scheduler without being concerned
 * about serializing runnables.  In this situation, reloading the script effectively
 * cancels all scheduled runnables.</p>
 */
@SuppressWarnings("all")
public class SchedulingClient extends WindowClient {

    private SchedulerInterface scheduler;

    private int everyCount = 5;
    private FutureEvent every;

    public static class EchoRunnable implements Runnable {

        final long created;
        final String msg;

        public EchoRunnable(String msg) { this(msg, System.currentTimeMillis()); }
        public EchoRunnable(String msg, long created) {
            this.msg = msg;
            this.created = created;
        }

        @Override
        public void run() {
            metric.echo(String.format("msg: %s, created: %d, time: %d", msg, created,
                    System.currentTimeMillis()));
        }
    }

    /**
     * We will use this to serialize an EchoRunnable if we are reloaded.
     */
    class EchoRunnableReloader extends RunnableReloader<EchoRunnable> {

        // This is necessary because of type erasure.
        @Override
        public Class<EchoRunnable> runnableType() {
            return EchoRunnable.class;
        }

        @Override
        public String runnableToString(EchoRunnable er) {
            // Util encoder relies on toString and will convert the objects into a
            // string format that does not rely on delimeters.
            return Util.encode(er.msg, er.created);
        }

        @Override
        public EchoRunnable stringToRunnable(String string) {
            // Util.decode will give us the original list of strings in the exact order
            // we laid out the objects in the call to Util.encode().
            List<String> list = Util.decode(string);

            // We know the 2nd string is a long because we passed in a long as the
            // 2nd argument to Util.encode().  So, here, we convert it to a long.
            return new EchoRunnable(list.get(0), new Long(list.get(1)));
        }
    }

    private RunnableReloader<?> []reloaders = {
      new EchoRunnableReloader()
    };

    @Override
    public void init(ProfileInterface profileInterface, ReloadData reloadData) {
        long ms = System.currentTimeMillis();
        super.init(profileInterface, reloadData);

        // if this is not our first time being loaded, any saved EchoRunnables
        // should get scheduled
        scheduler = profile.getScheduler(reloaders);

        // every events cannot be serialized, so we use a nice lambda
        every = scheduler.every(2000, 1000, () -> {
            metric.echo("a second has passed");
            if(--everyCount == 0) {
                every.cancel();
            }
        });

        metric.echo("script loaded in " + (System.currentTimeMillis() - ms) + " ms");
    }

    @Override
    public boolean handleCommand(String cmd) {
        // Even though we don't call reload, if we reload before this
        // runnable fires, it should still be scheduled.
        if (cmd.equals("run")) {
            scheduler.in(3000, new EchoRunnable("run"));
            metric.echo("scheduled run");
            return true;
        }

        // With this alias, we're scheduling an EchoRunnable and then forcing a
        // client restart to demonstrate that it will still run.
        // This is a really fun command to spam, as the scheduled events stack up.
        if (cmd.equals("reload")) {
            scheduler.in(3000, new EchoRunnable("reload"));
            metric.echo("scheduled reload");
            profile.clientRestart();
            return true;
        }

        return false;
    }
}
