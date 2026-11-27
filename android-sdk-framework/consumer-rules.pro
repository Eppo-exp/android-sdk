# ConfigurationCodec.Default stores the configuration with Java serialization. The stream records
# class names, field names and serialVersionUID, so keep them stable across app releases.
-keepnames class cloud.eppo.api.** implements java.io.Serializable
-keepclassmembers class cloud.eppo.api.** implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers enum cloud.eppo.api.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
