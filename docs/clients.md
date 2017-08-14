## CLIENTS

AugustMC tries to be more of a broker and framework than a telnet client.  It
tries to solve the generic GUI problems and basic text handling problems while
turning over the real decision making to the user.  From this perspective,
the user is expected to provide a "client" that is written in a JVM-based language
like Java, Scala, Clojure, or Kotlin.

A typical work flow is to have an IDE open with a project written in one of
these languages that uses the AugustMC Framework to interface with AugustMC.
After compiling the client, the script can be loaded (or reloaded) from within
AugustMC.  The entire Java ecosystem is thus available to the user as well as
any other language binaries that are built off of the JVM.

It's also possible to write a client that provides all the typical features of
a all-in-one mud client, including editor guis, triggers, aliases, auto reconnect,
etc.  This client could be distributed as a jar and configured to be used by
the user's profile.  In this way, users who write code can provide JARs to users
who don't write code but may be familiar with concepts like tintin++ triggers.

### LANGUAGE RESTRICTIONS

Clients must be using Java 8 to compile.  It is recommended to always use the
latest version of the Java 8 Oracle JDK to compile.

If the client is written in Scala, users must use the same version of Scala that
 AugustMC is written in (2.12.x at the time of this
writing).  Other versions of Scala are not binary compatible.

### CLIENT CLASSLOADER

The client is loaded in a client classloader which jails off client-specific
classes.  This is a simpler version of how J2EE webapps are loaded.  Classes
whose packages begin with "java", "scala", "aug.script.framework", and
"org.mongodb" are loaded in the Application classloader.  All other classes are
loaded in the client classloader.

On restart, the client gets a whole new classloader.  Thus, clients have clean
versions of their compiled classes to reference, as well as the ability to
update the versions of their dependencies.  It also means if the application
has multiple profiles open, that each client for each profile does not conflict
with what versions of the classes they have open, if they happen to use
conflicting classes.

### FRAMEWORK

In order to develop a client, the client project will need the framework JAR
that matches the version of AugustMC.  This should be easy to find via the
releases page.  The framework classes are generally well documented, so it is
advisable to also download the framework-sources jar and attach these sources
to the IDE.  In this way, the user can inspect the source code and read the
javadocs while developing the client.

The framework contains Java classes for everything so that all JVM languages
are supported.  However, there are enhanced versions of some features in Scala
that are more desirable for Scala users to use.

<aside class="notice">
The classes in the framework jar are never loaded by AugustMC.  The client
uses them to compile.  The most common reason for failure to load the client
is that the framework jar version does not match AugustMC's version.
</aside>

### CLIENT INTERFACE

Minimally, the client needs to implement a ClientInterface with a class that
has a parameterless constructor.  This class needs to be instatiated by
AugustMC and represents the entry point into the Client.  See the thorough
Java Docs for this class to get a detailed understanding of the thread model
and order of calls.

Of specific interest is the client timeout issue.  Read the ClientInterface
docs for a thorough explanation.

### PROFILE INTERFACE

AugustMC will call the ClientInterface implementation the user provides after
starting the client and give the client a ProfileInterface which contains
many powerful functions for manipulating the UI.  It also has thorough java
docs which should be considered the canonical reference.

### UPGRADING AUGUSTMC VERSIONS

AugustMC is willing, but reluctant, to break framework interfaces in new
versions.  Language versions may be similarly revved up to JDK 9 or Scala 2.13.x
in the future.  Where possible, breaking changes should be straightforward to
incorporate in the client.  Clients compiled for one version of AugustMC should
only be run on that version.

### CONFIGURING AUGUSTMC

AugustMC needs to be configured to load the compiled client using the Java tab
of the preferences dialog.  

#### Mode

The mode must be set to either ENABLED or AUTOSTART.
With AUTOSTART, the client will be started when the profile is opened and
restarted if the client times out.  ENABLED allows the user to manually start
the client.  In most circumstances, AUTOSTART is advised.

#### Main Class

The main class must be set to the fully qualified name of the class that
implements ClientInterface.  If the ClientInterface looks like ...

```java
package mymudclient.subpackage;

import aug.script.framework.ClientInterface;

public class MudClient implements ClientInterface {
    // ...
}
```

Given the above, main class would be set to "mymudclient.subpackage.MudClient".

#### Classpath

There are several ways to approach providing the compiled classes to AugustMC.

The IDE can be set to compile automatically and the build directory can be
incldued in the classpath.  The advantage here is fast reloads while
continuously updating the app.

The project can also build a "one-jar" which contains all the compiled classes
and the dependencies.  This simplifies configuration since only one jar is
added to the classpath.

Directories look for .class files in the similar fashion to configuring the
cp parameter when invoking the jvm.  Files are expected to be jars.

### FOR MORE INFO

See the various getting started documents to get started building a client.

Also see the document for framework features, which solve common problems.