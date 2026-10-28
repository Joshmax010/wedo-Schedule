#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# wedo 构建脚本（轻量工具链版）
#
# 背景：机器上不再安装 Android Studio（占内存），只保留命令行工具链。
# 本脚本集中设置环境变量，避免每次手敲。
#
# 工具链位置由环境变量决定，不写死在本机路径：
#   JAVA_HOME        指向 JDK 17+（本机为 JDK 21）
#   ANDROID_SDK_ROOT 指向 Android SDK（需 platform 37 + build-tools 37）
#   GRADLE_USER_HOME 指向 Gradle 缓存目录（可选，默认 ~/.gradle）
#
# 也可用 WEDO_TOOLCHAIN_HOME 一次性指定工具链根目录，其下需有
# jdk-21 / android-sdk / gradle-home 三个子目录。
#
# 用法：
#   ./build.sh testDebugUnitTest          # 跑单测
#   ./build.sh lintDebug                  # 跑 lint
#   ./build.sh assembleDebug              # 出 Debug APK（默认仅 x86_64，给模拟器）
#   ./build.sh testDebugUnitTest lintDebug assembleDebug   # 一次跑完
#   ./build.sh clean                      # 清理
#
# ABI 策略：
#   默认只编 x86_64（模拟器），不再生成 arm64 / armeabi-v7a 包。
#   WEDO_ALL_ABIS=1 ./build.sh assembleDebug    # 临时出 3 个 ABI
#   WEDO_ABI=arm64-v8a ./build.sh assembleDebug # 临时指定单个 ABI
#
# PowerShell 等价写法：
#   $env:JAVA_HOME="<your-jdk-21>"
#   $env:GRADLE_USER_HOME="<your-gradle-home>"
#   .\gradlew.bat testDebugUnitTest
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# 工具链根：优先 WEDO_TOOLCHAIN_HOME，其次 $HOME/toolchain。
TOOLCHAIN_HOME="${WEDO_TOOLCHAIN_HOME:-$HOME/toolchain}"

export JAVA_HOME="${JAVA_HOME:-$TOOLCHAIN_HOME/jdk-21}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLCHAIN_HOME/gradle-home}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$TOOLCHAIN_HOME/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
# SDK 用户目录（adb key / 分析缓存等）：默认沿用 HOME，不写死到别处
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"

if [ ! -d "$JAVA_HOME" ]; then
  echo "错误：找不到 JAVA_HOME（$JAVA_HOME）。" >&2
  echo "请设置 JAVA_HOME 指向 JDK 17+，或设置 WEDO_TOOLCHAIN_HOME 指向含 jdk-21 的工具链根目录。" >&2
  exit 1
fi
if [ ! -d "$ANDROID_SDK_ROOT" ]; then
  echo "错误：找不到 ANDROID_SDK_ROOT（$ANDROID_SDK_ROOT）。" >&2
  echo "请设置 ANDROID_SDK_ROOT 指向 Android SDK（需 platform 37 + build-tools 37）。" >&2
  exit 1
fi

# 路径含空格时 Gradle 的 JVM 内存计算容易失准，这里给足
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx3g -Dfile.encoding=UTF-8}"

if [ $# -eq 0 ]; then
  echo "用法: ./build.sh <gradle-task> [...]" >&2
  echo "示例: ./build.sh testDebugUnitTest lintDebug assembleDebug" >&2
  exit 1
fi

# 定位仓库根（本脚本放在 <root>/scripts/build.sh）
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# 默认只出模拟器用的 x86_64；需要其他 ABI 时显式覆盖环境变量。
GRADLE_ARGS=("$@")
if [ "${WEDO_ALL_ABIS:-0}" != "1" ]; then
  GRADLE_ARGS+=("-Pwedo.abi=${WEDO_ABI:-x86_64}")
  ABI_LABEL="${WEDO_ABI:-x86_64}"
else
  ABI_LABEL="all"
fi

echo "→ 工程根:   $ROOT"
echo "→ JAVA:     $JAVA_HOME"
echo "→ SDK:      $ANDROID_SDK_ROOT"
echo "→ GRADLE:   $GRADLE_USER_HOME"
echo "→ ABI:      $ABI_LABEL"
echo "→ 任务:     $*"
echo

exec ./gradlew.bat "${GRADLE_ARGS[@]}"
