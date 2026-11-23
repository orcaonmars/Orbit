# Orbit release build rules for R8 (shrink and optimize).
#
# Orbit is open source, so renaming classes protects nothing and only makes crash reports harder to read.
# Keep names and line numbers; R8 still removes unused code and resources and optimizes the rest.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# kotlinx.serialization, AndroidX and Compose ship their own R8 rules inside their libraries.
# Activities, services, receivers, the widget and the tile are kept through AndroidManifest.xml.
