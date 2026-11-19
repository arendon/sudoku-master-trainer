# Preserve annotations, inner classes, and generic signatures
-keepattributes *Annotation*, InnerClasses, Signature

# Keep Room entities, DAOs, and database classes
-keep class com.arendon.sudokumaster.data.local.** { *; }

# Keep ML Kit Digital Ink & Play Services classes without spurious warnings
-dontwarn com.google.mlkit.**
-dontwarn kotlinx.atomicfu.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
