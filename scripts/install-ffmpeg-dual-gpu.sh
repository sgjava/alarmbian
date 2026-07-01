#!/bin/bash
#
# Created on July 1, 2026
#
# @author: sgoldsmith
#
# Compiles an optimized, full-featured FFmpeg binary featuring simultaneous hardware acceleration for
# NVIDIA CUDA/NVDEC and Intel Quick Sync Video (QSV / VAAPI / libvpl).
# Pins toolchain precisely to CUDA 13.2 for architecture safety.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -euo pipefail

# --- Color Constants ---
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly NC='\033[0;0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# --- Pre-flight Checks ---
if [[ $EUID -ne 0 ]]; then
   log_error "This script must be run as root. Use: sudo ./install-ffmpeg-dual-gpu.sh"
   exit 1
fi

# Determine the actual invoking non-root user for group verification
if [[ -n "${SUDO_USER:-}" ]]; then
    readonly REAL_USER="${SUDO_USER}"
else
    readonly REAL_USER="${USER}"
fi

# 1. Force the Driver-Matched CUDA 13.2 Path Verification
log_info "Locating driver-compatible CUDA toolkit environment..."
if [[ -d "/usr/local/cuda-13.2" ]]; then
    readonly CUDA_PATH="/usr/local/cuda-13.2"
elif [[ -d "/usr/local/cuda" ]]; then
    readonly CUDA_PATH="/usr/local/cuda"
else
    readonly CUDA_PATH=$(find /usr/local/ -maxdepth 1 -type d -name "cuda-13.*" 2>/dev/null | sort -V | head -n 1)
fi

if [[ ! -d "${CUDA_PATH:-}" ]]; then
    log_error "CUDA toolkit path could not be resolved under /usr/local. Please verify your install stack."
    exit 1
fi
log_info "Targeting CUDA home path: ${CUDA_PATH}"

# 2. Update System Index and Install Codec Dependencies
log_info "Updating package metadata and synchronizing dual-GPU codec libraries..."
apt-get update
apt-get install -y \
    build-essential \
    pkg-config \
    git \
    yasm \
    nasm \
    libmfx-gen1.2 \    
    libpci-dev \
    libnuma-dev \
    libx264-dev \
    libx265-dev \
    libvpx-dev \
    libmp3lame-dev \
    libopus-dev \
    libva-dev \
    libvpl-dev \
    intel-media-va-driver-non-free \
    va-driver-all \
    vainfo

# 3. Secure User Group Access to Graphics Nodes
log_info "Ensuring user '${REAL_USER}' is mapped to hardware rendering groups..."
usermod -aG render "${REAL_USER}"
usermod -aG video "${REAL_USER}"

# 4. Establish Temporary Compilation Workspace Area
readonly WORK_DIR="/tmp/ffmpeg_dual_gpu_build"
log_info "Initializing volatile build workspace directory: ${WORK_DIR}"
rm -rf "${WORK_DIR}"
mkdir -p "${WORK_DIR}"
cd "${WORK_DIR}"

# 5. Pull and Inject NVIDIA Hardware Abstraction Interface Headers
log_info "Cloning and tracking matching NVIDIA Codec Headers version n12.2.72.0..."
git clone https://git.videolan.org/git/ffmpeg/nv-codec-headers.git
cd nv-codec-headers
git checkout n12.2.72.0
make
make install PREFIX=/usr/local
cd "${WORK_DIR}"

# 6. Clone and Isolate FFmpeg Main Development Tree
log_info "Retrieving fresh FFmpeg deployment manifest repository source..."
git clone --depth 1 https://github.com/FFmpeg/FFmpeg.git ffmpeg-source
cd ffmpeg-source

# 7. Export Environment Toolchain Variables for Cross-Hardware Linking
log_info "Setting temporary environment context search hooks..."
export PATH="${CUDA_PATH}/bin:${PATH}"
export PKG_CONFIG_PATH="/usr/local/lib/pkgconfig:/usr/local/share/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="${CUDA_PATH}/lib64:${LD_LIBRARY_PATH:-}"
export CPATH="${CUDA_PATH}/include:/usr/local/include:${CPATH:-}"

# 8. Execute Multi-GPU Build Configuration Step
log_info "Configuring compilation matrix with cross-GPU architecture flags..."
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
  --enable-vaapi \
  --enable-libvpl \
  --extra-cflags="-I${CUDA_PATH}/include -I/usr/local/include -O3" \
  --extra-ldflags="-L${CUDA_PATH}/lib64 -L/usr/local/lib"

# 9. Compile and Install Across System Binary Nodes
log_info "Executing multi-threaded translation mapping using $(nproc) processing cores..."
make -j"$(nproc)"

log_info "Installing new active binaries to standard target environment path..."
make install

# 10. Refresh and Enforce Global Dynamic Linker Configurations
log_info "Refreshing dynamic runtime bindings cache..."
echo "/usr/local/lib" > /etc/ld.so.conf.d/ffmpeg-dual-gpu.conf
ldconfig

# --- Post-Install Cleanup Routine ---
log_info "Scrubbing workspace build footprints from system memory layers..."
rm -rf "${WORK_DIR}"

log_info "=========================================================================="
log_info " Dual-GPU Hybrid FFmpeg Suite Installation Finished!                      "
log_info "=========================================================================="
log_info "Binary location verified: $(command -v ffmpeg)"
log_info "Active Version Verification: $(ffmpeg -version | head -n 1)"
log_info "=========================================================================="
log_info "NOTE: To test headless QSV capabilities immediately without logging out, execute:"
echo -e "${YELLOW}newgrp render && newgrp video && source /etc/profile.d/intel-media.sh${NC}"
