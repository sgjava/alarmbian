#!/bin/bash
#
# Created on July 1, 2026
#
# @author: sgoldsmith
#
# Dynamically detects the maximum supported CUDA version from the active NVIDIA driver and
# installs the matching targeted cuda-toolkit package on Ubuntu 26.04 safely.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -euo pipefail

# --- Color Constants for Output ---
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly NC='\033[0;0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# --- Pre-flight Checks ---
if [[ $EUID -ne 0 ]]; then
   log_error "This script must be run as root. Please use: sudo ./install-cuda.sh"
   exit 1
fi

log_info "Starting Driver-Aware CUDA Toolkit installation for Ubuntu 26.04..."

# 1. Verify nvidia-smi is present
if ! command -v nvidia-smi &> /dev/null; then
    log_error "nvidia-smi could not be found. Please ensure your nvidia-driver is installed."
    exit 1
fi

# Extract the maximum supported CUDA version (e.g., "13.2")
DETECTED_VERSION=$(nvidia-smi | grep -o "CUDA Version: [0-9]*\.[0-9]*" | head -n 1 | awk '{print $3}')

if [[ -z "${DETECTED_VERSION}" ]]; then
    log_error "Failed to parse supported CUDA Version from nvidia-smi output."
    exit 1
fi

# Convert dot format to apt package hyphen format (e.g., "13.2" -> "13-2")
PACKAGE_SUFFIX=$(echo "${DETECTED_VERSION}" | tr '.' '-')
TARGET_PACKAGE="cuda-toolkit-${PACKAGE_SUFFIX}"

log_info "Driver capability detected: CUDA ${DETECTED_VERSION}"
log_info "Targeted package: ${TARGET_PACKAGE}"

# 2. Dependencies
log_info "Updating package lists and checking for build dependencies..."
apt-get update
apt-get install -y build-essential linux-headers-"$(uname -r)" wget software-properties-common

# 3. Configure NVIDIA Repository Pinning for 26.04
log_info "Setting up repository preferences pinning for CUDA 26.04..."
mkdir -p /etc/apt/preferences.d
wget -qO /etc/apt/preferences.d/cuda-repository-pin-600 \
    https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2604/x86_64/cuda-ubuntu2604.pin

# 4. Fetch and Install the Official NVIDIA Keyring Package
log_info "Downloading and installing official NVIDIA repository keyring..."
readonly KEYRING_FILE="cuda-keyring_1.1-1_all.deb"
wget -q https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2604/x86_64/${KEYRING_FILE}
dpkg -i ${KEYRING_FILE}
rm -f ${KEYRING_FILE}

apt-get update

# 5. Install the Specific Driver-Matched CUDA Toolkit
log_info "Installing the targeted package: ${TARGET_PACKAGE}..."
apt-get install -y "${TARGET_PACKAGE}"

# 6. System-Wide Environment Path Initialization
log_info "Configuring system-wide environment path configurations..."
readonly PROFILE_SCRIPT="/etc/profile.d/cuda.sh"
readonly LD_SO_CONF_DIR="/etc/ld.so.conf.d/cuda.conf"

readonly CUDA_HOME_PATH="/usr/local/cuda-${DETECTED_VERSION}"
# Fallback scan if version directory naming varies
if [[ ! -d "${CUDA_HOME_PATH}" ]]; then
    readonly ACTIVE_CUDA=$(find /usr/local/ -maxdepth 1 -type d -name "cuda-*" | sort -V | tail -n 1)
else
    readonly ACTIVE_CUDA="${CUDA_HOME_PATH}"
fi

cat << EOF > "${PROFILE_SCRIPT}"
export CUDA_HOME=${ACTIVE_CUDA}
export PATH=${ACTIVE_CUDA}/bin:\$PATH
EOF
chmod +x "${PROFILE_SCRIPT}"

echo "${ACTIVE_CUDA}/lib64" > "${LD_SO_CONF_DIR}"
ldconfig

log_info "CUDA Toolkit Setup Completed Successfully to: ${ACTIVE_CUDA}"
