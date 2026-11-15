#!/usr/bin/env bash
# Compila o mod loconautics_ip_compat contra os jars do modpack (sem Gradle).
# Uso: ./build.sh   -> gera loconautics_ip_compat-<versão>.jar nesta pasta
set -euo pipefail
cd "$(dirname "$0")"
INST="${INST:-/home/usuariodejava/Documents/curseforge/minecraft/Instances/Create - Portais e Aeronautica - Aventuras/mods}"
LIB="${LIB:-/home/usuariodejava/Documents/curseforge/minecraft/Install/libraries}"
JDK="${JDK:-/home/usuariodejava/.local/opt/jdk-21/bin}"
VERSAO=$(grep -m1 '^version' res/META-INF/neoforge.mods.toml | sed -E 's/.*"(.*)"/\1/')
rm -rf build && mkdir -p build/lib build/nested build/classes build/stage
for j in loconautics- ip_aeronautics sable-neoforge create-1.21.1 create-aeronautics-bundled; do cp "$INST"/$j*.jar build/lib/; done
for j in build/lib/*.jar; do unzip -o -q "$j" 'META-INF/jarjar/*.jar' -d build/nested 2>/dev/null || true; done
CP=$(ls build/lib/*.jar | tr '\n' ':')$(find build/nested -name '*.jar' | tr '\n' ':')
for p in 'client-1.21.1-20240808.144430-srg.jar' 'neoforge-21.1.265-universal.jar' 'bus-8.0.5.jar' 'sponge-mixin-0.16.4+mixin.0.8.7.jar' 'joml-1.10.5.jar' 'log4j-api-2.22.1.jar' 'slf4j-api-2.0.9.jar' 'guava-32.1.2-jre.jar' 'datafixerupper-8.0.16.jar' 'fastutil-8.5.12.jar' 'logging-1.2.7.jar' 'brigadier-1.3.10.jar'; do CP="$CP$(find $LIB -name "$p" | head -1):"; done
CP="$CP$(find $LIB -path '*fancymodloader/loader/4.0.45*' -name '*.jar' | head -1)"
"$JDK/javac" --release 21 -proc:none -encoding UTF-8 -nowarn -cp "$CP" -d build/classes $(find src -name '*.java')
cp -r build/classes/* res/* build/stage/
(cd build/stage && "$JDK/jar" --create --file "../../loconautics_ip_compat-$VERSAO.jar" .)
echo "ok: loconautics_ip_compat-$VERSAO.jar"
