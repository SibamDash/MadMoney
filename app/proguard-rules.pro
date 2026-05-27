# Keep app's own classes (data models, activities, fragments, adapters)
-keep class com.example.myapplication.** { *; }

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep SQLiteOpenHelper subclasses (DatabaseHelper)
-keep class * extends android.database.sqlite.SQLiteOpenHelper { *; }

# Preserve line numbers in stack traces for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
