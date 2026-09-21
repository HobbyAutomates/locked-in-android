# R8 release shrinking rules — conservative keeps for this app.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
