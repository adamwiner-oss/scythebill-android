# ClassParserFactory (xml-parser) instantiates NodeParser subclasses via
# Class#getConstructor().newInstance() rather than a direct `new` call, so R8
# can't see the constructor is reachable from a call site — keep it explicitly.
-keepclassmembers class * implements com.scythebill.xml.NodeParser {
    public <init>();
}

# Room generates and loads *_Impl classes reflectively at runtime.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Joda-Time reads its provider/zone config via classpath resource scanning.
-dontwarn org.joda.time.**
-keep class org.joda.time.tz.Provider
-keep class org.joda.time.tz.NameProvider

# Guava uses reflection for some optional dependencies we don't ship.
-dontwarn com.google.common.**
-dontwarn java.lang.management.**

# Desktop model code carries Guice annotations (@Inject/@Singleton) for the
# desktop app's injector; Android never depends on Guice itself.
-dontwarn com.google.inject.**
