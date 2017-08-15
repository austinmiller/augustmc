## CONTRIBUTING

[![Join the chat at https://gitter.im/augustmc/Lobby](https://badges.gitter.im/augustmc/Lobby.svg)](https://gitter.im/augustmc/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Help wanted!

### DOC WRITING

Anyone who wants to contribute docs or examples is highly welcome!

### ISSUES / BUG REPORTING

Thinking of new features and reporting bugs is hard work.  We value anyone
who takes the time.  Please feel encouraged to submit new issues for bugs
or for new features.

### CODING

If you'd like to contribute, obviously the source code is open and you can just
download it and hack on it.  It would be lovely if you join our gitter.  No question
is too silly and we'd be happy to ramp you up.

If you're going to work on an issue, please comment on that issue that you're
working on it.

#### IntelliJ

The macro subproject makes it pretty much mandatory to use Intellij over Eclipse as
if you use the latter you'll have to live with lots of red squiggly lines.  The
Scala and SBT integration is excellent.

Importing into Intellij with the SBT/Scala plugin is a snap using
"project->from existing sources".  You almost certainly want to use the EAP
version of the Scala plugin.

#### Code Style

Outside of the framework and examples subprojects, all code needs to be in Scala
and ideally written in a Scala idiomatic style.  There are places where this
is relaxed, such as the preferences dialog code, anything heavily involving
Swing, or anything doing byte-level work.  If you're confused about how to
write something, ask in gitter or otherwise be willing to refactor after a
code review.

Here are some examples of Scala idiomatic conventions ...

* Prefer tail-recursion over iteration
* Prefer vals over vars
* Prefer immutable collections over mutable

The framework currently has two sections, a Java section and a Scala section.
Since we want the framework to grow in mud-library specific features over
time, experienced Java programmers could find a lot of work to do here.

It would also be nice if enthusiasts of other languages wrote framework
tools for that language in the project and figured out how to compile it
using sbt.

Examples are also only Java/Scala at the moment  and we could always use more.

Please do not issue pull requests with any new IntelliJ warnings or unused
imports.

#### Pull Requests

After you've written something, please issue a pull request against master
in the github repo.  If you'd like early feedback, create a branch in your
repo and tell us in gitter about it.  We'll pull it down and take a look.
