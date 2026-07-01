#!/bin/bash
#
# Created on July 1, 2026
#
# @author: sgoldsmith
#
# Installs and configures the official Intel Media Driver (iHD), VAAPI runtimes, development headers,
# and oneVPL environments safely for Tiger Lake Intel Iris Xe Graphics processing.
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
   log_error "This script must be run as root. Please use: sudo ./install-intel-media.sh"
   exit 1
fi

# Determine the actual invoking non-root user for group additions
if [[ -n "${SUDO_USER:-}" ]]; then
    readonly REAL_USER="${SUDO_USER}"
else
    readonly REAL_USER="${USER}"
fi

log_info "Starting Intel Media and Hardware Acceleration Setup..."

# 1. Update Package Lists and Install Dependencies
log_info "Updating package indexes and installing core toolchains..."
apt-get update

# intel-media-va-driver-non-free provides unrestricted low-power encoding modes
apt-get install -y \
    libmfx-gen1.2 \
    libva-dev \
    libvpl-dev \
    intel-media-va-driver-non-free \
    va-driver-all \
    vainfo

# 2. Assign Group Permissions
log_info "Assigning user '${REAL_USER}' to hardware video rendering groups..."
usermod -aG render "${REAL_USER}"
usermod -aG video "${REAL_USER}"

# 3. Configure System-wide Environment Variables
readonly PROFILE_SCRIPT="/etc/profile.d/intel-media.sh"
log_info "Setting default iHD hardware driver environment globally via ${PROFILE_SCRIPT}"

cat << 'EOF' > "${PROFILE_SCRIPT}"
export LIBVA_DRIVER_NAME=iHD
EOF
chmod +x "${PROFILE_SCRIPT}"

# --- Verification & Summary ---
log_info "========================================================"
log_info " Intel Media Stack Configuration Completed!             "
log_info "========================================================"
log_info "System-wide driver routing profile set to iHD."
log_info "Hardware group profiles applied successfully for user: ${REAL_USER}"

# Execute validation inline by bypassing the temporary session caching restriction via sg tool
log_info "Testing validation query against Intel hardware DRM device node..."

if command -v sg &> /dev/null; then
    if sg render -c "env LIBVA_DRIVER_NAME=iHD vainfo --display drm --device /dev/dri/renderD128" &> /dev/null; then
        log_info "Driver Validation: Interface /dev/dri/renderD128 initialized perfectly!"
    else
        log_warn "Direct validation check failed or renderD128 device path was busy/not found."
    fi
else
    log_warn "The 'sg' tool is missing. Skipping hot-reload group verification loop."
fi

log_info "To apply these changes immediately to your running terminal session, please execute:"
echo -e "${YELLOW}newgrp render && newgrp video && source /etc/profile.d/intel-media.sh${NC}"
