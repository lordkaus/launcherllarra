# Regras R8 ultra-agressivas para APK mínimo

# Manter apenas entry points essenciais
-keep public class com.launcher.larla.activity.** { *; }
-keep public class com.launcher.larla.model.** { *; }
-keep public class com.launcher.larla.view.** { *; }
-keep public class com.launcher.larla.widget.** { *; }
-keep public class com.launcher.larla.util.** { *; }

# Remover todos os logs em release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Otimização máxima
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 10
-dontpreverify

# Remover atributos desnecessários
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Remover enums quando possível
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}