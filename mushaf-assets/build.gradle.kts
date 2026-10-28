plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("mushaf_assets")
    dynamicDelivery {
        // Will matter once app.assetPacks includes this module again.
        deliveryType.set("install-time")
    }
}

// Images live under src/main/assets/quran-images (copied from mushaf-imad-android).
// SaloAleh does not declare this pack in app.assetPacks yet: mushaf-ui 0.3.0 only
// loads via AssetManager, so pages must ship inside :mushaf from the AAR.
