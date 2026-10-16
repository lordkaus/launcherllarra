#!/bin/bash
#
# Deploy do Launcher Llara: compila (release) e instala em Waydroid/USB/Wi-Fi.
#

# JDK compatível com o AGP (Java 27 quebra o jlink do android-35)
if [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Configurações de Caminho
PACKAGE_NAME="com.launcher.llara"
MAIN_ACTIVITY="com.launcher.llara.activity.LauncherActivity"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
# Último IP bom conhecido do Waydroid (fallback quando a detecção falhar)
WAYDROID_FALLBACK_IP="192.168.240.112"

# Variável de controle para perguntar apenas uma vez por sessão do script
ALREADY_ASKED_ADB_QR=false

# --- Helpers ---------------------------------------------------------------

# Lista seriais de dispositivos com estado "device" (conectados e autorizados)
adb_serials() {
    adb devices 2>/dev/null | awk '$2 == "device" {print $1}'
}

# Lista apenas transporte USB (celular físico). emulator-XXXX NÃO entra aqui.
# (Cobre os dois formatos de "adb devices -l": "usb:<porta>" e "transport:usb".)
usb_serials() {
    adb devices -l 2>/dev/null | awk '$2 == "device" { for (i = 3; i <= NF; i++) if ($i == "transport:usb" || $i ~ /^usb:[0-9]/) { print $1; break } }'
}

# Detecta o IP do container Waydroid.
# NOTA: o IP da interface waydroid0 (.1) é o HOST, não o container — por isso
# esse método foi removido; usamos o IP informado pelo próprio Waydroid.
detect_waydroid_ip() {
    local ip=""
    # Método 1: waydroid status (sem sudo primeiro; com sudo -n se precisar)
    local st
    st=$(timeout 10 waydroid status 2>/dev/null)
    if [ -z "$st" ]; then
        st=$(timeout 10 sudo -n waydroid status 2>/dev/null)
    fi
    # Sessão parada = Waydroid desligado: nem tenta (evita loop/timeout)
    if [ -n "$st" ] && echo "$st" | grep -qi "Session:.*STOPPED"; then
        return 1
    fi
    ip=$(echo "$st" | grep "IP address" | awk '{print $NF}')
    # Método 2: rota dentro do container (último campo = src do container)
    if [ -z "$ip" ]; then
        ip=$(sudo -n waydroid shell ip route 2>/dev/null | awk '/src/{print $NF}' | head -1)
    fi
    # Método 3: fallback estático
    if [ -z "$ip" ]; then
        ip="$WAYDROID_FALLBACK_IP"
    fi
    # Validação mínima: IPv4 com 4 octetos
    if [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "$ip"
    fi
}

# Garante ADB do Waydroid acessível. Pula tudo se já estiver conectado.
ensure_waydroid_adb() {
    local ip="$1"
    [ -z "$ip" ] && return 1
    if adb_serials | grep -qx "$ip:5555"; then
        echo -e "\e[32m🌐 Waydroid já conectado em $ip:5555.\e[0m"
        return 0
    fi
    # Sem ping, sem tentativa (evita travar em timeouts longos de TCP/adb)
    if ! ping -c1 -W2 "$ip" >/dev/null 2>&1; then
        echo -e "\e[31m⚠️  Waydroid em $ip não responde ao ping. Verifique se a sessão está rodando.\e[0m"
        return 1
    fi
    echo -e "\e[33m🌐 Waydroid detectado em $ip. Configurando ADB...\e[0m"
    timeout 15 sudo -n waydroid shell 2>/dev/null <<'EOSHELL'
setprop service.adb.tcp.port 5555
stop adbd
start adbd
EOSHELL
    if [ -f ~/.android/adbkey.pub ]; then
        ADBKEY=$(cat ~/.android/adbkey.pub)
        { echo 'mkdir -p /data/misc/adb'; echo "cat > /data/misc/adb/adb_keys <<EOF"; echo "$ADBKEY"; echo "EOF"; } \
            | timeout 15 sudo -n waydroid shell 2>/dev/null
    fi
    if timeout 20 adb connect "$ip:5555" 2>/dev/null | grep -qi "connected"; then
        echo -e "\e[32m✅ ADB do Waydroid conectado.\e[0m"
        return 0
    fi
    echo -e "\e[31m⚠️  Não foi possível conectar ao Waydroid em $ip:5555.\e[0m"
    return 1
}

# --- Loop principal ---------------------------------------------------------

while true; do
    clear
    echo -e "\e[34m🏗️  1. Iniciando Compilação (Gradle Release)...\e[0m"

    # 1. Build Release (uma única invocação; debug tem script próprio)
    if ./gradlew assembleRelease; then

        # Lógica para capturar o tamanho do APK
        if [ -f "$APK_PATH" ]; then
            APK_SIZE=$(du -sh "$APK_PATH" | cut -f1)
            echo -e "\e[32m✅ Build concluído com sucesso! \e[1;33m($APK_SIZE)\e[0m"
        else
            echo -e "\e[31m❌ Build OK, mas o APK não foi localizado em $APK_PATH.\e[0m"
        fi

        # 2. Auto-setup Waydroid ADB
        WAYDROID_IP=$(detect_waydroid_ip)
        if [ -n "$WAYDROID_IP" ]; then
            ensure_waydroid_adb "$WAYDROID_IP"
        else
            echo -e "\e[31m⚠️  Waydroid não detectado. Pulando configuração ADB.\e[0m"
        fi

        # 2.1 Verificação de celular físico via transporte USB
        PHYSICAL_PHONE=$(usb_serials)

        if [ -z "$PHYSICAL_PHONE" ]; then
            # Nenhum celular físico (USB) conectado
            if [ "$ALREADY_ASKED_ADB_QR" = false ]; then
                echo -ne "\e[33m📱 Nenhum celular USB conectado. Abrir npx adb-qr? [Y/n]: \e[0m"
                read -r QR_OPTION

                if [[ -z "$QR_OPTION" || "$QR_OPTION" =~ ^[Yy]$ ]]; then
                    if ! command -v npx >/dev/null 2>&1; then
                        echo -e "\e[31m❌ npx não encontrado. Instale o Node.js para usar o QR Code.\e[0m"
                    else
                        echo -e "\e[34m🚀 Iniciando npx adb-qr...\e[0m"
                        npx adb-qr

                        # Valida se conectou após rodar o comando
                        CHECK_AFTER=$(usb_serials)
                        if [ -z "$CHECK_AFTER" ]; then
                            # Aceita também Wi-Fi (tcpip) como sucesso
                            CHECK_AFTER=$(adb_serials | grep ":")
                        fi
                        if [ -n "$CHECK_AFTER" ]; then
                            echo -e "\e[32m✅ Celular conectado com sucesso: $CHECK_AFTER\e[0m"
                        else
                            echo -e "\e[31m❌ Falha ou conexão cancelada no momento da conexão via QR Code.\e[0m"
                        fi
                    fi
                else
                    echo -e "\e[33m⏭️ Conexão via QR Code ignorada nesta sessão.\e[0m"
                fi

                ALREADY_ASKED_ADB_QR=true
            fi
        else
            echo -e "\e[32m📱 Celular físico conectado via USB: $PHYSICAL_PHONE\e[0m"
        fi

        echo -e "\n\e[34m🔍 3. Verificando Dispositivos Conectados (Físicos, Emuladores e Waydroid)...\e[0m"

        # 3. Detecção de Múltiplos Dispositivos (somente autorizados)
        DEVICES=$(adb_serials)

        if [ -z "$DEVICES" ]; then
            echo -e "\e[31m❌ Erro: Nenhum dispositivo autorizado encontrado via ADB.\e[0m"
            echo "Certifique-se de que o Waydroid está rodando ou reconecte o dispositivo."
            echo "Dispositivos visíveis (incl. não autorizados/offline):"
            adb devices 2>/dev/null | tail -n +2
        else
            # 4. Instalação e Execução em Lote
            if [ -f "$APK_PATH" ]; then
                FAILED=0
                # O laço FOR garante que vai instalar no celular E no Waydroid
                for DEVICE in $DEVICES; do
                    echo -e "\n\e[35m📱 Alvo detectado: $DEVICE\e[0m"
                    echo "📲 Instalando APK..."
                    if adb -s "$DEVICE" install -r "$APK_PATH"; then
                        echo "🏃 Abrindo $PACKAGE_NAME..."
                        if ! adb -s "$DEVICE" shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"; then
                            echo -e "\e[33m⚠️  Instalou, mas falhou ao abrir no $DEVICE.\e[0m"
                            FAILED=$((FAILED + 1))
                        fi
                    else
                        echo -e "\e[31m❌ Falha ao instalar no $DEVICE. Pulando start.\e[0m"
                        FAILED=$((FAILED + 1))
                    fi
                done

                if [ "$FAILED" -eq 0 ]; then
                    echo -e "\n\e[32m✨ Ciclo de Deploy concluído em todos os alvos!\e[0m"
                else
                    echo -e "\n\e[33m⚠️  Deploy concluído com $FAILED falha(s). Veja acima.\e[0m"
                fi
            else
                echo -e "\e[31m❌ Erro crítico: O APK não foi encontrado em $APK_PATH\e[0m"
            fi
        fi
    else
        echo -e "\e[31m🔥 Falha no Build. Corrija os erros de código acima antes de prosseguir.\e[0m"
    fi

    # 5. Controle Manual de Reinício
    echo -e "\n--------------------------------------------------------"
    echo -e "🔄 Deseja compilar novamente? [\e[1mEnter/y\e[0m para SIM | qualquer tecla para sair]"
    read -p "Ação: " OPTION

    if [[ -z "$OPTION" || "$OPTION" =~ ^[Yy]$ ]]; then
        continue
    else
        echo -e "\e[33m👋 Saindo. Bom trabalho!\e[0m"
        break
    fi
done
