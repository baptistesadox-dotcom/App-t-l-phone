# Règles R8 spécifiques au launcher.
# kotlinx.serialization : les classes @Serializable sont gérées par les règles fournies
# par la bibliothèque ; on garde en plus les noms des classes de configuration
# pour que les sauvegardes JSON restent lisibles d'une version à l'autre.
-keep,includedescriptorclasses class fr.epure.launcher.data.config.** { *; }

# Sérialiseurs générés par kotlinx.serialization (configuration, météo).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class fr.epure.launcher.**$$serializer { *; }
-keepclassmembers class fr.epure.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class fr.epure.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
