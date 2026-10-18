# PS5 shortcut Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show a connected PS5 beside Live TV and swap the header sides.

**Architecture:** Use the TV input service and the CEC input identified in the device inventory. Refresh through input callbacks while Home is visible; launch the passthrough channel URI.

**Tech Stack:** Java, Android TV, XML, Gradle.

---

### Task 1: HDMI shortcut
Modify `launcher/app/src/main/java/com/gathin/tvhome/AppAdapter.java` to insert a PS5 input entry after Live TV only when the CEC input exists and is not disconnected. Use a gamepad vector in `launcher/app/src/main/res/drawable/ic_gamepad.xml`. Configure the observed CEC ID in `launcher/app/src/main/res/values/config.xml`. Do not open app settings for HDMI entries.

### Task 2: Lifecycle and header
Modify `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` to register input callbacks on resume and unregister on pause. Avoid rebuilding an unchanged grid. Swap start/end gravity in `launcher/app/src/main/res/layout/activity_main.xml`.

### Task 3: Validation
Run the existing offline assembleDebug command. Install the APK, inspect the Home screenshot and exercise the PS5 shortcut, then return Home. Verify the conditional insertion against current tv_input state. Physical disconnect requires user hardware interaction; do not claim it tested.
