package com.crow.locrowai.api.registration;

import net.minecraft.resources.ResourceLocation;

public record AIExtension(ResourceLocation loc, ResourceLocation manifest, ResourceLocation sig, ResourceLocation key) {
}
