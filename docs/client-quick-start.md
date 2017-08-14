## Client Quick Start

See [releases](https://github.com/austinmiller/augustmc/releases) 
to find the framework jars that match the AugustMC version.

### INTELLIJ JAVA SETUP

Maven is a nice approach here.  See the 
[Java example](https://github.com/austinmiller/augustmc-examples).

#### Framework

The framework jar is referenced by the pom.xml file in a specific way so the
jar must be placed relative to the root project directory and named exactly
the same.  It's also possible manually install the jar to the local repo
along with the sources jar and then to pull it in like any other maven jar.

#### Importing into IntelliJ

* File->new>project from existing sources...
* Select the project root directory
* Make sure maven is selected

### INTELLIJ SCALA CLIENT SETUP

The recommended approach is to use a simple sbt project.

Create a new directory to hold the project.

#### build.sbt

In the project root directory, add a file 'build.sbt' that
contains this text.

```sbt
name := "myclient"
version := "1.0"
scalaVersion := "2.12.3"
```

#### Framework

Put the framework and framework-sources jars under /lib in
the project's root directory.  This will get automatically
picked up by sbt and IntelliJ's sbt plugin.

#### IntelliJ

In IntelliJ ...

* install the sbt plugin (restart if necessary)
* File->new->project from existing sources...
* Select the project root directory
* Make sure to select sbt project
* configure as desired

#### ClientInterface

Create a class that extends ClientInterface and implement
all the methods.

#### Hot Tips

* turn on sbt auto-import
* turn on IntelliJ's auto-build for quick script reloads

### USING THE FRAMEWORK

The framework has powerful features.  Some of them are demonstrated in the
[Java example](https://github.com/austinmiller/augustmc-examples) however
more examples exist in the [AugustMC repo](https://github.com/austinmiller/augustmc/tree/master/examples/src/main).
