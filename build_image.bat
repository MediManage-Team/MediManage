@echo off
rmdir /s /q "dist\image"
"C:\Program Files\Java\jdk-25\bin\jpackage.exe" --name "MediManage" --input "dist\MediManage-0.1.5" --main-jar "MediManage.jar" --main-class "org.example.MediManage.Launcher" --type app-image --dest "dist\image"
