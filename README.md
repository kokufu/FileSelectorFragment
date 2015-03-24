FileSelectorFragment
====================

## How to use
First, import the library. If you use Android Studio (Gradle), you can do it like below.

build.gradle
```gradle
repositories {
    maven { url 'http://kokufu.github.io/maven-repo' }
}

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile 'com.kokufu.android.lib.ui:fileselectorfragment-aar:1.0'
}
```

Then, You can use it like below.

```java
public class MainActivity extends Activity implements FileSelectorFragment.OnFileSelectorFragmentInteractionListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Fragment f = FileSelectorFragment
                .newInstance(FileSelectorFragment.SelectionType.FILE, null, true);
        getChildFragmentManager().beginTransaction().add(R.id.container, f, FILE_SELECTOR_FRAGMENT_TAG)
                    .commit();
    }

    @Override
    public void onFileSelected(FileSelectorFragment parent, File file) {
        // Do something
    }
}
```
