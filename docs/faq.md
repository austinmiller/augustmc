## GENERAL FAQ

### How do I delete a profile?

Find the config directory for the application under "profile->config dir"
menu option.  Inside this directory is a subdirectory for "profiles".
While the application is not running, delete the directory for the profile
you don't want to have anymore.

### How do I rename a profile?

Find the config directory for the application under "profile->config dir"
menu option.  Inside this directory is a subdirectory for "profiles".
While the application is not running, rename the directory for the profile
and inside "profileConfig.xml" change the name attribute to the new name.

### What is GMCP?

It is a mud protocol used by some popular muds to send and receive
structured data.  If your mud doesn't support it, you can safely
ignore it.

### What is MCCP?

It is a compression protocol that saves bandwidth between the server
and the client.  MCCP is automatically negotiated and supported by
the client.

### Why does AugustMC not support a popular telnet protocol?

Most likely because we don't know about it.  File an issue!

### My question isn't in this faq. / I found a bug.

File an issue or ask in our gitter.

[![Join the chat at https://gitter.im/augustmc/Lobby](https://badges.gitter.im/augustmc/Lobby.svg)](https://gitter.im/augustmc/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## CLIENT DEVELOPMENT FAQ

### How do I capture text from the MUD in a separate window?

Create a new TextWindow and then display it using a window graph.  Save
a reference to this window, and when you have a line you want to save
you can echo it to the text window.

[See this example](../examples/src/main/java/aug/script/examples/java/WindowClient.java)

### How do I save state across client reloads?

At first it seems like a disadvantage to lose all state during a client
reload, however it is a powerful feature for atomizing changes to client
code.  Yet, we still want to be able to pickup where we left off.

The AugustMC Framework has a powerful tool for this feature called the
reloader.  See [reloader](reloader.md) for more information on how
to use it.

The ClientInterface also allows the client to add data to ReloadData
object which contains a map where the keys and values are both strings.
The client can instantiate ReloadData on shutdown, fill it with data,
and return it.  The next client will get this reload data passed in
as part of the init call.  In general, the [reloader](reloader.md) is
more powerful in that it allows the client to skip concerns about
serialization.

### What is the scheduler and why do I want to use it?

The [scheduler](scheduler.md) is a tool the framework provides to allow
scheduling future callbacks into the client in the future.  For instance,
if the client wants to periodically ask the mud to "who", the
[scheduler](scheduler.md) can be used to regularly invoke this request.

The [scheduler](scheduler.md) also provides the ability to serialize callbacks so that
they still run after a client reload.  Another major feature of the
[scheduler](scheduler) is that all callbacks happen on the client thread, so that
thread safety concerns can be minimized or even ignored. 

### How do I capture an ASCII map and draw it in a window?

Create a [window graph](../examples/src/main/java/aug/script/examples/java/WindowClient.java)
that holds a new text window.  Save the reference to this text window.

After capturing all the lines of the map, use `TextWindowiInterface.setLines(...)`
to set the contents of the TextWindowInterface.  This will only call redraw at
the end of processing the lines.  If the lines are always the same amount, it is not
necessary to clear the TextWindowInterface, since they are being overdrawn, otherwise
clear the text window before setting the lines.

If you don't want the lines of the map to show in the console, return true out of
`ClientInterface.handleLine` when the line is a map line. 

### How do I trigger off of color?

The `handleLine(LineEvent)` call sends a LineEvent object which has the raw line
as a member.  You can match against this raw line.  To help with matching color,
the AugustMC Framework has a `Utils.matchColor` method.

### Why does the initial client load take so long?

It's quite common for the script loader to take a few seconds on the first load
of a client.  It seems this is partly due to Java warming up classes and reflection
caches.  It may also be loading the classpath files for the first time.

In any case, subsequent reloads tend to be pretty snappy: less than 100 ms for a
rather advanced Scala client.

### Why did my client stop responding after reload?

Most likely, AugustMC was unable to load the class from the classpath
and this error should be visible from the system tab.

### Why is my client timing out?

Clients are advised not to timeout the client thread.  On a modern desktop or
laptop, it should be no problem to process thousands of triggers against each
incoming line.  However, IO can timeout itself and be devilishly slow, so it
is advised to do any IO, especially non-localhost io, on separate threads.

### What is a fragment and why do I want to handle it?

When AugustMC receives a partial line from the server without getting a
new line, the partial line is considered a fragment.  The most common
reason for getting a fragment is that the server sent a prompt. So,
AugustMC sends this fragment to the client which can use a prompt trigger
to respond.

Every fragment will eventually get a newline and be sent again, along
with any subsequently received text, to the client via `handleLine`.

Fragments also happen in the middle of large blocks of text because
of internet lag or socket buffers running out of space and returning
partial text.
