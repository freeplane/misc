Custom Ant tasks for use in freeplane.
======================================

 - target "jar" builds build/freeplaneant.jar
 - target "test" performs some tests
 - See test/build.xml for examples of their use

Task FormatTranslation
----------------------
formats a translation file and writes the result to another file.

The following transformations are made:
 - sort lines (case insensitive)
 - remove duplicates
 - if a key is present multiple times, then entries marked as
   [translate me] and [auto] are removed in favor of normal entries.
 - newline style is changed to the platform default.

Attributes:
 - dir: the input directory (default: ".")
 - outputDir: the output directory. Overwrites existing files if
   outputDir equals the input directory (default: the input directory)
 - includes: wildcard pattern (default: all regular files).
 - excludes: wildcard pattern, overrules includes (default: no
   excludes).
