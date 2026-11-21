# Arc-Video

![GitHub Workflow Status (master)](https://img.shields.io/github/actions/workflow/status/m1cxzfw3q/arc-video/publish_snapshot.yml?branch=master)

[![](https://jitpack.io/v/m1cxzfw3q/arc-video.svg)](https://jitpack.io/#m1cxzfw3q/arc-video)

[![Maven Central Version](https://img.shields.io/maven-central/v/com.badlogicgames.gdx-video/gdx-video?label=release)](https://search.maven.org/artifact/com.badlogicgames.gdx-video/gdx-video)

A arc cross-platform video rendering extension

## Contents
* [Getting Started](#getting-started)
  * [Repositories](#repositories)
  * [Using with Gradle](#gradle-dependency-declarations)
* [Playing video](#playing-video)
* [Encoding recommendations](#encoding-recommendations)
  * [File format and codec](#file-format-and-codec)
  * [Resolution and framerate](#resolution-and-framerate)
* [Contributing](#contributing)
  * [Building from source](#building-from-source)
  * [Cross-compilation on macOS](#cross-compilation-on-macos)
* [Licensing](#licensing)

## Getting Started

Arc-video is currently available in maven with official builds and snapshot builds. You can find them at the following repositories:

### Repositories

* **Official**  For official releases, use https://oss.sonatype.org/content/repositories/releases
* **Snapshots** For snapshot builds, use https://oss.sonatype.org/content/repositories/snapshots

### Gradle dependency declarations
##### Core:
```groovy
implementation "com.github.m1cxzfw3q.arc-video:arc-video:$arcVideoVersion"
```

```kotlin
implementation("com.github.m1cxzfw3q.arc-video:arc-video:$arcVideoVersion")
```

##### Desktop:
```groovy
implementation "com.github.m1cxzfw3q.arc-video:arc-video-lwjgl3:$arcVideoVersion"
// or (when using legacy LWJGL2):
implementation "com.github.m1cxzfw3q.arc-video:arc-video-lwjgl:$arcVideoVersion"
```

```kotlin
implementation("com.github.m1cxzfw3q.arc-video:arc-video-lwjgl3:$arcVideoVersion")
// or (when using legacy LWJGL2):
implementation("com.github.m1cxzfw3q.arc-video:arc-video-lwjgl:$arcVideoVersion")
```

##### Android:
```groovy
implementation "com.github.m1cxzfw3q.arc-video:arc-video-android:$arcVideoVersion"
```

```kotlin
implementation("com.github.m1cxzfw3q.arc-video:arc-video-android:$arcVideoVersion")
```

## Playing video

First, get a video player for the current platform using

```java
VideoPlayer player = VideoPlayerCreator.createVideoPlayer();
```

Then, load a video from your game's assets.

```java
Fi file = Core.files.internal("video.webm");
player.load(file);
```

Then you can play the video:

```java
player.play();
```

Once the video has fully loaded, you may retrieve additional
information about the file.

```java
if (player.isBuffered()) {
    int videoWidth = player.getVideoWidth();
    int videoHeight = player.getVideoHeight();
}
```

On each frame, call the `update()` function to acquire new video frames
and keep the decoder running. You may then retrieve the frame using `getTexture()`,
but note that the texture may be larger than the video itself. The provided
`VideoElement` takes care of both updating and drawing when using Scene2D.

Once you are done playing, remember to `dispose()` the video player.

## Encoding recommendations

Depending on the devices your game targets, you may need to encode
your videos with multiple formats and resolutions.

See the following tables for a rough overview, but remember to test
your game on real devices.

### File format and codec

| Format                 | Desktop  | Android  | iOS    | Web |
|------------------------|----------|----------|--------|-----|
| MP4 (H.264/AVC + AAC)  | ❌ *      | ✅        | ✅      | ⚠️  |
| MP4 (H.265/HEVC + AAC) | ❌ *      | ⚠️ > 5.0 | ✅ > 11 | ⚠️  |
| WebM (VP8 + Vorbis)    | ✅        | ✅        | ❌      | ✅   |
| WebM (VP9 + Opus)      | ✅        | ⚠️ > 5.0 | ❌      | ❔   |
| MKV (AV1 + Opus)       | ✅        | ⚠️ > 10  | ❌ *   | ⚠️  |

#### Additional notes

**Desktop:** Additional formats and codecs can be enabled when compiling 
arc-video yourself. See the file [arc-video-desktop/FFmpeg/build.gradle](arc-video-desktop/FFmpeg/build.gradle).

**Android**: See the following webpage for officially supported media formats: 
<https://developer.android.com/guide/topics/media/platform/supported-formats>.
Note that this support table is not always accurate, especially for devices
and emulator images without Google Play Services. 

In practice, H.264 is a good choice for physical Android tablets / smartphones
as most devices (including old and cheap ones) have hardware decoding support.
Remember to dispose a VideoPlayer before loading a new one, as some devices
fail to load the next video otherwise.

### Resolution and framerate

Even when the format is officially supported, not all devices
have hardware acceleration for video decoding. Many video
codecs specify the decoding capabilities as "levels" of bitrate,
video resolution and framerate. Trying to play a video above the
device's capabilities may cause increased power usage, frame drops,
visual or audio artifacts or even game crashes.

#### Examples (using H.264 levels):

| Level | (Mobile) device category | Example 1            | Example 2           |
|-------|--------------------------|----------------------|---------------------|
| 3     | Low end                  | 720 x  480 @  30fps  | -                   |
| 3.1   |                          | 720 x  480 @  60fps  | 1280 x  720 @ 30fps |
| 4     | Budget / mid range       | 1280 x  720 @  60fps | 1920 x 1080 @ 30fps |
| 4.2   |                          | 1280 x  720 @ 120fps | 1920 x 1080 @ 60fps |
| 5.1   | High performance         | 1920 x 1080 @ 120fps | 3840 x 2160 @ 30fps |

#### Possible approaches:

- Use a lower resolution as 'baseline' that works on all target devices
- Ship your videos in multiple resolutions and guess the best one for the
  device (e.g. based on OS version, RAM size or display resolution)

## Contributing

### Building from source

To build from source, clone or download this repository, then open it in Android Studio
and perform a gradle sync. If you get any *ZipFile* errors, watch the logs above and
install the remaining Android SDK components through the SDK manager.

When building for desktop, build the native components using the Gradle tasks
`:arc-video-desktop:buildFFmpeg{platform}{arch}`
and `:arc-video-desktop:jnigenBuild{platform}{arch}`.

Perform the following command to compile and upload the library in your local repository:

    ./gradlew publishToMavenLocal

See `build.gradle` file for current version to use in your dependencies.

### Cross-compilation on MacOS

Install the cross-compilers using [homebrew](https://brew.sh) with the commands
~~~
brew install mingw-w64 nasm
brew tap messense/macos-cross-toolchains
brew install i686-unknown-linux-gnu x86_64-unknown-linux-gnu x86_64-unknown-linux-gnu arm-unknown-linux-gnueabihf aarch64-unknown-linux-gnu
~~~


## Licensing
The project is licensed under the Apache 2 License, meaning you can use it free of charge, without strings attached in commercial and non-commercial projects. We love to get (non-mandatory) credit in case you release a game or app using this project!
