#!/bin/bash
# Test script to verify MediaBrowserService is working

echo "Testing MediaBrowserService..."
echo ""

echo "1. Checking if app is installed:"
adb shell pm list packages | grep androidautoradioplayer

echo ""
echo "2. Starting the service manually:"
adb shell am start-foreground-service com.example.androidautoradioplayer/.RadioService

echo ""
echo "3. Waiting 2 seconds..."
sleep 2

echo ""
echo "4. Checking if service is running:"
adb shell dumpsys activity services RadioService

echo ""
echo "5. Recent logs from RadioService:"
adb logcat -d | grep "RadioService" | tail -20
