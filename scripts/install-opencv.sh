#!/bin/bash
#
# Created on May 23, 2026
#
# @author: sgoldsmith
#
# Build and install OpenCV from source using SDKMAN Ant/Java and system FFmpeg.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -e

OPENCV_VERSION="4.11.0"
BUILD_DIR="$HOME/opencv_build"
INSTALL_PREFIX="/usr/local"

echo "--------------------------------------------------"
echo "STEP 1: Install Native Subsystem Dependencies"
echo "--------------------------------------------------"
sudo apt update
sudo apt install -y \
    build-essential \
    cmake \
    ninja-build \
    pkg-config \
    git \
    libjpeg-dev \
    libpng-dev \
    libtiff-dev \
    libv4l-dev \
    libxvidcore-dev \
    libx264-dev \
    libatlas-base-dev \
    gfortran

echo "--------------------------------------------------"
echo "STEP 2: Install FFmpeg Development Libraries"
echo "--------------------------------------------------"
sudo apt install -y \
    libavcodec-dev \
    libavformat-dev \
    libswscale-dev \
    libavutil-dev \
    libavfilter-dev \
    libswresample-dev

echo "--------------------------------------------------"
echo "STEP 3: Clone OpenCV Repositories"
echo "--------------------------------------------------"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

if [ ! -d "opencv" ]; then
    git clone --depth 1 --branch "$OPENCV_VERSION" https://github.com/opencv/opencv.git
else
    cd opencv && git fetch --tags && git checkout "$OPENCV_VERSION" && cd ..
fi

if [ ! -d "opencv_contrib" ]; then
    git clone --depth 1 --branch "$OPENCV_VERSION" https://github.com/opencv/opencv_contrib.git
else
    cd opencv_contrib && git fetch --tags && git checkout "$OPENCV_VERSION" && cd ..
fi

echo "--------------------------------------------------"
echo "STEP 4: Resolve SDKMAN Environment paths"
echo "--------------------------------------------------"
# Source environment variables if they haven't been picked up in the current shell
if [ -f /etc/environment ]; then
    source /etc/environment
fi
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# Verify core tooling from your install-java setup is active
if [ -z "$JAVA_HOME" ] || [ -z "$ANT_HOME" ]; then
    echo "ERROR: JAVA_HOME or ANT_HOME is missing from the environment."
    echo "Please ensure you have sourced /etc/environment or restarted your shell."
    exit 1
fi

ANT_BIN="$ANT_HOME/bin/ant"
echo "Using Java Home: $JAVA_HOME"
echo "Using Ant Executable: $ANT_BIN"

echo "--------------------------------------------------"
echo "STEP 5: Configure Build via CMake and Ninja"
echo "--------------------------------------------------"
mkdir -p "$BUILD_DIR/opencv/build"
cd "$BUILD_DIR/opencv/build"

cmake -G Ninja \
    -D CMAKE_BUILD_TYPE=RELEASE \
    -D CMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" \
    -D OPENCV_EXTRA_MODULES_PATH="$BUILD_DIR/opencv_contrib/modules" \
    -D CPU_BASELINE=NATIVE \
    -D WITH_ENABLE_EXTRA_CLEAN_COMPILE=ON \
    -D OPENCV_ENABLE_NONFREE=ON \
    -D WITH_PTHREADS_PF=ON \
    -D WITH_V4L=ON \
    -D WITH_FFMPEG=ON \
    -D BUILD_opencv_java=ON \
    -D ANT_EXECUTABLE="$ANT_BIN" \
    -D JAVA_AWT_INCLUDE_PATH="$JAVA_HOME/include" \
    -D JAVA_AWT_LIBRARY="$JAVA_HOME/lib" \
    -D JAVA_COMPILE_FLAGS="-source 25 -target 25" \
    -D WITH_GSTREAMER=OFF \
    -D WITH_GTK=OFF \
    -D WITH_QT=OFF \
    -D WITH_OPENGL=OFF \
    -D BUILD_EXAMPLES=OFF \
    -D BUILD_TESTS=OFF \
    -D BUILD_PERF_TESTS=OFF \
    -D BUILD_DOCS=OFF \
    -D BUILD_opencv_apps=OFF \
    -D BUILD_opencv_python2=OFF \
    -D BUILD_opencv_python3=OFF \
    -D CMAKE_C_FLAGS="-O3" \
    -D CMAKE_CXX_FLAGS="-O3" \
    ..

echo "--------------------------------------------------"
echo "STEP 6: Compile and Install"
echo "--------------------------------------------------"
ninja
sudo ninja install
sudo ldconfig

echo "--------------------------------------------------"
echo "STEP 7: Output Artifact Locations"
echo "--------------------------------------------------"
echo "OpenCV build complete!"
echo "--------------------------------------------------"
echo "Your Java build artifacts can be located here:"
ls -l "$BUILD_DIR/opencv/build/bin/opencv_"* 2>/dev/null || true
ls -l "$BUILD_DIR/opencv/build/lib/libopencv_java"* 2>/dev/null || true
echo "--------------------------------------------------"
