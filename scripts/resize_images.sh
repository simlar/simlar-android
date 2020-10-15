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

declare -r GREADLINK=$(which greadlink)
declare -r READLINK=${GREADLINK:-"$(which readlink)"}

declare -r GFIND=$(which gfind)
declare -r FIND=${GFIND:-"$(which find)"}

declare -r RES_DIR="$(dirname $(${READLINK} -f $0))/../app/src/main/res/"

rm -rf "${RES_DIR}"/drawable-xxhdpi/*
rm -rf "${RES_DIR}"/drawable-xhdpi/*
rm -rf "${RES_DIR}"/drawable-hdpi/*
rm -rf "${RES_DIR}"/drawable-mdpi/*
rm -rf "${RES_DIR}"/drawable-ldpi/*

"${FIND}" "${RES_DIR}"/drawable-xxxhdpi/ -type f -printf "%f\n" | sort | while read IMAGE; do
	git grep -q ${IMAGE%.*} || echo "WARNING: file not used: ${IMAGE}"
	gm convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -strip -resize 75%    "${RES_DIR}"/drawable-xxhdpi/"${IMAGE}"
	gm convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -strip -resize 50%    "${RES_DIR}"/drawable-xhdpi/"${IMAGE}"
	gm convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -strip -resize 37.5%  "${RES_DIR}"/drawable-hdpi/"${IMAGE}"
	gm convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -strip -resize 25%    "${RES_DIR}"/drawable-mdpi/"${IMAGE}"
	gm convert "${RES_DIR}"/drawable-xxxhdpi/"${IMAGE}" -strip -resize 18.75% "${RES_DIR}"/drawable-ldpi/"${IMAGE}"
done
