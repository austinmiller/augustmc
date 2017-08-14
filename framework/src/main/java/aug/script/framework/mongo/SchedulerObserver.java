package aug.script.framework.mongo;

import aug.script.framework.SchedulerInterface;
import org.mongodb.scala.Observer;
import org.mongodb.scala.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>Create mongo observer which will use the scheduler to do processing on the client
 * thread.  This can help reduce thread safety issues as well as automatically cancel
 * processing if the client is reloaded.</p>
 *
 * <p>This instance should not be used to observe multiple requests at the same time.
 * If it's reused to observe again, the list must be defensively copied inside the results
 * processor or not referenced after the first observation.</p>
 */
public class SchedulerObserver<T> implements Observer<T> {

    private final Consumer<List<T>> resultProcessor;
    private final Consumer<Throwable> errorProcessor;
    private final List<T> results = new ArrayList<>();
    private final SchedulerInterface scheduler;

    public SchedulerObserver(SchedulerInterface scheduler,
                             Consumer<List<T>> resultProcessor,
                             Consumer<Throwable> errorProcessor) {
        this.scheduler = scheduler;
        this.resultProcessor = resultProcessor;
        this.errorProcessor = errorProcessor;
    }

    @Override
    public final void onNext(T result) {
        results.add(result);
    }

    @Override
    public final void onError(Throwable e) {
        scheduler.in(0, () -> {
           errorProcessor.accept(e);
        });
    }

    @Override
    public final void onComplete() {
        scheduler.in(0, () -> {
            resultProcessor.accept(results);
        });
    }

    @Override
    public final void onSubscribe(Subscription subscription) {
        results.clear();
        subscription.request(Long.MAX_VALUE);
    }
}
