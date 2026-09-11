# MD4a native engine: JNI registration is name-based, so the native method
# must not be renamed/removed by R8.
-keep class com.md4a.parser.NativeMd4aParser { *; }
