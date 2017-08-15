## Scheduler

The scheduler is a convenient framework object which clients can use to schedule callbacks to
be executed in the future or periodically.  The scheduler specifically uses the client thread
to execute the callback in order to simplify the threading model.

The scheduler is acquired by asking the profile for the scheduler.  This call is repeatable
despite taking an array of RunnableReloaders.  The parameter can be empty.

### RunnableReloaders -- Serializing Runnables

It's frustrating to keep runnables scheduled across client reschedules, so to this
end the AugustMC Framework allows the user to install a set of [RunnableReloaders](../framework/src/main/java/aug/script/framework/RunnableReloader.java)
when calling `getScheduler` for the first time the client has been loaded.  This first
call will reload any runnables serialized from the last client.

To serialize a Runnable, at a minimum it must not be anonymous or a lambda and it
must have a parameterless constructor.  There must also be a RunnableReloader
parameterized by the Runnable implementation to serialize.  On the first call
the client should pass all the RunnableReloaders when calling `getScheduler`.  This
list will be used later to save any extant runnables that have yet to run.

Periodic runnables scheduled with `Scheduler.every()` cannot be serialized.

### Links

* [scheduler example](../examples/src/main/java/aug/script/examples/java/SchedulingClient.java)
* [scheduler interface](../framework/src/main/java/aug/script/framework/SchedulerInterface.java)

