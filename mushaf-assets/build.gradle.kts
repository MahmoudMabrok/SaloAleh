plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("mushaf_assets")
    dynamicDelivery {
        // install-time packs are merged into the app's AssetManager, which is
        // what mushaf-ui's default AssetLineImageSource reads.
        deliveryType.set("install-time")
    }
}

// Images live under src/main/assets/quran-images/{page}/{line}.png (identical to the
// files in mushaf-imad-android's mushaf-images artifact). Declared in app.assetPacks.
// MushafAssetPackImagesTest (:mushaf unit test) checks every page line is here.
