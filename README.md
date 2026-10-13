# Text Replacer

Text Replacer is an Android Xposed module that automatically replaces text typed through **Gboard** using your own replacement rules. Use it for shortcuts, recurring typos, or preferred wording across apps where you type with Gboard.

For example, add `brb → be right back`, then type `brb` with Gboard.

## Features

- Add, edit, and delete replacement rules in the companion app.
- View rules in an alphabetically sorted list.
- Match source text without regard to case; insert the replacement as saved.
- Handle committed text, with fallback paths for composing text and character-by-character typing.
- Store the dictionary through the LSPosed/libxposed remote preferences service.

## Requirements

- Android 12 or newer (minimum SDK 31).
- A rooted device with an Xposed framework supporting the modern libxposed API used by this module, such as a compatible LSPosed/Vector build.
- Gboard (`com.google.android.inputmethod.latin`).

This module hooks Gboard. Other keyboards and text entered without Gboard are outside its current implementation.

## Install and use

1. Download the APK from [Releases](https://github.com/cod-md/TextReplacer/releases) and install it.
2. Enable **Text Replacer** in your Xposed manager. Keep Gboard and Text Replacer's companion app selected in the module scope.
3. Restart Gboard or reboot the device to load the module.
4. Open Text Replacer and wait for the framework service to connect.
5. Tap **Add Word**, enter the source text and its replacement, then save.
6. Restart Gboard after adding, editing, or deleting rules so its dictionary is reloaded.
7. Type with Gboard in a text field to try the rule.

Both the source and replacement must be nonempty. Adding the same source again updates its replacement. Source names that differ only by case should be avoided because the module normalizes them when loading.

## Matching behavior and limitations

Replacement is **not restricted to whole words**. In a committed text chunk, matching text can be replaced inside a longer word: a rule `cat → dog` can turn `cats` into `dogs`. The character-by-character fallback also checks text endings without requiring a word boundary.

If multiple source rules overlap, or a replacement contains another source rule, the result can depend on the dictionary's iteration order. Avoid overlapping or chained rules when predictable output matters.

The module loads rules when its process/package starts; it does not currently listen for live dictionary updates. Behavior also depends on how Gboard and the receiving app handle composing text and cursor operations.

## Troubleshooting

- **“Waiting for LSPosed service…”**: check that the module is enabled, its companion app is in scope, and your framework supports the required service/API.
- **Rules saved but not applied**: restart Gboard or reboot, confirm Gboard is in scope, and test in a simple text field.
- **Unexpected replacements**: check for substring matches and overlapping rules.
- Framework logs use the tag `TextReplacer`. Verbose logging is currently enabled and can include typed/composing text and dictionary entries.

## Build from source

```sh
git clone --recurse-submodules https://github.com/cod-md/TextReplacer.git
cd TextReplacer
./gradlew assembleDebug
```

Use an Android SDK installation with API 36 and a Java 21 toolchain available to Gradle. The project compiles/targets SDK 36, uses Java 21 source compatibility, and currently sets app version `1.0.0` (version code `1`).

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds the debug APK; downloadable builds should be treated as debug builds unless explicitly described otherwise.

## Implementation

- `MainActivity.java`: dictionary editor and remote preference storage.
- `MainModule.java`: hooks Gboard's input connection and applies replacement rules.
- `WordAdapter.java`: replacement list with edit/delete actions.
- `META-INF/xposed/`: module entry point, metadata, and scope.

The current Android application ID is `com.example.module`. The project was originally based on an Xposed module template using [libxposed](https://github.com/libxposed/api).
