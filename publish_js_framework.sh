#!/bin/bash
 
npm install 

#npm run build

#npm run build:native
npm run build:native:release

cp dist/native.release.js android/sdk/assets/main.js
