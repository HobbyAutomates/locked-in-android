# R8 release shrinking rules — conservative keeps for this app.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# v2.13 nutrition: Guava (CameraX ListenableFuture) references annotation-only classes.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
