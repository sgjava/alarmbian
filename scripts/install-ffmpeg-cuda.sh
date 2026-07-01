#!/bin/bash
#
# Created on July 1, 2026
#
# @author: sgoldsmith
#
# Compiles a full-featured FFmpeg with libx264, libx265, and CUDA.
# Pins build toolchain to CUDA 13.2 to match the 595 driver capability.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -euo pipefail

# --- Color Constants ---
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly NC='\033[0;0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

if [[ $EUID -ne 0 ]]; then
   log_error "This script must be run as root. Use: sudo ./install-ffmpeg-cuda.sh"
   exit 1
fi

# 1. Force the Driver-Matched CUDA 13.2 Path
log_info "Locating driver-compatible CUDA toolkit..."
if [[ -d "/usr/local/cuda-13.2" ]]; then
    readonly CUDA_PATH="/usr/local/cuda-13.2"
elif [[ -d "/usr/local/cuda" ]]; then
    readonly CUDA_PATH="/usr/local/cuda"
else
    readonly CUDA_PATH=$(find /usr/local/ -maxdepth 1 -type d -name "cuda-13.*" 2>/dev/null | sort -V | head -n 1)
fi

if [[ ! -d "${CUDA_PATH}" ]]; then
    log_error "Compatible CUDA Toolkit installation not detected in /usr/local/."
    exit 1
fi
log_info "Building against CUDA installation: ${CUDA_PATH}"

# 2. Install System Codec Libraries & Build Tools
log_info "Installing development libraries for H.264, H.265, and build tools..."
apt-get update
apt-get install -y \
    build-essential \
    pkg-config \
    yasm \
    nasm \
    git \
    wget \
    libpci-dev \
    libx264-dev \
    libx265-dev \
    libnuma-dev \
    libvpx-dev \
    libmp3lame-dev \
    libopus-dev

# 3. Setup Workspace
readonly WORK_DIR="/tmp/ffmpeg_cuda_build"
rm -rf "${WORK_DIR}"
mkdir -p "${WORK_DIR}"
cd "${WORK_DIR}"

# 4. Fetch and Install Stable NV Codec Headers (SDK 12.2 for 595 drivers)
log_info "Installing NVIDIA codec headers (SDK 12.2 stable)..."
git clone https://git.videolan.org/git/ffmpeg/nv-codec-headers.git
cd nv-codec-headers
git checkout n12.2.72.0
make
make install PREFIX=/usr/local
cd "${WORK_DIR}"

# 5. Clone and Configure FFmpeg Master
log_info "Cloning FFmpeg source..."
git clone --depth 1 https://github.com/FFmpeg/FFmpeg.git ffmpeg-source
cd ffmpeg-source

log_info "Configuring compilation matrix with precise GPU architecture flags..."
export PATH="${CUDA_PATH}/bin:${PATH}"
export PKG_CONFIG_PATH="/usr/local/lib/pkgconfig:/usr/local/share/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="${CUDA_PATH}/lib64:${LD_LIBRARY_PATH:-}"
export CPATH="${CUDA_PATH}/include:/usr/local/include:${CPATH:-}"

./configure \
  --prefix=/usr/local \
  --enable-shared \
  --disable-static \
  --enable-gpl \
  --enable-nonfree \
  --enable-libx264 \
  --enable-libx265 \
  --enable-libvpx \
  --enable-libmp3lame \
  --enable-libopus \
  --enable-cuda-nvcc \
  --nvccflags="-arch=sm_75" \
  --extra-cflags="-I${CUDA_PATH}/include -I/usr/local/include -O3" \
  --extra-ldflags="-L${CUDA_PATH}/lib64 -L/usr/local/lib"

# 6. Compile and Install
log_info "Compiling pipeline..."
make -j$(nproc)
make install

# 7. Refresh Linker Cache
log_info "Refreshing dynamic links..."
echo "/usr/local/lib" > /etc/ld.so.conf.d/ffmpeg-cuda.conf
ldconfig

log_info "========================================================"
log_info " FFmpeg CUDA Build Successfully Installed! "
log_info "========================================================"

# Clean up workspace
cd /tmp
rm -rf "${WORK_DIR}"
