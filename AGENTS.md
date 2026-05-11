# AGENTS.md for AircraftWar Android Game

## Project Overview
AircraftWar is a simple Android game built with SurfaceView for rendering. The app uses a custom game loop in `MySurfaceView` for animations.

## Architecture
- **MainActivity**: Standard activity setting up the UI layout.
- **MySurfaceView**: Custom SurfaceView handling drawing and game loop via `Runnable` and `SurfaceHolder.Callback`.
- Package: `edu.hitsz.sufaceview` (note: "sufaceview" appears to be a typo for "surfaceview").

## Key Patterns
- Game loop: Implemented in `run()` method with `mbLoop` flag for start/stop.
- Drawing: `draw()` method locks canvas, draws background and circle, unlocks.
- Threading: Separate thread for animation, sleeping 200ms per frame.
- Surface lifecycle: `surfaceCreated` starts thread, `surfaceDestroyed` stops loop.

## Workflows
- Build: `./gradlew build`
- Install debug: `./gradlew installDebug`
- Run tests: `./gradlew test`
- Clean: `./gradlew clean`

## Conventions
- Use version catalogs in `gradle/libs.versions.toml` for dependencies.
- Java 11 compatibility.
- Min SDK 30, target 36.

## Current State
- Layout shows "Hello World!" placeholder; SurfaceView not yet integrated into UI.
- Basic animation: Colored circle on solid background, color cycling every 100 frames.

## File References
- Core game logic: `app/src/main/java/edu/hitsz/sufaceview/MySurfaceView.java`
- App config: `app/build.gradle`, `gradle/libs.versions.toml`
- UI: `app/src/main/res/layout/activity_main.xml` (placeholder)</content>
<parameter name="filePath">D:\AircraftWar\AGENTS.md
