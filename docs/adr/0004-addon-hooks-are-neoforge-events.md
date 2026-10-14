# Addon hooks are NeoForge events, not our own event bus

Inside the mod there is no cross-loader event abstraction. Thin NeoForge listeners call plain methods on `core` services. Addons subscribe to NeoForge events published in `api.neoforge`, beside the capability constants. A loader-neutral callback system of our own would mean a second event bus to design, document and keep stable, all for a Fabric port that may never happen. The accepted consequence is that a Fabric port must add a Fabric-side mirror of the addon hooks, while the loader-free `api` package carries over unchanged.
