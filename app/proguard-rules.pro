# Release rules for the standalone QR Scanner application.
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisible*Annotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep IVISTA_TECH diagnostics in customer Release builds, including the CURL request/response.
-keep class com.ivistatect.qrscanner.util.Logger { *; }

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class **.R$* { public static <fields>; }

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
