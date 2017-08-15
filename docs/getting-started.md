## GETTING STARTED

### SYSTEM TAB

The system tab is always tab 0 and contains logging information for the entire
app.  For instance, if exceptions occur on a profile, they will be printed in
the system tab.  It also contains a log of connections, disconnections, and
other essential events.

### CREATE A PROFILE

Profiles describe what server to connect to and with what options.  To create
a profile, open the preferences dialog, navigate to "profiles", enter a name
for a profile, and click "create profile".  The new profile will save if 
"apply" or "ok" is clicked.

The profile should be visible in the preferences dialog.  Opening this up and
configuring the server and host is required to be able to connect.  It is a good
idea to take a moment and survey the other settings options.

Save the settings.

### OPEN A PROFILE AND CONNECT

Using "Profile->open profile", find the profile in the dropdown and click "open".
A new tab should open with the profile name.  With this open, 
"connections->connect" should open a connection to the configured host and port.

### SCRIPTING AND CLIENTS

AugustMC is designed to be scripted with a JVM language.  The general philosophy
is not to build expensive features into the profile, but rather to build a
powerful set of tools into the AugustMC Framework.  This allows the user to
rapidly build a powerful client that controls the AugustMC application.  More
is configurable from the framework than from the preferences dialog, so even
though AugustMC should look a little spare at this point, the framework will give
the user complete control.

#### FOR MORE INFO

* [Clients](clients.md)
