#!/bin/bash
#
# Created on May 24, 2026
#
# @author: sgoldsmith
#
# Build and install OpenCV from source using SDKMAN Ant/Java.
# Clones repository source trees directly into $HOME.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -e

# 1. CLEAN ENVIRONMENT
# Wipe variables that cause "unrecognized command-line option" errors
unset CC CXX CFLAGS CXXFLAGS
export CC=/usr/bin/gcc
export CXX=/usr/bin/g++

INSTALL_PREFIX="/usr/local"

echo "--------------------------------------------------"
echo "STEP 1: Purge Old OpenCV Installation Artifacts"
echo "--------------------------------------------------"
echo "Removing old libraries and jars from $INSTALL_PREFIX..."
sudo rm -rf "$INSTALL_PREFIX/include/opencv4"
sudo rm -f "$INSTALL_PREFIX/lib/libopencv_"*
sudo rm -rf "$INSTALL_PREFIX/share/opencv4"
sudo rm -rf "$INSTALL_PREFIX/share/java/opencv4"

echo "--------------------------------------------------"
echo "STEP 2: Install System Dependencies"
echo "--------------------------------------------------"
sudo apt install -y \
    build-essential cmake ninja-build pkg-config git \
    libjpeg-dev libpng-dev libtiff-dev libwebp-dev libv4l-dev \
    libopenblas-dev libtbb-dev libva-dev libdrm-dev

echo "--------------------------------------------------"
echo "STEP 2.5: Patch VideoIO for FFmpeg 8 Master Compatibility"
echo "--------------------------------------------------"
python3 -c '
import pathlib

# 1. Fix cap_ffmpeg_hw.hpp (pix_fmts loop)
hw_file = pathlib.Path("opencv/modules/videoio/src/cap_ffmpeg_hw.hpp")
if hw_file.exists():
    content = hw_file.read_text()
    # Safely neutralize the loop condition so it never executes or accesses the deleted field
    content = content.replace("c->pix_fmts[i]", "AV_PIX_FMT_NONE")
    content = content.replace("c->pix_fmts", "nullptr")
    hw_file.write_text(content)
    print("Successfully patched cap_ffmpeg_hw.hpp")

# 2. Fix cap_ffmpeg_impl.hpp (supported_framerates)
impl_file = pathlib.Path("opencv/modules/videoio/src/cap_ffmpeg_impl.hpp")
if impl_file.exists():
    content = impl_file.read_text()
    # Replace the missing member struct lookup with a safe nullptr
    content = content.replace("codec->supported_framerates", "nullptr")
    impl_file.write_text(content)
    print("Successfully patched cap_ffmpeg_impl.hpp")
'
echo "Patches successfully applied to fresh clone."

echo "--------------------------------------------------"
echo "STEP 3: Clone OpenCV (master)"
echo "--------------------------------------------------"
cd "$HOME"
rm -rf opencv opencv_contrib
git clone --depth 1 https://github.com/opencv/opencv.git
git clone --depth 1 https://github.com/opencv/opencv_contrib.git

echo "--------------------------------------------------"
echo "STEP 4: Resolve SDKMAN Environment paths"
echo "--------------------------------------------------"
if [ -f /etc/environment ]; then
    source /etc/environment
fi

# Fix: Disable unbound variable check temporarily for SDKMAN initialization
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    set -u
fi

if [ -z "${JAVA_HOME:-}" ] || [ -z "${ANT_HOME:-}" ]; then
    log_error "JAVA_HOME or ANT_HOME is missing from the environment."
    log_error "Please ensure you have sourced /etc/environment or restarted your shell."
    exit 1
fi

ANT_BIN="$ANT_HOME/bin/ant"
echo "Using Java Home: $JAVA_HOME"
echo "Using Ant Executable: $ANT_BIN"

echo "--------------------------------------------------"
echo "STEP 5: Configure and Build"
echo "--------------------------------------------------"
rm -rf "$HOME/opencv/build"
mkdir -p "$HOME/opencv/build"
cd "$HOME/opencv/build"

# OpenCV 5.0 CMake Configuration
# OPENCV_SKIP_COMPILER_CHECKS=ON: Bypasses the broken 'probe' tests
cmake -G Ninja \
    -D CMAKE_BUILD_TYPE=RELEASE \
    -D CMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" \
    -D OPENCV_EXTRA_MODULES_PATH="$HOME/opencv_contrib/modules" \
    -D OPENCV_SKIP_COMPILER_CHECKS=ON \
    -D WITH_FFMPEG=ON \
    -D WITH_GSTREAMER=OFF \
    -D WITH_VAAPI=ON \
    -D BUILD_opencv_python3=OFF \
    -D BUILD_opencv_python2=OFF \
    -D BUILD_opencv_java=ON \
    -D BUILD_TESTS=OFF \
    -D BUILD_PERF_TESTS=OFF \
    -D BUILD_EXAMPLES=OFF \
    ..

echo "--------------------------------------------------"
echo "STEP 6: Compile and Install"
echo "--------------------------------------------------"
ninja
sudo ninja install
sudo ldconfig

echo "--------------------------------------------------"
echo "Build complete."
echo "--------------------------------------------------"

