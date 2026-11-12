# ORANGE SHIELD — regras ProGuard/R8 (build release)

# Room: mantém entidades, DAOs e implementações geradas
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclasseswithmembernames class * {
    @androidx.room.* <fields>;
}
-keepclasseswithmembernames class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# Corrotinas
-dontwarn kotlinx.coroutines.**

# Mantém a linha do stack trace para depuração do motor de triagem
-keepattributes SourceFile,LineNumberTable
