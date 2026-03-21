@echo off
echo ============================================
echo  GlycoMate — First-time setup
echo ============================================
echo.
echo Downloading gradle-wrapper.jar...
curl -L -o gradle\wrapper\gradle-wrapper.jar "https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo.
    echo SUCCESS!
    echo.
    echo Next steps:
    echo 1. Open Android Studio
    echo 2. File ^> Open ^> select THIS folder (GlycoMate2)
    echo 3. Wait for Gradle sync to finish
    echo 4. Run on your device or emulator
    echo.
    echo IMPORTANT: Make sure this folder path has NO Greek characters!
    echo Good:  E:\projects\GlycoMate\
    echo Bad:   E:\Έγγραφα\GlycoMate\
) else (
    echo.
    echo FAILED. Download manually:
    echo https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar
    echo Place in: gradle\wrapper\gradle-wrapper.jar
)
pause
