# IDE Configuration Update

I have updated your VS Code and Gradle settings to resolve the Java classpath errors.

## Changes Made
1.  **Updated `.vscode/settings.json`**:
    *   Forced the Java Language Server to use the installed JDK 21 at `/usr/lib/jvm/java-21-openjdk-arm64`.
    *   Mapped `JavaSE-17` to this JDK path to satisfy the IDE extension's version check while using the newer runtime.

2.  **Updated `gradle.properties`**:
    *   Added `org.gradle.java.home` to explicitly point to the JDK 21 installation.

## Next Steps
Please **restart VS Code** or reload the window (`Ctrl+R` / `Cmd+R`) to apply these changes. The build errors should disappear.
