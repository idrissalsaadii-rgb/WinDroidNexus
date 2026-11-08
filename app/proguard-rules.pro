# Keep Hilt-generated components.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# The EngineBridge invokes native runtimes by reflectionless ProcessBuilder,
# but model classes are serialized to DataStore — keep their members.
-keepclassmembers class com.windroid.nexus.domain.model.** { *; }

# Kotlin coroutines internals.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn org.jetbrains.annotations.**
