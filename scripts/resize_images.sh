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

rm -rf "${RES_DIR}"/drawable-xxhdpi/*
rm -rf "${RES_DIR}"/drawable-xhdpi/*
rm -rf "${RES_DIR}"/drawable-hdpi/*
rm -rf "${RES_DIR}"/drawable-mdpi/*
rm -rf "${RES_DIR}"/drawable-ldpi/*

find "${RES_DIR}"/drawable-xxxhdpi/ -type f -printf "%f\n" | sort | while read IMAGE; do
	git grep -q ${IMAGE%.*} || echo "WARNING: file not used: ${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -resize 75%    "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -resize 50%    "${RES_DIR}"/drawable-xhdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -resize 37.5%  "${RES_DIR}"/drawable-hdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -resize 25%    "${RES_DIR}"/drawable-mdpi/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -resize 18.75% "${RES_DIR}"/drawable-ldpi/"${IMAGE}"
done

rm -rf "${RES_DIR}"/drawable-xxhdpi-v11/*
rm -rf "${RES_DIR}"/drawable-xhdpi-v11/*
rm -rf "${RES_DIR}"/drawable-hdpi-v11/*
rm -rf "${RES_DIR}"/drawable-mdpi-v11/*
rm -rf "${RES_DIR}"/drawable-ldpi-v11/*

find "${RES_DIR}"/drawable-xxxhdpi-v11/ -type f -printf "%f\n" | sort | while read IMAGE; do
	git grep -q ${IMAGE%.*} || echo "WARNING: file not used: ${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi-v11/"${IMAGE}" -resize 75%    "${RES_DIR}"/drawable-xxhdpi-v11/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi-v11/"${IMAGE}" -resize 50%    "${RES_DIR}"/drawable-xhdpi-v11/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi-v11/"${IMAGE}" -resize 37.5%  "${RES_DIR}"/drawable-hdpi-v11/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi-v11/"${IMAGE}" -resize 25%    "${RES_DIR}"/drawable-mdpi-v11/"${IMAGE}"
	convert "${RES_DIR}"/drawable-xxxhdpi-v11/"${IMAGE}" -resize 18.75% "${RES_DIR}"/drawable-ldpi-v11/"${IMAGE}"
done
