// NexaFlow elevated shell channel.
//
// Implemented by UserShellService. Shizuku runs that service's process as a
// child of its own server process (uid 0 on rooted devices, shell uid over
// wireless debugging), so every method here executes with elevated privileges.
//
// This AIDL contract replaces the legacy Shizuku.newProcess() API, which is
// private since Shizuku API 13.1.5 and removed in API 14.
package com.nexaflow.core.rom;

interface IUserShellService {
    /**
     * Runs `sh -c <command>` with a bounded timeout and returns the merged
     * stdout+stderr encoded as "exitCode\noutput". A non-zero exit code means
     * the command failed; 124 marks a timeout (mirrors the `timeout` utility)
     * and 126 an internal error in the service process.
     */
    String exec(String command);
}
