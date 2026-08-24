# NexaFlow v3.39.20

### Bug Fixes & Improvements

- **Network Mode Recognition**: Fixed an issue on legacy Android devices (Android 12 and older) where the network mode capabilities were unreadable despite having root and all required permissions granted. The application will now correctly fall back to providing selectable cellular networks, ensuring the configuration menu is accessible.
- **Task Execution Engine**: Addressed critical memory and state leaks within the execution engine. Tasks that are forcefully cancelled or time out will now strictly clean up their lifecycles and clear durable execution checkpoints. This guarantees system stability and prevents the task queue from silently halting due to unbounded checkpoint accumulation.
- **Strict Task Cleanup**: Ensured that the execution engine strictly respects task end procedures (`completeExitOnFinish`), running termination actions safely and securely even when the primary task sequence crashes or cancels.
