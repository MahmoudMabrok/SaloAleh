plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("mushaf_assets")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
