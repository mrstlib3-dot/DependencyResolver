# ============================================
# REMOVE JAVA 9+ MODULE-INFO
# ============================================
-dontwarn module-info
-dontnote module-info

# Remove all module-info classes
-assumenosideeffects class module-info {
    *;
}

# Remove multi-release versioned classes
-dontwarn META-INF.versions.**
-dontnote META-INF.versions.**

# Force removal of versioned classes
-assumenosideeffects class ** {
    *;
}

# ============================================
# REMOVE SPECIFIC PROBLEMATIC CLASSES
# ============================================

# Remove Kotlin coroutines problematic flow classes
-assumenosideeffects class kotlinx.coroutines.flow.FlowKt__BuildersKt$asFlow$* {
    *;
}

# Remove wstx XML parser inner class
-assumenosideeffects class com.ctc.wstx.shaded.msv.relaxng_datatype.helpers.DatatypeLibraryLoader$1 {
    *;
}

# ============================================
# REMOVE ALL DEBUG INFO (causes D8 issues)
# ============================================
-keepattributes !LocalVariableTable,!LocalVariableTypeTable
-keepattributes !StackMapTable,!LineNumberTable
-keepattributes !SourceFile,!SourceDebugExtension

# ============================================
# REMOVE KOTLIN METADATA (if not needed)
# ============================================
-dontwarn kotlin.Metadata
-assumenosideeffects class kotlin.Metadata {
    *;
}

# ============================================
# STRIP ALL ATTRIBUTES EXCEPT ESSENTIAL
# ============================================
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,EnclosingMethod

# ============================================
# REMOVE SIGNATURE FILES
# ============================================
-assumenosideeffects class META-INF {
    *;
}

# ============================================
# IGNORE ALL WARNINGS
# ============================================
-dontwarn **
-ignorewarnings

# ============================================
# DON'T OBFUSCATE (to keep class names)
# ============================================
-dontobfuscate

# ============================================
# OPTIMIZE AGGRESSIVELY TO REMOVE UNUSED
# ============================================
-optimizationpasses 5
-dontpreverify
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# ============================================
# REMOVE UNUSED CLASSES AUTOMATICALLY
# ============================================
-dontshrink  # Don't shrink, just remove specified classes
# OR use shrink to remove unused:
# -dontshrink false  # Remove unused classes

# ============================================
# KEEP YOUR ACTUAL CODE
# ============================================
-keep class com.yourpackage.** { *; }
-keep class ** { *; }  # Keep everything else
