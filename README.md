# Larla Launcher

Lançador para Android 100% nativo e sem dependências: nenhum SDK, biblioteca ou framework de terceiros — só APIs do próprio Android, do Android 5.0+ (API 21) em diante.

- Gaveta de apps com busca, pastas e ocultar apps
- Dock com páginas e gestos por ícone
- Área de trabalho com atalhos, pastas, widgets e cantos com snap
- Ícones por superfície (gaveta, dock, área) + modo unificado
- Gestos globais e por ícone (inclui abrir apps)
- APK release ~280 KB

## Build

Pré-requisito: JDK 17 (o AGP quebra com Java 27 no `jlink` do android-35).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleRelease
# APK em: app/build/outputs/apk/release/app-release.apk
```

Ou use `./deploy.sh` (compila, conecta no Waydroid/USB e instala).

## Estrutura

- `app/src/main/java/com/launcher/larla/` — `activity/`, `model/`, `receiver/`, `util/`, `view/`, `widget/`
- `app/src/main/res/` — layouts, drawables, `mipmap-*/`, `values/`
- `deploy.sh` / `debug_waydroid.sh` / `logs.sh` / `backup.sh` — automação local
