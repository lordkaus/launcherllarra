#!/bin/bash

# Detecta o primeiro dispositivo ativo
DEVICE=$(adb devices | grep -v "List" | grep "device$" | awk '{print $1}' | head -n 1)

if [ -z "$DEVICE" ]; then
    echo -e "\e[31m❌ Erro: Nenhum dispositivo ADB ativo encontrado.\e[0m"
    exit 1
fi

PACKAGE="com.launcher.llara"

# Limpa a tela do terminal para começar do zero visualmente
clear

echo -e "\e[34m🔍 Monitorando logs em TEMPO REAL de:\e[0m \e[1;32m$PACKAGE\e[0m"
echo -e "\e[33m⚠️  Logs anteriores foram ignorados (Modo: From Now On)\e[0m"
echo "--------------------------------------------------------"

# Tenta capturar o PID para um monitoramento mais preciso
PID=$(adb -s "$DEVICE" shell pidof -s $PACKAGE)
if [ ! -z "$PID" ]; then
    echo -e "\e[32m✅ Aplicativo detectado! (PID: $PID)\e[0m"
else
    echo -e "\e[35m⏳ Aguardando abertura do aplicativo...\e[0m"
fi

# Executa o logcat
# -T 1 -> Ignora tudo o que aconteceu antes do comando ser disparado
# -v color -> Garante que o ADB não mate as cores nativas
adb -s "$DEVICE" logcat -T 1 -v color $PACKAGE:I *:E ActivityTaskManager:I AndroidRuntime:V | grep --line-buffered -E "$PACKAGE|FATAL EXCEPTION|AndroidRuntime|Displayed" | 
sed \
    -e "s/\(E\/.*\)/\x1b[31m\1\x1b[0m/g" \
    -e "s/\(F\/.*\)/\x1b[31;1m\1\x1b[0m/g" \
    -e "s/\(.*FATAL EXCEPTION.*\)/\x1b[41;37;1m\1\x1b[0m/g" \
    -e "s/\(.*Displayed $PACKAGE.*\)/\x1b[36;1m✨ APLICATIVO ABERTO COM SUCESSO: \1\x1b[0m/g" \
    -e "s/\($PACKAGE\)/\x1b[32m\1\x1b[0m/g"