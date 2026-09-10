# Build local sem sudo (Linux)

Instruções para compilar o OpenDroidAuto **nesta máquina Linux (Arch/Omarchy)** sem
nenhuma permissão de root (`sudo`). Tudo é instalado em diretórios do usuário.

> Validado em 2026-09: build `assembleRelease` concluído com sucesso, gerando
> `app/build/outputs/apk/release/HondaAppCenter_A1.apk`.

## Componentes instalados (já provisionados)

| Componente | Versão | Localização |
|---|---|---|
| JDK | Temurin **17.0.20.1** | `~/.local/share/mise/installs/java/temurin-17.0.20+101` |
| Android SDK | platform 34 + build-tools 34.0.0 | `~/android-sdk` |
| Android NDK | **17.2.4988734** (r17) | `~/android-sdk/ndk/17.2.4988734` |
| libtinfo.so.5 (ncurses5) | do Ubuntu `libtinfo5_6.3` | `~/android-ndk-libs/libtinfo.so.5.9` |

## Por que libtinfo.so.5?

O NDK r17 (clang/llvm) foi linkado contra `libtinfo.so.5` (ncurses5), que não
existe em distros modernas (só `libtinfo.so.6`). Sem ela, o clang falha e o build
trava em `aasdk:configureNdkBuildRelease`. A lib é baixada do pacote Ubuntu e
exposta via `LD_LIBRARY_PATH` (sem instalar nada no sistema).

```sh
mkdir -p ~/android-ndk-libs
cd /tmp
curl -sSL -o libtinfo5.deb \
  "http://archive.ubuntu.com/ubuntu/pool/universe/n/ncurses/libtinfo5_6.3-2ubuntu0.3_amd64.deb"
ar x libtinfo5.deb data.tar.*
tar -xf data.tar.* -C /tmp/tin
cp /tmp/tin/lib/x86_64-linux-gnu/libtinfo.so.5.9 ~/android-ndk-libs/
ln -sf ~/android-ndk-libs/libtinfo.so.5.9 ~/android-ndk-libs/libtinfo.so.5
```

## Passo a passo de instalação (reproduzível)

### 1. JDK 17 (via mise, sem sudo)

```sh
mise install java@temurin-17
JAVA_HOME=$(mise where java)
```

### 2. Android SDK

```sh
mkdir -p ~/android-sdk/cmdline-tools
cd /tmp
curl -sSL -o cmdtools.zip \
  "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q cmdtools.zip -d ~/android-sdk/cmdline-tools/
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

export ANDROID_HOME=$HOME/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" \
  "platforms;android-34" "build-tools;34.0.0" "ndk;17.2.4988734"
```

### 3. libtinfo.so.5 (seção acima)

### 4. local.properties

Na raiz do repositório:

```properties
sdk.dir=/home/SEU_USUARIO/android-sdk
```

## Build

```sh
cd ~/Documents/Projects/Car/hrv-app-android/OpenDroidAuto

export JAVA_HOME="$HOME/.local/share/mise/installs/java/temurin-17.0.20+101"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export LD_LIBRARY_PATH="$HOME/android-ndk-libs"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew assembleRelease
```

Ou simplesmente: `~/Documents/Projects/Car/hrv-app-android/build.sh`

APK em `app/build/outputs/apk/release/HondaAppCenter_A1.apk`.

## Notas

- `compileSdk 34`, `minSdk 15`, `targetSdk 15`, ABI `armeabi-v7a` — inalterados
  (a central Honda roda Android 4.0.4).
- O `prefab = true` no módulo `aasdk` é **obrigatório** (importa `prefab/common`);
  não desabilitar.
- NDK r17 + AGP 8.7.2 funciona, mas gera warnings `CXX5107` (prefab até r21) —
  inofensivos.
- O `local.properties` é ignorado pelo git.
