package com.soulstealer.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The pointy wizard hat, rendered as the "head" of a humanoid armor model. */
@OnlyIn(Dist.CLIENT)
public class WizardHatModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("soulstealer", "wizard_hat"), "main");

    public WizardHatModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, -9.0F, -8.0F, 16.0F, 1.0F, 16.0F)
                .texOffs(0, 17).addBox(-5.0F, -13.0F, -5.0F, 10.0F, 4.0F, 10.0F)
                .texOffs(0, 31).addBox(-3.5F, -17.0F, -3.5F, 7.0F, 4.0F, 7.0F)
                .texOffs(0, 42).addBox(-2.0F, -21.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.ZERO);

        // The bent tip: pivot at the top of the cone (y = -21) so the tilt bends it instead of swinging it around the neck.
        head.addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(0, 50).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -21.0F, 0.0F, 0.3F, 0.0F, 0.0F));

        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }
}
