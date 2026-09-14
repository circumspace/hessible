# Project-specific ProGuard/R8 rules.

# ez-vcard reflects over its own model/property classes when reading and writing vCards, so the
# whole library must be kept intact. Its optional integrations (templating, XML) are not used.
-keep class ezvcard.** { *; }
-dontwarn ezvcard.**
-dontwarn freemarker.**
-dontwarn org.jdom2.**
-dontwarn javax.xml.**
# jsoup (transitive via ez-vcard) references the optional re2j engine, which is not bundled.
-dontwarn com.google.re2j.**

# Quartz ships its own consumer rules; these are kept here only as a defensive backstop.
-keep class com.vitorpamplona.quartz.** { *; }
