#!/bin/bash
#
# Created on May 24, 2026
#
# @author: sgoldsmith
#
# Build and install FFmpeg from source with shared libraries (.so),
# hardware acceleration auto-detection, and free/non-free codecs.
# Clones repository source trees directly into $HOME.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -e

WORKSPACE="$HOME/ffmpeg_shared_build"
INSTALL_PREFIX="/usr/local"

# Arrays to programmatically accumulate build flags and dependencies
CONF_FLAGS=()
DEPS=(
    build-essential git pkg-config nasm yasm libsdl2-dev
    libx264-dev libx265-dev libvpx-dev libmp3lame-dev 
    libopus-dev libfdk-aac-dev libaom-dev libnuma-dev libv4l-dev
)

echo "--------------------------------------------------"
echo "STEP 1: Hardware Acceleration Auto-Detection"
echo "--------------------------------------------------"

# 1. Test for NVIDIA CUDA Pipeline
if command -v nvidia-smi &> /dev/null || [ -d /usr/local/cuda ]; then
    echo "[DETECTED] NVIDIA hardware/drivers. Preparing NVENC/NVDEC/CUDA runtime infrastructure..."
    DETECT_CUDA=true
else
    DETECT_CUDA=false
fi

# 2. Test for Intel/AMD Open Source VAAPI Pipeline
if [ -c /dev/dri/renderD128 ] || [ -d /sys/module/i915 ] || [ -d /sys/module/amdgpu ]; then
    echo "[DETECTED] Intel or AMD DRM subsystems. Enabling VAAPI infrastructure..."
    DETECT_VAAPI=true
else
    DETECT_VAAPI=false
fi

# 3. Test for AMD Advanced Media Framework (AMF) Header Eligibility
if [ -d /sys/module/amdgpu ]; then
    echo "[DETECTED] AMD Radeon Graphics. Preparing AMF compilation headers..."
    DETECT_AMF=true
else
    DETECT_AMF=false
fi

echo "--------------------------------------------------"
echo "STEP 2: Resolve and Pull Prerequisites via APT"
echo "--------------------------------------------------"
if [ "$DETECT_VAAPI" = true ]; then
    DEPS+=(libva-dev libdrm-dev va-driver-all)
fi

sudo apt update
sudo apt install -y "${DEPS[@]}"

# Setup or reset a completely pristine workspace for multi-run safety
if [ -d "$WORKSPACE" ]; then
    echo "Existing build workspace detected. Purging old trees for multi-run safety..."
    rm -rf "$WORKSPACE"
fi
mkdir -p "$WORKSPACE"
cd "$WORKSPACE"

echo "--------------------------------------------------"
echo "STEP 3: Process External Vendor Headers"
echo "--------------------------------------------------"

# If CUDA is active, clone the required NV-Codec definitions
if [ "$DETECT_CUDA" = true ]; then
    echo "Cloning NVENC/NVDEC definitions..."
    git clone --depth 1 https://git.videolan.org/git/ffmpeg/nv-codec-headers.git
    cd nv-codec-headers
    sudo make install PREFIX="$INSTALL_PREFIX"
    cd "$WORKSPACE"
fi

# If AMF is active, install the open-source AMD AMF headers into system paths
if [ "$DETECT_AMF" = true ]; then
    echo "Cloning AMD Advanced Media Framework headers..."
    git clone --depth 1 https://github.com/GPUOpen-LibrariesAndSDKs/AMF.git
    sudo rm -rf /usr/local/include/AMF
    sudo mkdir -p /usr/local/include/AMF
    sudo cp -r AMF/amf/public/include/* /usr/local/include/AMF/
fi

echo "--------------------------------------------------"
echo "STEP 4: Clone Stable Production FFmpeg Source"
echo "--------------------------------------------------"
git clone --depth 1 --branch release/7.1 https://github.com/FFmpeg/FFmpeg.git ffmpeg_src
cd ffmpeg_src

echo "--------------------------------------------------"
echo "STEP 5: Dynamically Assemble Compilation Engine Flags"
echo "--------------------------------------------------"

# Core global flags for shared OpenCV linkages
# Replaced --enable-v4l2 with --enable-indev=v4l2 and --enable-outdev=v4l2
CONF_FLAGS+=(
    --prefix="$INSTALL_PREFIX"
    --enable-gpl
    --enable-nonfree
    --enable-shared
    --enable-pic
    --enable-indev=v4l2
    --enable-outdev=v4l2
)

# Standard Software Codec Blocks
CONF_FLAGS+=(
    --enable-libx264
    --enable-libx265
    --enable-libvpx
    --enable-libmp3lame
    --enable-libopus
    --enable-libfdk-aac
    --enable-libaom
)

# Append detected hardware pipelines
if [ "$DETECT_CUDA" = true ]; then
    CONF_FLAGS+=(
        --enable-cuda-nvcc
        --enable-libnpp
        --enable-ffnvcodec
        --enable-nvenc
        --enable-nvdec
    )
fi

if [ "$DETECT_VAAPI" = true ]; then
    CONF_FLAGS+=(--enable-vaapi)
fi

if [ "$DETECT_AMF" = true ]; then
    CONF_FLAGS+=(--enable-amf)
fi

echo "Configuring FFmpeg with these parameters:"
echo "${CONF_FLAGS[@]}"
echo "--------------------------------------------------"

./configure "${CONF_FLAGS[@]}"

echo "--------------------------------------------------"
echo "STEP 6: Compile, System Install, and Shared Library Bind"
echo "--------------------------------------------------"
make -j$(nproc)
sudo make install
sudo ldconfig

echo "--------------------------------------------------"
echo "STEP 7: Post-Installation Validation"
echo "--------------------------------------------------"
echo "Verifying standalone application deployment:"
ls -l "$INSTALL_PREFIX/bin/ffmpeg"
ls -l "$INSTALL_PREFIX/bin/ffplay"
ls -l "$INSTALL_PREFIX/bin/ffprobe"
echo "--------------------------------------------------"
echo "Available hardware acceleration mechanisms built:"
"$INSTALL_PREFIX/bin/ffmpeg" -hwaccels
echo "--------------------------------------------------"
