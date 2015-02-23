#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

## Example
# ldpi    48px * .75 = 36px
# mdpi    48px * 1   = 48px
# hdpi    48px * 1.5 = 72px
# xhdpi   48px * 2   = 96px
# xxhdpj  48px * 3   = 144px
# xxxhdpj 48px * 4   = 192px

#declare -r RES_DIR="res"
declare -r RES_DIR="$(dirname $(readlink -f $0))/../app/src/main/res/"

rm -rf "${RES_DIR}"/drawable-xxxhdpi/*
rm -rf "${RES_DIR}"/drawable-xhdpi/*
rm -rf "${RES_DIR}"/drawable-hdpi/*
rm -rf "${RES_DIR}"/drawable-mdpi/*
rm -rf "${RES_DIR}"/drawable-ldpi/*

find "${RES_DIR}"/drawable-xxhdpi/ -type f -printf "%f\n" | sort | while read IMAGE; do
	git grep -q ${IMAGE%.*} || echo "WARNING: file not used: ${IMAGE}"
	convert "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}" -resize 133.33% "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}" -resize 66.67% "${RES_DIR}"/drawable-xhdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}" -resize 50%    "${RES_DIR}"/drawable-hdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}" -resize 33.33% "${RES_DIR}"/drawable-mdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}" -resize 25%    "${RES_DIR}"/drawable-ldpi/"${IMAGE}"
done
