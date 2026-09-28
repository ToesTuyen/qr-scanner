# ============================================================================
#  R8 / ProGuard rules — obfuscation hardening for release.
#  Goal: make the release DEX hard to read/decompile while keeping every
#  reflection- or serialization-driven path working (Gson, Retrofit, Hilt,
#  Compose, Firebase, the ads/IAP wrapper). Debug builds are NOT obfuscated.
# ============================================================================

# ---- Obfuscation strength ---------------------------------------------------
# Flatten all obfuscated (non-kept) classes into the root package and let R8
# widen access + rename with a confusing dictionary. Kept classes are untouched.
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5
-obfuscationdictionary        proguard-dictionary.txt
-classobfuscationdictionary   proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# Keep just enough attributes for (a) working reflection/generics and (b)
# Crashlytics de-obfuscation via the uploaded mapping.txt. SourceFile is renamed
# so the real .kt names don't leak, but line numbers stay for symbolication.
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisible*Annotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Strip logging from release (no log strings to grep in the DEX) ----------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
-assumenosideeffects class com.vnnami.appkit.api.Logger {
    public *** v(...);
    public *** d(...);
    public *** i(...);
    public *** w(...);
    public *** e(...);
}

# ---- App: Gson / Retrofit DTOs (serialized reflectively — keep field names) --
# The AI request/response models are (de)serialized by Gson by field name and
# @SerializedName; obfuscating their fields would silently break the JSON.

# Any @SerializedName field anywhere must keep its name (value can still shrink).
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclasseswithmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
}

# ---- Gson core (TypeAdapters / generic reflection) --------------------------
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn sun.misc.**

# ---- Retrofit / OkHttp ------------------------------------------------------
# Retrofit reads method + parameter annotations reflectively.
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
# The app's own Retrofit service interface (annotations must survive).
-keep interface com.vnnami.androidphoneringtone.data.ai.AiApi { *; }

# ---- Kotlin metadata / coroutines / enums -----------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
# Enums are frequently used with valueOf() (Gson, when-exhaustiveness helpers).
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Android reflection-required members ------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class **.R$* { public static <fields>; }
# Views inflated from XML with a (Context, AttributeSet) ctor.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# ---- Hilt / Dagger (mostly covered by consumer rules — belt & suspenders) ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# The Application / manifest components are auto-kept by AGP, but pin them
# explicitly (referenced by name via the base wrapper).
-keep class com.vnnami.androidphoneringtone.AndroidPhoneRingtoneApplication { *; }
-keep class com.vnnami.androidphoneringtone.MainActivity { *; }
-keep class com.vnnami.androidphoneringtone.service.VideoWallpaperService { *; }
-keep class com.vnnami.androidphoneringtone.ui.real.RealIncomingCallActivity { *; }
-keep class com.vnnami.androidphoneringtone.ui.real.RingCallReceiver { *; }

# ---- Firebase / Crashlytics -------------------------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---- Ads mediation referenced reflectively (kept from the original rules) ----
-dontwarn com.facebook.ads.**
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.appevents.**
-keep class com.facebook.appevents.** { *; }
-dontwarn com.facebook.infer.annotation.**
-keep class com.facebook.infer.annotation.** { *; }

-keep class com.brian.base_application.base.BaseActivity { *; }
-keep class com.brian.base_application.language.** { *; }
-keep class com.brian.base_application.databinding.** { *; }

# BaseActivity discovers generated ViewBinding factories by the literal method names
# "inflate" and "bind". Preserve those names for any binding used through reflection.
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}
