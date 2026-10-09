# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /path/to/sdk/tools/proguard/proguard-android.txt

# Keep Room entities
-keep class com.example.localupload.database.** { *; }

# Keep ViewModel classes
-keep class com.example.localupload.viewmodel.** { *; }
