#!/bin/sh
echo "Running wyvern fixes"
cd node_modules
rm -rf wyvern-schemas/
git clone https://github.com/ProjectOpenSea/wyvern-schemas
# rm -rf wyvern-js/
# git clone https://github.com/ProjectOpenSea/wyvern-js
cd ../