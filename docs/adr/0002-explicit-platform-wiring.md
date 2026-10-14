# Platform services are wired explicitly, not through ServiceLoader

Multiloader mods usually find their platform implementation with `java.util.ServiceLoader`. We don't. The NeoForge `@Mod` constructor builds every platform implementation and passes them to `core` once (`Vftv.init(platform)`). `ServiceLoader` is reflection in all but name, and allowing it would be the only exception to the ban on runtime reflection. Explicit wiring is no more code, and a Fabric entrypoint would do the same thing. ArchUnit bans `ServiceLoader` along with the rest of reflection.
