#!/bin/bash
#
# Created on May 23, 2026
#
# @author: sgoldsmith
#
# Install core system dependencies, JDK 25, and build tools for Alarmbian.
#
# Steven P. Goldsmith
# sgjava@gmail.com

set -e

ARCH=$(uname -m)
SDKMAN_DIR="$HOME/.sdkman"
JAVA_TMP="$HOME/.java_tmp"

echo "--------------------------------------------------"
echo "STEP 1: System Prep & Tmp Dir"
echo "--------------------------------------------------"
sudo apt update && sudo apt install -y curl zip unzip wget xz-utils git build-essential
mkdir -p "$JAVA_TMP"
chmod 777 "$JAVA_TMP"

echo "--------------------------------------------------"
echo "STEP 2: SDKMAN Setup"
echo "--------------------------------------------------"
export SDKMAN_DIR="$HOME/.sdkman"
if [[ ! -d "$SDKMAN_DIR" ]]; then
    curl -s "https://get.sdkman.io" | bash || true
fi
[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"

echo "--------------------------------------------------"
echo "STEP 3: JDK Installation"
echo "--------------------------------------------------"
case $ARCH in
    armv7l|armv8l)
        JDK_DIR="$SDKMAN_DIR/candidates/java/25-arm32-local"
        if [ ! -d "$JDK_DIR" ]; then
            wget -q -O /tmp/jdk25.tar.xz "https://builds.shipilev.net/openjdk-jdk25/openjdk-jdk25-linux-arm32-hflt-server.tar.xz"
            mkdir -p "$JDK_DIR"
            tar -xJf /tmp/jdk25.tar.xz -C "$JDK_DIR" --strip-components=1
            sdk install java 25-arm32-local "$JDK_DIR"
        fi
        sdk default java 25-arm32-local
        ;;
    *)
        # Default for aarch64 (ARM64) or x86_64
        sdk install java 25-zulu || true
        sdk default java 25-zulu
        ;;
esac

# Install standard build automation tools via SDKMAN
sdk install maven || true
sdk install ant || true
sdk install gradle 9.3.0 || true
sdk default gradle 9.3.0

echo "--------------------------------------------------"
echo "STEP 4: Global Environment Persistence"
echo "--------------------------------------------------"
update_env_var() {
    local var_name=$1
    local var_value=$2
    if grep -q "^${var_name}=" /etc/environment; then
        sudo sed -i "s|^${var_name}=.*|${var_name}=\"${var_value}\"|" /etc/environment
    else
        echo "${var_name}=\"${var_value}\"" | sudo tee -a /etc/environment
    fi
}

JAVA_P="$SDKMAN_DIR/candidates/java/current"
M2_P="$SDKMAN_DIR/candidates/maven/current"
ANT_P="$SDKMAN_DIR/candidates/ant/current"
GRADLE_P="$SDKMAN_DIR/candidates/gradle/current"

update_env_var "JAVA_HOME" "$JAVA_P"
update_env_var "JAVA_OPTS" "-Djava.io.tmpdir=$JAVA_TMP"
update_env_var "M2_HOME" "$M2_P"
update_env_var "ANT_HOME" "$ANT_P"
update_env_var "GRADLE_HOME" "$GRADLE_P"

# Rebuild generic scannable system PATH
NEW_PATH="$JAVA_P/bin:$M2_P/bin:$ANT_P/bin:$GRADLE_P/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
update_env_var "PATH" "$NEW_PATH"

echo "--------------------------------------------------"
echo "STEP 5: Verification"
echo "--------------------------------------------------"
[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"

printf "Java:      " && java -version 2>&1 | head -n 1
printf "Maven:     " && mvn -version | head -n 1
printf "Ant:       " && ant -version | head -n 1
printf "Gradle:    " && gradle -version | grep "Gradle"

echo "--------------------------------------------------"
echo "Setup Complete! Please run: source /etc/environment"
echo "--------------------------------------------------"
