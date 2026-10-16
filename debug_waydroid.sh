#!/bin/bash

# JDK compatível com o AGP (Java 27 quebra o jlink do android-35)
if [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

PACKAGE_NAME="com.launcher.llara"
MAIN_ACTIVITY="com.launcher.llara.activity.LauncherActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

while true; do
    clear
    echo -e "\e[34m🏗️  Compilando (Debug)...\e[0m"

    if ./gradlew assembleDebug 2>&1 | tail -5; then
        if [ -f "$APK_PATH" ]; then
            echo -e "\e[32m✅ Build: $(du -sh "$APK_PATH" | cut -f1)\e[0m"
        fi

        # --- Dispositivos USB ---
        USB_DEVICES=$(adb devices | grep -v "List" | grep "device$" | awk '{print $1}')

        # --- WayDroid (best-effort) ---
        WAYDROID_IP=$(sudo waydroid status 2>/dev/null | grep "IP address" | awk '{print $NF}')
        if [ -n "$WAYDROID_IP" ]; then
            echo -e "\e[33m🌐 WayDroid em $WAYDROID_IP\e[0m"
            printf 'setprop service.adb.tcp.port 5555\nstop adbd\nstart adbd\n' | sudo waydroid shell 2>/dev/null
            if [ -f ~/.android/adbkey.pub ]; then
                printf 'mkdir -p /data/misc/adb\ncat > /data/misc/adb/adb_keys <<EOF\n%s\nEOF\n' "$(cat ~/.android/adbkey.pub)" | sudo waydroid shell 2>/dev/null
            fi
            adb connect "$WAYDROID_IP:5555" 2>/dev/null
        fi

        # --- Relista (agora com WayDroid se conectou) ---
        ALL_DEVICES=$(adb devices | grep -v "List" | grep "device$" | awk '{print $1}')

        if [ -z "$ALL_DEVICES" ]; then
            echo -e "\e[31m❌ Nenhum dispositivo encontrado.\e[0m"
        else
            for DEVICE in $ALL_DEVICES; do
                # identifica tipo
                if echo "$DEVICE" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+'; then
                    LABEL="🌐 WayDroid ($DEVICE)"
                else
                    LABEL="📱 USB ($DEVICE)"
                fi
                echo -e "\e[35m$LABEL\e[0m"
                adb -s "$DEVICE" install -r -d "$APK_PATH" 2>&1 | grep -v "Performing Streamed Install\|Success$"
                adb -s "$DEVICE" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY" 2>&1 | tail -1
            done
            echo -e "\e[32m✨ Concluído!\e[0m"
        fi
    else
        echo -e "\e[31m🔥 Build falhou.\e[0m"
    fi

    echo -e "\n--------------------------------------------------------"
    echo -e "🔄 [\e[1mEnter/y\e[0m] Recompilar | qualquer tecla sair"
    read -p "Ação: " OPTION
    if [[ -z "$OPTION" || "$OPTION" =~ ^[Yy]$ ]]; then
        continue
    else
        echo -e "\e[33m👋\e[0m"
        break
    fi
done
