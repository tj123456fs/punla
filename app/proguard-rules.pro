# Most of our dependencies (Room, MapLibre, Firebase Messaging, Glance,
# WorkManager) ship their own consumer-rules.pro bundled in the AAR, so R8
# picks those up automatically — we don't need to duplicate them here.
# These are the app-specific rules that experience shows are worth adding
# up front, since they cover the couple of things R8 can't infer on its own.

# --- Room entities / DAO result types ---
# Room's DAOs are generated at compile time from these classes, so R8 sees
# the real usage and keeps them correctly. Nothing extra needed here, but if
# a "no such column"/reflection-style crash ever shows up in a release build
# only, it's almost always solved by adding a targeted keep for the specific
# entity, e.g.:
# -keep class com.uplb.punla.data.entity.** { *; }

# --- Firebase Cloud Messaging ---
# Keep our FCM service and its callback methods; the service is instantiated
# by the OS via the manifest, not a direct code reference, so R8 can't see
# that it's used.
-keep class com.uplb.punla.push.PunlaFirebaseMessagingService { *; }

# --- WorkManager workers ---
# Same story: Workers are instantiated by class name from WorkManager's
# internal machinery, not called directly from our code.
-keep class com.uplb.punla.worker.** extends androidx.work.ListenableWorker { *; }

# --- Glance app widgets / receivers ---
# Instantiated by the OS via AndroidManifest, same reasoning as above.
-keep class com.uplb.punla.widget.** { *; }

# --- org.json ---
# We build JSON manually (JSONObject/JSONArray) in BackupManager rather than
# reflection-based serialization, so no field-name keep rules are needed
# there. If BackupManager's export/import format ever switches to a
# reflection-based serializer (Gson/Moshi/kotlinx.serialization), add the
# matching keep rules for the affected data classes at that point.

# --- Kotlin coroutines / reflection metadata some libraries probe for ---
-dontwarn kotlinx.coroutines.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
