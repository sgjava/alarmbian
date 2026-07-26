#!/bin/bash
#
# Created on July 1, 2026
#
# @author: sgoldsmith
#
# Build and install OpenCV from source using SDKMAN Ant/Java and CUDA 13.x on Ubuntu 26.04.
# Clones repository source trees directly into $HOME.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -e

# --------------------------------------------------
# Logging Helper Functions
# --------------------------------------------------
log_info() {
    echo -e "\e[32m[INFO]\e[0m $1"
}

log_warn() {
    echo -e "\e[33m[WARN]\e[0m $1"
}

log_error() {
    echo -e "\e[31m[ERROR]\e[0m $1"
}

# 1. CLEAN ENVIRONMENT
# Wipe variables that cause "unrecognized command-line option" errors
unset CC CXX CFLAGS CXXFLAGS
export CC=/usr/bin/gcc
export CXX=/usr/bin/g++

INSTALL_PREFIX="/usr/local"

log_info "--------------------------------------------------"
log_info "STEP 1: Purge Old OpenCV Installation Artifacts"
log_info "--------------------------------------------------"
log_info "Removing old libraries and jars from $INSTALL_PREFIX..."
sudo rm -rf "$INSTALL_PREFIX/include/opencv4"
sudo rm -f "$INSTALL_PREFIX/lib/libopencv_"*
sudo rm -rf "$INSTALL_PREFIX/share/opencv4"
sudo rm -rf "$INSTALL_PREFIX/share/java/opencv4"

log_info "--------------------------------------------------"
log_info "STEP 2: Install System Dependencies"
log_info "--------------------------------------------------"
sudo apt update
sudo apt install -y \
    build-essential cmake ninja-build pkg-config git \
    libjpeg-dev libpng-dev libtiff-dev libwebp-dev libv4l-dev \
    libopenblas-dev libtbb-dev libprotobuf-dev protobuf-compiler

log_info "--------------------------------------------------"
log_info "STEP 3: Clone OpenCV Source Repositories"
log_info "--------------------------------------------------"
cd "$HOME"
rm -rf opencv opencv_contrib
git clone --depth 1 https://github.com/opencv/opencv.git
git clone --depth 1 https://github.com/opencv/opencv_contrib.git

log_info "--------------------------------------------------"
log_info "STEP 3.5: Patch VideoIO for FFmpeg 8 Master Compatibility"
log_info "--------------------------------------------------"
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
log_info "Patches successfully applied to fresh clone."

log_info "--------------------------------------------------"
log_info "STEP 4: Resolve SDKMAN Environment paths"
log_info "--------------------------------------------------"
if [ -f /etc/environment ]; then
    source /etc/environment
fi

# Disable unbound variable check temporarily for SDKMAN initialization
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
log_info "Using Java Home: $JAVA_HOME"
log_info "Using Ant Executable: $ANT_BIN"

log_info "--------------------------------------------------"
log_info "STEP 5: Configure CMake for Ninja Build (CUDA 13.x)"
log_info "--------------------------------------------------"
rm -rf "$HOME/opencv/build"
mkdir -p "$HOME/opencv/build"
cd "$HOME/opencv/build"

# OpenCV CMake Configuration
cmake -G Ninja \
    -D CMAKE_BUILD_TYPE=RELEASE \
    -D CMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" \
    -D OPENCV_EXTRA_MODULES_PATH="$HOME/opencv_contrib/modules" \
    -D OPENCV_SKIP_COMPILER_CHECKS=ON \
    -D WITH_CUDA=ON \
    -D CUDA_ARCH_BIN=75 \
    -D CUDA_ARCH_PTX=75 \
    -D BUILD_opencv_cudacodec=ON \
    -D WITH_CUDNN=ON \
    -D WITH_CUBLAS=ON \
    -D ENABLE_FAST_MATH=ON \
    -D CUDA_FAST_MATH=ON \
    -D WITH_FFMPEG=ON \
    -D WITH_GSTREAMER=OFF \
    -D BUILD_opencv_python3=OFF \
    -D BUILD_opencv_python2=OFF \
    -D BUILD_opencv_java=ON \
    -D ANT_EXECUTABLE="$ANT_BIN" \
    -D JAVA_HOME="$JAVA_HOME" \
    -D BUILD_opencv_dnn=ON \
    -D OPENCV_DNN_CUDA=ON \
    -D CMAKE_CXX_STANDARD=17 \
    -D BUILD_TESTS=OFF \
    -D BUILD_PERF_TESTS=OFF \
    -D BUILD_EXAMPLES=OFF \
    -D CMAKE_C_FLAGS="-O3 -w" \
    -D CMAKE_CXX_FLAGS="-O3 -w" \
    ..

log_info "--------------------------------------------------"
log_info "STEP 6: Compile and Install"
log_info "--------------------------------------------------"
ninja
sudo ninja install
sudo ldconfig

log_info "--------------------------------------------------"
log_info "STEP 7: Create Legacy JNI Symlinks for Spring Boot"
log_info "--------------------------------------------------"
# Dynamically locate the built libopencv_java*.so file and map libopencv_java4140.so to it
BUILT_JAVA_SO=$(ls "$HOME/opencv/build/lib"/libopencv_java*.so 2>/dev/null | head -n 1)

if [ -n "$BUILT_JAVA_SO" ]; then
    SO_NAME=$(basename "$BUILT_JAVA_SO")
    log_info "Found compiled Java library: $SO_NAME"
    
    # 1. Local build tree symlink
    cd "$HOME/opencv/build/lib"
    ln -sf "$SO_NAME" libopencv_java4140.so
    
    # 2. System install directory symlink
    if [ -d "$INSTALL_PREFIX/share/java/opencv4" ]; then
        sudo ln -sf "$INSTALL_PREFIX/share/java/opencv4/$SO_NAME" "$INSTALL_PREFIX/share/java/opencv4/libopencv_java4140.so"
    fi
    
    sudo ldconfig
    log_info "Symlinked libopencv_java4140.so -> $SO_NAME successfully."
else
    log_warn "No libopencv_java*.so found in build directory!"
fi

log_info "--------------------------------------------------"
log_info "Build complete."
log_info "--------------------------------------------------"
