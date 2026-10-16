# Launcher Llara

Launcher leve e rápido para Android 5.0+ (API 21), feito com APIs nativas — zero dependências.

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

## Assinatura release

O APK de debug assina sozinho. Para publicar na Play, **não commite chaves**:

1. Gere o keystore localmente (fora do repo):
   `keytool -genkeypair -alias llara -keyalg RSA -keysize 2048 -validity 10000 -keystore ~/llara-release.jks`
2. Crie `keystore.properties` na raiz (ignorado pelo git — veja `.gitignore`):
   ```properties
   storeFile=/home/seu-usuario/llara-release.jks
   storePassword=***
   keyAlias=llara
   keyPassword=***
   ```
3. Aponte o `signingConfig` de release para ele.

## Estrutura

- `app/src/main/java/com/launcher/llara/` — `activity/`, `model/`, `receiver/`, `util/`, `view/`, `widget/`
- `app/src/main/res/` — layouts, drawables, `mipmap-*/`, `values/`
- `deploy.sh` / `debug_waydroid.sh` / `logs.sh` / `backup.sh` — automação local
