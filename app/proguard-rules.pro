# R8 shrinks and obfuscates the app. The libraries (Compose, CameraX, coroutines) ship their own keep rules.
-keepattributes SourceFile,LineNumberTable
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
