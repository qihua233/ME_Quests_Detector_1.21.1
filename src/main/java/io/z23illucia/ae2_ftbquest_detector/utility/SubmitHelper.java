package io.z23illucia.ae2_ftbquest_detector.utility;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.z23illucia.ae2_ftbquest_detector.blockentity.DetectorEntityList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class SubmitHelper {
    public static void submitTask(TeamData teamData, ServerPlayer player, Task task)
    {
        for(var e: DetectorEntityList.getAll())
        {
            //System.out.println(e.getBlockPos());
            if(e.ownerTeamId.equals(teamData.getTeamId()))
            {
                var Inventory = e.getGridNode(null).getGrid().getStorageService().getInventory();
                for(var entry : Inventory.getAvailableStacks()) {
                    var key = entry.getKey();

                    if(key instanceof AEItemKey itemKey
                            && task instanceof ItemTask itemTask
                            && itemTask.test(itemKey.toStack())
                    ){
                        ItemStack stack = itemKey.toStack();
                        stack.setCount((int) entry.getLongValue());
                        long extrable = Inventory.extract(key,
                                stack.getCount(),
                                Actionable.SIMULATE,
                                IActionSource.ofPlayer(player)
                        );
                        stack.setCount((int) extrable);
                        //System.out.println("real"+stack.getCount());
                        ItemStack stack1 = itemTask.insert(teamData, stack, false);
                        //System.out.println("left"+stack1.getCount());
                        if (stack.getCount() != stack1.getCount()) {
                            try {
                                Inventory.extract(key,
                                        stack.getCount() - stack1.getCount(),
                                        Actionable.MODULATE,
                                        IActionSource.ofPlayer(player)
                                );
                                //System.out.println("!!!!!!"+stack.getCount()+"  "+stack1.getCount());
                            } catch (Exception err) {
                                System.err.println("[Mixin Error]");
                                err.printStackTrace();
                            }

                        }
                    }
                    else if(task instanceof FluidTask fluidTask
                            && key instanceof AEFluidKey fluidKey
                            && fluidTask.getFluid() == fluidKey.getFluid()
                    )
                    {
                        FluidStack stack = fluidKey.toStack((int) entry.getLongValue());

                        long extrable = Inventory.extract(key,
                                stack.getAmount(),
                                Actionable.SIMULATE,
                                IActionSource.ofPlayer(player)
                        );
                        stack.setAmount((int) extrable);
                        //System.out.println("real"+stack.getCount());
                        long add = Math.min(stack.getAmount(), task.getMaxProgress() - teamData.getProgress(task));
                        if (add > 0L && teamData.getFile().isServerSide()) {
                                teamData.addProgress(task, add);
                            try {
                                Inventory.extract(key,
                                        add,
                                        Actionable.MODULATE,
                                        IActionSource.ofPlayer(player)
                                );
                                //System.out.println("!!!!!!"+stack.getAmount());
                            } catch (Exception err) {
                                System.err.println("[Mixin Error]");
                                err.printStackTrace();
                            }
                        }

                    }

                }
            }

        }
    }
}
