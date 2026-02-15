# Waselak iOS Apps

Three iOS apps built with Compose Multiplatform (Kotlin/Native frameworks):

- **WaselakManager** — Restaurant management app
- **WaselakCashier** — Point-of-sale cashier app
- **WaselakDelivery** — Delivery tracking app

## Setup

### Option 1: Using XcodeGen (Recommended)

1. Install XcodeGen: `brew install xcodegen`
2. Generate the Xcode project: `cd iosApp && xcodegen generate`
3. Open `Waselak.xcodeproj` in Xcode
4. Select a target and run

### Option 2: Manual Xcode Setup

1. Open Xcode and create a new iOS App project
2. Set the Swift entry point to use the corresponding `*App.swift` file
3. Add the framework search path:
   - Debug (Simulator): `$(SRCROOT)/../app-manager/build/bin/iosSimulatorArm64/debugFramework`
   - Release (Device): `$(SRCROOT)/../app-manager/build/bin/iosArm64/releaseFramework`
4. Add `-ObjC` to Other Linker Flags
5. Add a Run Script build phase to build the Kotlin framework before compilation

## Building Frameworks

Build the Kotlin/Native frameworks before running in Xcode:

```bash
# Simulator (Apple Silicon Mac)
./gradlew :app-manager:linkDebugFrameworkIosSimulatorArm64
./gradlew :app-cashier:linkDebugFrameworkIosSimulatorArm64
./gradlew :app-delivery:linkDebugFrameworkIosSimulatorArm64

# Physical device
./gradlew :app-manager:linkReleaseFrameworkIosArm64
./gradlew :app-cashier:linkReleaseFrameworkIosArm64
./gradlew :app-delivery:linkReleaseFrameworkIosArm64
```
