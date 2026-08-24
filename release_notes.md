# NexaFlow v3.39.21

### Bug Fixes & Improvements

- **Execution Options Visibility**: Fixed a critical bug in the compatibility engine that was overly aggressive in hiding specific system actions (such as Network Mode) from the automation builder UI. Previously, actions requiring privileged system capabilities were hidden immediately if the direct Android permission was not found, completely ignoring the fact that the user had an elevated root shell available. The logic has been corrected to properly respect the root fallback, ensuring all applicable actions are visible for rooted users.
