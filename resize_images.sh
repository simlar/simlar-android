#!/bin/bash

## exit if an error occurs or on unset variables
set -eu -o pipefail

## Example
# mdpi    48px * 1   = 48px
# hdpi    48px * 1.5 = 72px
# xhdpi   48px * 2   = 96px
# xxhdpj  48px * 3   = 144px
# xxxhdpj 48px * 4   = 192px

rm -rf res/drawable-xhdpi/*
rm -rf res/drawable-hdpi/*
rm -rf res/drawable-mdpi/*

find res/drawable-xxhdpi/ -type f -printf "%f\n" | sort | while read IMAGE; do
	git grep -q ${IMAGE%.*} || echo "WARNING: file not used: ${IMAGE}"
	convert res/drawable-xxhdpi/"${IMAGE}" -resize 66.67% res/drawable-xhdpi/"${IMAGE}"
	convert res/drawable-xxhdpi/"${IMAGE}" -resize 50%    res/drawable-hdpi/"${IMAGE}"
	convert res/drawable-xxhdpi/"${IMAGE}" -resize 33.33% res/drawable-mdpi/"${IMAGE}"
done
