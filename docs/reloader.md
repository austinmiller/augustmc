## Reloader

### How it works

When the client is shutting down, the reloader can be invoked to convert classes
and objects into strings and save them in the ReloadData.  On a subsequent
client initialization, the reloader can be called with the ReloadData to
hydrate classes and objects from the saved strings.

The reloader can serialize/hydrate three kinds of fields.

* Java static fields
* Scala object/module fields
* Instance fields that implement Reloadable

In each case, the fields to be reloaded need to be annoted with @Reload.

### Serialization limitations.

The serialization is limited to primitives and some scala and java
collections.  To serialize other objects, a converter can be written
that converts an instance of a class to a string and from that
string representation back to the class.

### Examples

* [Java Example](https://github.com/austinmiller/augustmc-examples/blob/master/src/main/java/aug/client/JavaClient.java)
* [Java Reload Test](../framework/src/test/java/aug/script/framework/reload/JavaReloadSubject.java)
* [Scala Reload Tests](../framework/src/test/scala/aug/script/framework/reload/ReloaderTest.scala)
