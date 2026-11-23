package de.luma.gallery.model

import de.luma.gallery.R

data class PhotoPreset(val id:String,val label:Int,val recipe:PhotoRecipe)
val PHOTO_PRESETS=listOf(
    PhotoPreset("natural",R.string.filter_natural,PhotoRecipe()),
    PhotoPreset("clear",R.string.filter_clear,PhotoRecipe(contrast=1.18f,saturation=1.1f,sharpness=.12f)),
    PhotoPreset("matte",R.string.filter_matte,PhotoRecipe(contrast=.9f,saturation=.82f,curves=PhotoCurves(rgb=ToneCurve(black=.09f,white=.95f)))),
    PhotoPreset("warm",R.string.filter_warm,PhotoRecipe(temperature=.35f,saturation=1.08f)),
    PhotoPreset("cool",R.string.filter_cool,PhotoRecipe(temperature=-.3f,tint=.04f,contrast=1.06f)),
    PhotoPreset("gold",R.string.filter_gold,PhotoRecipe(temperature=.55f,exposure=.12f,shadows=.15f,saturation=.95f)),
    PhotoPreset("cinema",R.string.filter_cinema,PhotoRecipe(contrast=1.15f,saturation=.72f,temperature=-.08f,curves=PhotoCurves(red=ToneCurve(shadows=.2f,highlights=.82f),blue=ToneCurve(shadows=.32f,highlights=.7f)))),
    PhotoPreset("vintage",R.string.filter_vintage,PhotoRecipe(temperature=.2f,saturation=.65f,curves=PhotoCurves(rgb=ToneCurve(black=.08f,white=.92f)))),
    PhotoPreset("portrait",R.string.filter_portrait,PhotoRecipe(contrast=.93f,temperature=.12f,highlights=-.15f,shadows=.12f)),
    PhotoPreset("landscape",R.string.filter_landscape,PhotoRecipe(contrast=1.12f,saturation=1.28f,sharpness=.2f,highlights=-.16f)),
    PhotoPreset("vivid",R.string.filter_vivid,PhotoRecipe(contrast=1.12f,saturation=1.45f,shadows=.1f)),
    PhotoPreset("soft",R.string.filter_soft,PhotoRecipe(exposure=.2f,contrast=.87f,saturation=.92f,highlights=-.12f)),
    PhotoPreset("bw",R.string.filter_bw,PhotoRecipe(contrast=1.1f,saturation=0f)),
    PhotoPreset("noir",R.string.filter_noir,PhotoRecipe(contrast=1.35f,saturation=0f,shadows=-.2f)),
    PhotoPreset("silver",R.string.filter_silver,PhotoRecipe(saturation=0f,curves=PhotoCurves(rgb=ToneCurve(black=.07f,shadows=.3f,highlights=.8f)))),
    PhotoPreset("sepia",R.string.filter_sepia,PhotoRecipe(saturation=.15f,temperature=.6f,tint=.15f))
)
fun PhotoRecipe.withPreset(preset:PhotoRecipe,strength:Float):PhotoRecipe {
    require(strength.isFinite()&&strength in 0f..1f)
    fun mix(a:Float,b:Float)=a+(b-a)*strength
    fun curve(a:ToneCurve,b:ToneCurve)=ToneCurve(mix(a.black,b.black),mix(a.shadows,b.shadows),mix(a.midtones,b.midtones),mix(a.highlights,b.highlights),mix(a.white,b.white))
    return copy(brightness=mix(brightness,preset.brightness),contrast=mix(contrast,preset.contrast),saturation=mix(saturation,preset.saturation),
        exposure=mix(exposure,preset.exposure),temperature=mix(temperature,preset.temperature),tint=mix(tint,preset.tint),highlights=mix(highlights,preset.highlights),
        shadows=mix(shadows,preset.shadows),sharpness=mix(sharpness,preset.sharpness),curves=PhotoCurves(curve(curves.rgb,preset.curves.rgb),curve(curves.red,preset.curves.red),curve(curves.green,preset.curves.green),curve(curves.blue,preset.curves.blue)))
}
