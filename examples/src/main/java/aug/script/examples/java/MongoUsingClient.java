package aug.script.examples.java;


import aug.script.framework.ProfileInterface;
import aug.script.framework.ReloadData;
import aug.script.framework.RunnableReloader;
import aug.script.framework.SchedulerInterface;
import aug.script.framework.mongo.SchedulerObserver;
import aug.script.framework.tools.Util;
import org.mongodb.scala.MongoClient;
import org.mongodb.scala.MongoDatabase;
import org.mongodb.scala.Observer;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Example using Mongo.  It is necessary to setup mongo tab.  It can take some
 * time before initDB is called on the first load.  After everything goes off, try
 * reloading to see how long it takes.</p>
 *
 * <p>MongoDB is supported on all platforms and highly flexible.</p>
 *
 * <p>Once requests are made of the client/db, they will complete even under script
 * reload.  It's a good idea on observers to check if the script has been closed.  To
 * prevent thread safety, it's quite nice to combine processing results with the scheduler
 * as this demonstrates.  That way, if the client has been closed, the processing won't
 * occur and the processing will always take place on the client thread.</p>
 *
 * <p>Since this is a powerful idiom, it has been encapsulated in a {@link SchedulerObserver}
 * which is also shown below.</p>
 */
@SuppressWarnings("unused")
public class MongoUsingClient extends WindowClient {

    private SchedulerInterface scheduler;

    @Override
    public void init(ProfileInterface profileInterface, ReloadData reloadData) {
        super.init(profileInterface, reloadData);
        scheduler = profileInterface.getScheduler(new RunnableReloader[0]);
    }

    /**
     * This is passing in the authorizing database in addition to the client.  Use the client to
     * reference other databases.
     */
    @Override
    public void initDB(MongoClient mongoClient, MongoDatabase mongoDatabase) {

        // Note that this Observer will be called back on another thread.
        // In general, it is highly adviseable to use async like this.  This
        // requires clients using Mongo to think about thread safety.
        mongoDatabase.listCollectionNames().subscribe(new Observer<String>() {
            private List<String> collections = new ArrayList<>();

            final long ms = System.currentTimeMillis();

            @Override
            public void onError(Throwable e) {
                metric.echo("error " + Util.stackTraceToString(e));
            }

            @Override
            public void onComplete() {
                scheduler.in(0, () -> {
                    for (String collection: collections) {
                        metric.echo("collection: " + collection);
                    }
                    metric.echo("completed after " + (System.currentTimeMillis() - ms));
                });
            }

            @Override
            public void onNext(String result) {
                collections.add(result);
            }
        });

        /**
         * This accomplishes the same as above using {@link SchedulerObserver} which is a convenience
         * object the aug framework provides to do the same as above.  It uses the scheduler to
         * force the processing to occur on the client thread, and it uses lambdas to ultimately
         * handle the processing or error states.
         */
        mongoDatabase.listCollectionNames().subscribe(new SchedulerObserver<>(scheduler,
                list -> list.forEach(name -> com.echo("collection: " + name)),
                throwable -> com.echo("error: " + Util.stackTraceToString(throwable))));
    }
}
