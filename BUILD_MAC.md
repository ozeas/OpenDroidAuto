# Build no macOS (Apple Silicon / M1)

Este projeto é um app **Android Auto head-unit** para a central "Honda Connect"
(Android 4.0.4) de um Honda HR-V EXL 2018. O build produz um APK 32-bit
(`armeabi-v7a`) com código nativo C++ (ndk-build) e pinos de toolchain antigos.

## Pré-requisitos

| Componente | Versão | Obs. |
|---|---|---|
| JDK | **17** (Temurin) | AGP 8.7.2 exige Java 17 |
| Android SDK | `platforms;android-34` + `build-tools` + `cmdline-tools` | — |
| Android NDK | **`17.2.4988734` (r17)** | `APP_PLATFORM := android-15` exige r17 |
| Gradle | 8.11 (via wrapper) | baixado automaticamente |
| Rosetta 2 | — | NDK r17 só tem binários host **x86_64** |

> **Por que NDK r17?** O `Application.mk` usa `APP_PLATFORM := android-15`
> (API 15 = Android 4.0.3, retirado a partir do NDK r18). r17 é a última versão
> que suporta API 15.

## 1. Instalar Rosetta 2 (obrigatório no M1/M2)

```sh
softwareupdate --install-rosetta --agree-to-license
```

O toolchain do NDK r17 (clang, as, ld, arm-linux-androideabi-*) são binários
x86_64 e rodam por tradução via Rosetta 2.

## 2. Instalar JDK 17

```sh
brew install --cask temurin@17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## 3. Instalar Android SDK (cmdline-tools) + NDK r17

```sh
# baixar cmdline-tools (ou instalar via Android Studio)
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
# baixe o zip "commandlinetools-mac-..." de developer.android.com e extraia p/ "latest"

export ANDROID_HOME=$HOME/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# aceitar licenças
yes | sdkmanager --licenses

# instalar platform + build-tools
sdkmanager "platforms;android-34" "build-tools;34.0.0"

# instalar NDK r17
sdkmanager "ndk;17.2.4988734"
```

Verifique se ficou em `$ANDROID_HOME/ndk/17.2.4988734`.

## 4. Configurar `local.properties`

Na raiz do repositório (`OpenDroidAuto/`), crie `local.properties`:

```properties
sdk.dir=/Users/SEU_USUARIO/android-sdk
```

(O `ndkVersion` já está fixado em `17.2.4988734` no `build.gradle`.)

## 5. Build

```sh
cd OpenDroidAuto
chmod +x gradlew
./gradlew :common:assemble
./gradlew assembleRelease
```

APK em: `app/build/outputs/apk/release/HondaAppCenter_A1.apk`

## Possíveis problemas em Apple Silicon

- **NDK r17 vs macOS recente**: binários antigos podem falhar em macOS Big Sur+
  (11+). Rode o processo sob Rosetta:
  ```sh
  arch -x86_64 /usr/libexec/java_home -v 17  # obter o java x86_64
  ```
  Em último caso, use um JDK **x86_64** (não arm64) para que todo o toolchain
  rode no mesmo modo Rosetta:
  ```sh
  arch -x86_64 /bin/zsh   # shell x86_64, depois repita o passo 5
  ```
- **`APP_SHORT_COMMANDS := true`** já mitiga limite de tamanho de linha de comando
  no macOS (segurança deixe ligado).
- Erros de `libncurses`/`libtinfo`: no macOS o r17 não depende de `libtinfo5`
  (isso é específico de Linux/Ubuntu, resolvido no CI com `sudo apt-get install libtinfo5`).

## Alternativa recomendada: build via CI (GitHub Actions)

O fork já inclui `.github/workflows/main.yml`, que compila em **ubuntu-22.04**
com o toolchain exato (JDK 17 + NDK r17 + `libtinfo5`) e publica o APK como
release. Isso evita todos os problemas de NDK-r17 em Apple Silicon:

```sh
git tag v2.5.3-test
git push origin v2.5.3-test
```

O workflow dispara em `push` de tags e publica `HondaAppCenter_A1.apk` em
Releases do fork.
