# StateProof Navigation - Consumer ProGuard Rules
# These rules are applied to apps that use this library

# Keep state class names for reflection-based routing
-keepnames class * extends kotlin.Any {
    public static ** INSTANCE;
}

# Keep sealed class subclasses
-keep class * extends io.stateproof.navigation.** { *; }
