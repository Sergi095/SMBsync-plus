# Keep file format and network classes that the legacy sync engine and bundled jars
# may locate by name while R8 still shrinks and obfuscates the rest of the app.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.sentaroh.android.SMBSync2.SyncTaskItem { *; }
-keep class com.sentaroh.android.SMBSync2.SyncTaskEditor$SyncFolderEditValue { *; }
-keep class com.sentaroh.android.SMBSync2.SyncScheduleItem { *; }
-keep class com.sentaroh.jcifs.** { *; }
# jcifs-ng requests MD4 through Bouncy Castle's provider registry. The
# provider loads these classes by name, so R8 cannot discover them itself.
-keep class org.bouncycastle.jcajce.provider.digest.MD4 { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4$Digest { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4$HashMac { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4$KeyGenerator { *; }
-keep class org.bouncycastle.jcajce.provider.digest.MD4$Mappings { *; }
# SMB3 signing uses the AES-CMAC implementation registered the same way.
-keep class org.bouncycastle.jcajce.provider.symmetric.AES { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.AES$AESCMAC { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.AES$Mappings { *; }
