# WTConnect

WTConnect is an optional AndroidAPS `PluginType.GENERAL` integration for the
Yidao/WT family monitoring APIs. It intentionally depends only on AndroidAPS
core contracts, so this directory can be maintained as a private Git submodule.

Implemented protocol surface:

- Seven `/wt_gateway/v1/family/aps/*` synchronization endpoints.
- PHONE ticket authentication and one-use WebSocket tickets.
- WebSocket reconnect, heartbeat, glucose uplink and command rejection.
- Normal remote bolus and matching stop command for Apex, Embecta and Lenomed.
- Persistent operation deduplication and an HTTP retry outbox.

Remote pump commands are disabled by default. Square/double bolus, pump
pause/resume and loop commands are rejected until their driver and protocol
requirements are implemented safely.

The test endpoint is used by default as required by the integration document.
Change `WTConnectNetworkConfig.BASE_URL` and `WS_URL` when promoting to the
production environment.
