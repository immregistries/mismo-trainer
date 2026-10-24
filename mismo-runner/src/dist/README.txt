MISMO RUNNER
============

Mismo Runner optimizes Mismo patient-matching configurations on this
computer, using an assignment set up for it in Mismo Trainer, and reports
its progress and best results back to Mismo Trainer.

Nothing needs to be installed. Java is included in this folder.


GETTING STARTED
---------------

1. Unzip this folder anywhere you like, for example your Desktop or
   Documents. You don't need administrator rights.

2. In Mismo Trainer, open Optimization > Runners, create a runner for this
   computer, and note the pairing code it shows. The code is valid for
   15 minutes.

3. Double-click "Mismo Runner.exe". A console window opens.
   The program isn't code-signed yet, so Windows may show "Windows
   protected your PC". Choose "More info", then "Run anyway".

4. Type:  pair
   Enter the Mismo Trainer address shown with the pairing code
   (for example https://informatics.immregistries.org/mismo) and the code.
   You only need to do this once.

5. In Mismo Trainer, set the runner's assignment: the starting
   configuration, the Test Set, the population size, and the scoring profile.

6. In the runner, type:  start
   It downloads the assignment, checks it, shows you a summary, and asks
   before it begins.


WHILE IT RUNS
-------------

  (Enter)   Show the status again
  sync      Send progress and the best result to Mismo Trainer now
  weights   Show the scoring weights for this run
  script    Show the current best configuration
  stop      Stop this run and go back to the idle prompt
  exit      Stop this run and close the runner
  help      List the commands

Progress is sent about once a minute, and the best result whenever it
improves (at most every five minutes). If the connection drops, the run
keeps going and reporting resumes when it comes back.

Changing the assignment in Mismo Trainer never changes a run that is
already going. The change is used the next time you type 'start'.

If the computer restarts or the runner is closed suddenly, start it again.
It will offer to send the best result it had saved.


WHERE THINGS ARE KEPT
---------------------

The runner keeps its identity, downloads, and logs in:

    %LOCALAPPDATA%\Mismo Runner

The identity.json file there lets this computer connect to Mismo Trainer.
Treat it like a password and don't share it. If this computer is lost or
retired, revoke its credential on the runner's page in Mismo Trainer.

If something goes wrong, the log files in the "logs" folder there are the
most useful thing to send along with a description of what happened.


UPDATING
--------

When a new version is released, download and unzip it, and use the new
"Mismo Runner.exe". Your pairing is kept, so you don't need to pair again.
If Mismo Trainer says the runner is too old, it will tell you to do this.
