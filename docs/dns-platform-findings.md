# Android DNS Platform Findings

## Verified official behavior

`LinkProperties.getDnsServers()` exposes DNS server addresses for a network link. `LinkProperties.isPrivateDnsActive()` reports whether Private DNS is in use, while `getPrivateDnsServerName()` returns the configured hostname in strict mode and may be null in opportunistic mode.

An ordinary application should treat these values as read-only diagnostics. Android does not expose a general public API that lets a normal third-party application directly write the system Private DNS setting. The supported user-facing route is to open the system Network and Internet / Private DNS settings screen.

`VpnService` can create an app-owned VPN interface after explicit user consent. A VPN may route traffic through an app-controlled DNS resolver, but it does not change the device-wide Private DNS setting. Only one VPN connection can be active at a time, and Android shows a system-managed VPN notification while it runs.

## Implementation consequence

NexaFlow can safely implement current-DNS inspection through `ConnectivityManager` and `LinkProperties`, expose the Android Private DNS state, and provide built-in provider profiles as validated hostnames. Direct system-wide mutation requires user action in Android Settings or elevated device-owner/root capabilities; it must never be falsely reported as completed by the app.

Sources:

1. https://developer.android.com/reference/android/net/LinkProperties — Android `LinkProperties` API reference.
2. https://developer.android.com/reference/android/net/VpnService — Android `VpnService` API reference.
