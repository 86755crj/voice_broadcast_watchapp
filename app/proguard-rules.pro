# Wear app proguard rules. Debug build 不开 minify，所以这里很简单。
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.crj.voicebroadcast.** { *; }
