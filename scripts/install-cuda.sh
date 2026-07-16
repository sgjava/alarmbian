#!/bin/bash
#
# Created on July 16, 2026
#
# @author: sgoldsmith
#
# Dynamically detects the NVIDIA driver capability and installs the latest
# available CUDA Toolkit (e.g., 13.3) on Ubuntu 26.04.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -euo pipefail

# --- Color Constants for Output ---
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly NC='\033[0;0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# --- Pre-flight Checks ---
if [[ $EUID -ne 0 ]]; then
   log_error "This script must be run as root. Please use: sudo ./install-cuda.sh"
   exit 1
fi

log_info "Starting Driver-Aware CUDA Toolkit installation for Ubuntu 26.04..."

# 1. Verify nvidia-smi
if ! command -v nvidia-smi &> /dev/null; then
    log_error "nvidia-smi not found. Install nvidia-driver-595-open first."
    exit 1
fi

# 2. Dependencies
log_info "Updating system dependencies..."
apt-get update
apt-get install -y build-essential linux-headers-"$(uname -r)" wget software-properties-common

# 3. Configure Repository Pinning (ubuntu2604)
log_info "Setting up repository pinning for Ubuntu 26.04..."
mkdir -p /etc/apt/preferences.d
wget -qO /etc/apt/preferences.d/cuda-repository-pin-600 \
    https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2604/x86_64/cuda-ubuntu2604.pin

# 4. Install Keyring
log_info "Installing NVIDIA repository keyring..."
readonly KEYRING_FILE="cuda-keyring_1.1-1_all.deb"
wget -q https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2604/x86_64/${KEYRING_FILE}
dpkg -i ${KEYRING_FILE}
rm -f ${KEYRING_FILE}
apt-get update

# 5. Dynamic Toolkit Selection (Targets 13.3 or latest available)
log_info "Searching for available CUDA Toolkit 13.x packages..."
TARGET_PACKAGE=$(apt-cache search cuda-toolkit-13 | grep -o "cuda-toolkit-13-[0-9]" | sort -V | tail -n 1)

if [[ -z "${TARGET_PACKAGE}" ]]; then
    log_warn "Exact version match not found, defaulting to meta-package..."
    TARGET_PACKAGE="cuda-toolkit-13"
fi

log_info "Installing ${TARGET_PACKAGE}..."
apt-get install -y "${TARGET_PACKAGE}"

# 6. Path Initialization
log_info "Configuring environment paths..."
readonly PROFILE_SCRIPT="/etc/profile.d/cuda.sh"
readonly LD_SO_CONF_DIR="/etc/ld.so.conf.d/cuda.conf"
readonly ACTIVE_CUDA=$(find /usr/local/ -maxdepth 1 -type d -name "cuda-13*" | sort -V | tail -n 1)

cat << EOF > "${PROFILE_SCRIPT}"
export CUDA_HOME=${ACTIVE_CUDA}
export PATH=${ACTIVE_CUDA}/bin:\$PATH
EOF
chmod +x "${PROFILE_SCRIPT}"

echo "${ACTIVE_CUDA}/lib64" > "${LD_SO_CONF_DIR}"
ldconfig

log_info "========================================================"
log_info " CUDA Toolkit Setup Completed: ${ACTIVE_CUDA} "
log_info "========================================================"
