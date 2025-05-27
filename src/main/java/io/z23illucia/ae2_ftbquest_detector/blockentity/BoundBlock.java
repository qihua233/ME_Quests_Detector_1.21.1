package io.z23illucia.ae2_ftbquest_detector.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record BoundBlock(ResourceKey<Level> dimension, BlockPos pos) {}
