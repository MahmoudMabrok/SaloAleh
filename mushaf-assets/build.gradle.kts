plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("mushaf_assets")
    dynamicDelivery {
        // on-demand: not part of the install/update size. Downloaded by the
        // reader on first open (MushafPackController) and read from
        // AssetPackLocation.assetsPath()/quran-images (not context.assets).
        deliveryType.set("on-demand")
    }
}

// Images live under src/main/assets/quran-images/{page}/{line}.png (identical to the
// files in mushaf-imad-android's mushaf-images artifact). Declared in app.assetPacks.
// MushafAssetPackImagesTest (:mushaf unit test) checks every page line is here.
