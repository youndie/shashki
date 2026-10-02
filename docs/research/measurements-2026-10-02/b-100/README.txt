B-100 raw evidence: the Kotlin daemon's heap on the hosted runner. The report is
docs/backlog/B-100-the-kotlin-daemon-runs-out-of-heap-on-the-shared-ui-test-link.md.

One directory per gradle job: <run id>-<event>-<attempt>[-arm]. The run id is the GitHub Actions run
of .github/workflows/check.yaml on branch build/b-100-kotlin-daemon-heap.

  -warm   the first probe; main's build cache supplied every link, so nothing here fails
  (none)  cold build (--no-build-cache), the daemon at today's inherited 3g
  -5g     cold build, -Pkotlin.daemon.jvmargs=-Xmx5g, a forced full collection every 6 s
  -verify cold build, kotlin.daemon.jvmargs=-Xmx5g from gradle.properties, forced collections

Files:
  host.txt                 nproc, free -m, /proc/meminfo, the cgroup limit (empty: none)
  mem.log                  every 2 s: MemAvailable, then resident MB per JVM as role:pid=MB, Chrome
  kotlin-daemon-<pid>.args the daemon's command line as ps showed it, classpath jars dropped
  gradle-daemon-<pid>.args the same for the Gradle daemon
  <step>-kotlin-daemon-<pid>.log  that daemon's GC log (-Xlog:gc,gc+init:time,uptime,pid); <step>:
                           gc = the check build, gc-isolated = the shared-ui link alone (first unit),
                           gc-iso-<module> = that link alone, gc-control = the positive control
  gc-gradle-daemon-<pid>.log      the Gradle daemon's GC log during the check build
  kotlin-daemon-events.log the daemons' own logs, cut to: JVM and daemon arguments (the run-files
                           directory is in the latter), each compilation's start with its module,
                           each result, OutOfMemoryError, shutdowns
  gradle-outcome.log       from the job log: each Gradle invocation, its result, the failing task,
                           and the probe's own B-100 lines

The live set is the heap after a full collection ("Pause Full", the number after "->"). Young
pauses ("Pause Young") include dead old-generation objects under the Parallel collector and are not
the live set; amendment 1 of the item is about exactly that.
