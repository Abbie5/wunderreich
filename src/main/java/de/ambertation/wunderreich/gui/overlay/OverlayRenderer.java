package de.ambertation.wunderreich.gui.overlay;

import de.ambertation.lib.math.Bounds;
import de.ambertation.lib.math.Float3;
import de.ambertation.lib.math.Transform;
import de.ambertation.lib.math.sdf.SDF;
import de.ambertation.lib.math.sdf.SDFMove;
import de.ambertation.lib.math.sdf.interfaces.BoundedShape;
import de.ambertation.lib.math.sdf.interfaces.MaterialProvider;
import de.ambertation.lib.math.sdf.shapes.Box;
import de.ambertation.lib.math.sdf.shapes.Empty;
import de.ambertation.wunderreich.items.construction.ConstructionData;
import de.ambertation.wunderreich.registries.WunderreichItems;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class OverlayRenderer implements DebugRenderer.SimpleDebugRenderer {
    record RenderInfo(Float3 pos, double camDistSquare, float deflate,
                      int color, float alpha,
                      int outlineColor, float outlineAlpha) {
        public static RenderInfo withCamPos(
                Float3 pos, Float3 camPos, float deflate,
                int color, float alpha,
                int outlineColor, float outlineAlpha
        ) {
            return new RenderInfo(
                    pos,
                    pos.sub(camPos).lengthSquare(),
                    deflate,
                    color,
                    alpha,
                    outlineColor,
                    outlineAlpha
            );
        }
    }

    public static final int[] FILL_COLORS = {
            0xFFAA574A,
            0xFFBE647D,
            0xFFB381B3,
            0xFF84A5D7,
            0xFF39C6DC,
            0xFF30E0BF,
            0xFF8BF28F,
            0xFFE8F966
    };

    public static final int[] OUTLINE_COLORS = {
            0xFF331E2A,
            0xFF44374C,
            0xFF4A546E,
            0xFF45748C,
            0xFF3896A0,
            0xFF39B8A9,
            0xFF5ED9A5,
            0xFF97F799
    };
    public static final int COLOR_MINION_YELLOW = 0xFFFFE74C;
    public static final int COLOR_FIERY_ROSE = 0xFFFF5964;
    public static final int COLOR_PURPLE = 0xFF5F00BA;
    public static final int COLOR_MEDIUM_PURPLE = 0xFFAB69F2;
    public static final int COLOR_RICH_BLACK = 0xFF090909;
    public static final int COLOR_BLUE_JEANS = 0xFF35A7FF;
    public static final int COLOR_MAUVE = 0xFFD9BBF9;
    public static final int COLOR_DARK_MAUVE = 0xFFCBA2F6;
    public static final int COLOR_DARK_GREEN_MOSS = 0xFF3F612D;

    public static final int COLOR_SELECTION = COLOR_MINION_YELLOW;
    public static final int COLOR_OUT_OF_REACH = COLOR_FIERY_ROSE;
    public static final int COLOR_BOUNDING_BOX = COLOR_BLUE_JEANS;
    public static final int COLOR_BLOCK_PREVIEW_FILL = COLOR_MAUVE;
    public static final int COLOR_BLOCK_PREVIEW_OUTLINE = COLOR_DARK_MAUVE;

    public static final OverlayRenderer INSTANCE = new OverlayRenderer();
    private final List<RenderInfo> positions = new ArrayList<>(64);

    float time = 0;

    @ApiStatus.Internal
    @Override
    public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double x, double y, double z) {
        final Player player = Minecraft.getInstance().player;
        positions.clear();
        //Box.angle += Math.toRadians(0.5);
        //-4, 63, -24


        ItemStack ruler = player.getMainHandItem();
        if (ruler == null || !ruler.is(WunderreichItems.RULER)) ruler = player.getOffhandItem();
        if (ruler == null || !ruler.is(WunderreichItems.RULER)) return;


        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        if (camera.isInitialized()) {
            final Float3 camPosWorldSpace = Float3.of(camera.getPosition());

            ConstructionData.setLastTargetInWorldSpaceOnClient(getTargetedBlock(Minecraft.getInstance()
                                                                                         .getCameraEntity(), 8, 4));
            Vec3 camPos = camera.getPosition().reverse();

            VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
            renderBlockOutline(vertexConsumer, poseStack,
                    ConstructionData.getLastTargetInWorldSpace(), camPos, .01f, COLOR_SELECTION, 1
            );

            DebugRenderer.renderFloatingText(
                    ConstructionData.getLastTargetInWorldSpace().toString(),
                    ConstructionData.getLastTargetInWorldSpace().x,
                    ConstructionData.getLastTargetInWorldSpace().y,
                    ConstructionData.getLastTargetInWorldSpace().z,
                    COLOR_SELECTION
            );

            ConstructionData constructionData = ConstructionData.getConstructionData(ruler);
            if (constructionData != null) {
                SDF sdf_active = constructionData.getActiveSDF();
                if (sdf_active == null) return;
                SDF sdf_root = sdf_active.getRoot();
                SDF sdf = sdf_active;
                SDF sdf_moved_root = sdf_root;


                if (sdf != null && !(sdf instanceof Empty)) {
                    Float3 offsetToWorldSpace = constructionData.CENTER.get();
                    if (offsetToWorldSpace != null) {
                        sdf = new SDFMove(sdf, offsetToWorldSpace);
                        sdf_moved_root = new SDFMove(sdf_root, offsetToWorldSpace);
                    }


                    for (Bounds.Interpolate i : Bounds.Interpolate.CORNERS_AND_CENTER) {
                        printDist(sdf, i.t);
                    }


                    Bounds worldSpaceBox = sdf.getBoundingBox();
                    Bounds worldSpaceBoxAligned = worldSpaceBox.blockAligned();
                    Bounds worldSpaceRootBox = sdf_moved_root.getBoundingBox();
                    final Float3 worldSpaceTargetPos = ConstructionData.getLastTargetInWorldSpace();
                    final Bounds.Interpolate targetCorner = worldSpaceBoxAligned.isCornerOrCenter(worldSpaceTargetPos);

                    time += Minecraft.getInstance().getDeltaFrameTime();
                    if (time > 10000) time -= 10000;
                    double scaledTime = time * 0.02;
                    float phase = (float) (Math.sin(Math.PI * 2 * (scaledTime - Math.floor(scaledTime))) + 1) / 2;


                    if (constructionData.inReach(camPosWorldSpace)) {
                        //resize/move bounding Box
                        if (constructionData.getSelectedCorner() != null) {
                            Bounds.Interpolate selectedCorner = constructionData.getSelectedCorner();

                            worldSpaceBox = ConstructionData.getNewBoundsForSelectedCorner(
                                    worldSpaceBox,
                                    selectedCorner,
                                    worldSpaceTargetPos
                            );
                            worldSpaceBoxAligned = worldSpaceBox.blockAligned();
                            renderBlockOutline(
                                    vertexConsumer, poseStack,
                                    worldSpaceBox.get(selectedCorner), camPos, 0.1f,
                                    COLOR_PURPLE, 1
                            );

                            if (sdf_active instanceof BoundedShape bs) {
                                if (offsetToWorldSpace != null) {
                                    Bounds bb = worldSpaceBox.move(offsetToWorldSpace.mul(-1));
                                    bs.setFromBoundingBox(bb);
                                } else {
                                    bs.setFromBoundingBox(worldSpaceBox);
                                }
                                constructionData.SDF_DATA.set(sdf_root);
                            }
                        }

                        renderBlockOutline(
                                vertexConsumer,
                                poseStack,
                                worldSpaceBoxAligned,
                                camPos,
                                0,
                                COLOR_BOUNDING_BOX,
                                1
                        );
                        renderBlockOutline(
                                vertexConsumer,
                                poseStack,
                                worldSpaceBox,
                                camPos,
                                0,
                                COLOR_MAUVE,
                                1
                        );
//                        renderBlockOutline(vertexConsumer, poseStack,
//                                worldSpaceRootBox, camPos, 0, COLOR_BOUNDING_BOX, .25f
//                        );
                        DebugRenderer.renderFloatingText(
                                worldSpaceBox.toString(),
                                worldSpaceBox.min.x,
                                worldSpaceBox.min.y - 0.4,
                                worldSpaceBox.min.z,
                                COLOR_MAUVE
                        );
                        DebugRenderer.renderFloatingText(
                                worldSpaceBoxAligned.toString(),
                                worldSpaceBox.min.x,
                                worldSpaceBox.min.y - 0.6,
                                worldSpaceBox.min.z,
                                COLOR_BOUNDING_BOX
                        );

                        if (sdf_active instanceof Box boxSDF) {
                            renderLineBox(
                                    vertexConsumer,
                                    poseStack,
                                    boxSDF.transform.translate(offsetToWorldSpace),
                                    camPos,
                                    COLOR_FIERY_ROSE,
                                    1
                            );

                            renderLineBox(
                                    vertexConsumer,
                                    poseStack,
                                    Transform.of(boxSDF.transform.center, boxSDF.transform.size)
                                             .translate(offsetToWorldSpace),
                                    camPos,
                                    COLOR_FIERY_ROSE,
                                    1
                            );


                            DebugRenderer.renderFloatingText(
                                    boxSDF.transform.getBoundingBoxWorldSpace().toString(),
                                    worldSpaceBox.min.x,
                                    worldSpaceBox.min.y - 0.8,
                                    worldSpaceBox.min.z,
                                    COLOR_FIERY_ROSE
                            );
                        }


                        if (constructionData.getSelectedCorner() == null) {
                            for (Bounds.Interpolate corner : Bounds.Interpolate.CORNERS_AND_CENTER) {
                                if ((targetCorner != null && targetCorner.idx == corner.idx)) {
                                    positions.add(RenderInfo.withCamPos(
                                            worldSpaceBoxAligned.get(corner).blockAligned(), camPosWorldSpace, 0.1f,
                                            COLOR_FIERY_ROSE, 0.5f,
                                            COLOR_FIERY_ROSE, 0.8f
                                    ));
                                } else {
                                    positions.add(RenderInfo.withCamPos(
                                            worldSpaceBoxAligned.get(corner).blockAligned(), camPosWorldSpace, 0.1f,
                                            blendColors(phase, COLOR_BOUNDING_BOX, COLOR_SELECTION), 0.8f,
                                            COLOR_SELECTION, phase
                                    ));
                                    renderBlockOutline(
                                            vertexConsumer,
                                            poseStack,
                                            worldSpaceBoxAligned.get(corner).blockAligned(),
                                            camPos,
                                            0.1f,
                                            blendColors(phase, COLOR_BOUNDING_BOX, COLOR_SELECTION),
                                            1
                                    );
                                }

                                DebugRenderer.renderFloatingText(
                                        worldSpaceBoxAligned.get(corner).toString(),
                                        worldSpaceBoxAligned.get(corner).x,
                                        worldSpaceBoxAligned.get(corner).y,
                                        worldSpaceBoxAligned.get(corner).z,
                                        COLOR_SELECTION
                                );
                            }
                        }

                        renderSDF(
                                camPosWorldSpace,
                                sdf_moved_root,
                                worldSpaceRootBox,
                                0.3f,
                                0.15f,
                                0,
                                false
                        );
                        renderSDF(camPosWorldSpace, sdf, worldSpaceBox, 0.2f, 0.95f, .6f, true);

                        renderPositionOutlines(vertexConsumer, poseStack, camPos);

                    } else
                        renderBlockOutline(vertexConsumer, poseStack, worldSpaceBox, camPos, 0, COLOR_OUT_OF_REACH, 1);
                }
            }

            positions.sort((a, b) -> {
                if (Math.abs(b.camDistSquare - a.camDistSquare) < 0.001) return 0;
                if (b.camDistSquare > a.camDistSquare) return 1;
                return -1;
            });
        } else {
            ConstructionData.setLastTargetInWorldSpaceOnClient(null);
        }


    }

    private void printDist(SDF sdf, Float3 oo) {
        oo = oo.sub(0.5);
        Float3 oa = ConstructionData.getLastTargetInWorldSpace();
        Float3 op = oa.add(oo);
        Float3 ot = oa.add(oo.mul(1.3).sub(0.15));
        double dist = sdf.dist(op);
        dist = Math.round(dist * 4) / 4.0;
        DebugRenderer.renderFloatingText(Float3.toString(dist),
                (float) ot.x, (float) ot.y, (float) ot.z,
                dist < 0 ? FILL_COLORS[0] : FILL_COLORS[FILL_COLORS.length - 1]
        );
    }

    private void renderSDF(
            Float3 camP,
            SDF sdf,
            Bounds box,
            float deflate,
            float alpha,
            float lineAlpha,
            boolean debugDist
    ) {
        sdf.evaluate(box, (p, ed) -> {
            int mIdx = 0;
            if (ed.source() instanceof MaterialProvider mp)
                mIdx = mp.getMaterialIndex();

            positions.add(RenderInfo.withCamPos(
                    p,
                    camP,
                    deflate,
                    FILL_COLORS[mIdx % FILL_COLORS.length],
                    alpha,
                    OUTLINE_COLORS[mIdx % OUTLINE_COLORS.length],
                    lineAlpha
            ));
        }, debugDist ? (p, ed, didPlace) -> {
            DebugRenderer.renderFloatingText(
                    "" + (Math.round(4 * ed.dist()) / 4.0),
                    p.x, p.y, p.z,
                    (ed.dist() < 0 ? COLOR_FIERY_ROSE : COLOR_BLUE_JEANS)
            );
        } : null);
    }

    @ApiStatus.Internal
    public void renderBlocks(PoseStack poseStack, Camera camera) {
        if (camera.isInitialized()) {
            Vec3 camPos = camera.getPosition().reverse();
            renderPositions(poseStack, camPos);
        }
    }

    private int blendColors(float t, int c1, int c2) {
        int r = (int) (t * FastColor.ARGB32.red(c2) + (1 - t) * FastColor.ARGB32.red(c1));
        int g = (int) (t * FastColor.ARGB32.green(c2) + (1 - t) * FastColor.ARGB32.green(c1));
        int b = (int) (t * FastColor.ARGB32.blue(c2) + (1 - t) * FastColor.ARGB32.blue(c1));
        int a = (int) (t * FastColor.ARGB32.alpha(c2) + (1 - t) * FastColor.ARGB32.alpha(c1));
        return FastColor.ARGB32.color(a, r, g, b);
    }


    private void renderBlockOutline(
            VertexConsumer vertexConsumer, PoseStack poseStack,
            BlockPos pos, Vec3 camPos, float deflate,
            int color, float alpha
    ) {
        final float x = (float) (pos.getX() + camPos.x);
        final float y = (float) (pos.getY() + camPos.y);
        final float z = (float) (pos.getZ() + camPos.z);
        renderLineBox(vertexConsumer, poseStack,
                x + deflate, y + deflate, z + deflate,
                1 + x - deflate, 1 + y - deflate, 1 + z - deflate,
                FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color),
                (int) (alpha * 0xFF)
        );
    }

    private void renderBlockOutline(
            VertexConsumer vertexConsumer, PoseStack poseStack,
            Float3 pos, Vec3 camPos, float deflate,
            int color,
            float alpha
    ) {
        final float x = (float) (pos.x + camPos.x) - 0.5f;
        final float y = (float) (pos.y + camPos.y) - 0.5f;
        final float z = (float) (pos.z + camPos.z) - 0.5f;
        renderLineBox(vertexConsumer, poseStack,
                x + deflate, y + deflate, z + deflate,
                1 + x - deflate, 1 + y - deflate, 1 + z - deflate,
                FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color),
                (int) (alpha * 0xFF)
        );
    }

    private void renderBlockOutline(
            VertexConsumer vertexConsumer, PoseStack poseStack,
            Bounds bounds, Vec3 camPos, float deflate,
            int color,
            float alpha
    ) {
        renderLineBox(
                vertexConsumer, poseStack,
                (float) (bounds.min.x + camPos.x) + deflate - 0.5f,
                (float) (bounds.min.y + camPos.y) + deflate - 0.5f,
                (float) (bounds.min.z + camPos.z) + deflate - 0.5f,
                (float) (bounds.max.x + camPos.x) - deflate + 0.5f,
                (float) (bounds.max.y + camPos.y) - deflate + 0.5f,
                (float) (bounds.max.z + camPos.z) - deflate + 0.5f,
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color),
                (int) (alpha * 0xFF)
        );
    }

    private static void renderLineBox(
            VertexConsumer vertexConsumer, PoseStack poseStack,
            Transform t, Vec3 camPos, int color, float alpha
    ) {
        renderLineBox(vertexConsumer, poseStack, t, camPos, FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color),
                (int) (alpha * 0xFF)
        );
    }

    private static void renderLineBox(
            VertexConsumer vertexConsumer, PoseStack poseStack,
            Transform t, Vec3 camPos,
            int r, int g, int b, int a
    ) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Float3[] corners = t.translate(camPos).getCornersInWorldSpace(false);


        DebugRenderer.renderFloatingText(
                t.toString(),
                corners[0].x - camPos.x,
                corners[0].y - 0.1 - camPos.y,
                corners[0].z - camPos.z,
                COLOR_BOUNDING_BOX
        );

        addLine(
                Bounds.Interpolate.MIN_MIN_MIN,
                Bounds.Interpolate.MAX_MIN_MIN,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );
        addLine(
                Bounds.Interpolate.MAX_MIN_MIN,
                Bounds.Interpolate.MAX_MIN_MAX,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );
        addLine(
                Bounds.Interpolate.MAX_MIN_MAX,
                Bounds.Interpolate.MIN_MIN_MAX,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );
        addLine(
                Bounds.Interpolate.MIN_MIN_MAX,
                Bounds.Interpolate.MIN_MIN_MIN,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );


        addLine(
                Bounds.Interpolate.MIN_MAX_MIN,
                Bounds.Interpolate.MAX_MAX_MIN,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );
        addLine(
                Bounds.Interpolate.MAX_MAX_MIN,
                Bounds.Interpolate.MAX_MAX_MAX,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );
        addLine(
                Bounds.Interpolate.MAX_MAX_MAX,
                Bounds.Interpolate.MIN_MAX_MAX,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );
        addLine(
                Bounds.Interpolate.MIN_MAX_MAX,
                Bounds.Interpolate.MIN_MAX_MIN,
                vertexConsumer, r, g, b, a, pose, normal, corners
        );


    }

    private static void addVertex(
            Float3 p,
            float nx, float ny, float nz,
            VertexConsumer vertexConsumer,
            int r, int g, int b, int a,
            Matrix4f pose, Matrix3f normal
    ) {
        vertexConsumer.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                      .color(r, g, b, a)
                      .normal(normal, nx, ny, nz)
                      .endVertex();
    }

    private static void addVertex(
            int corner,
            float nx, float ny, float nz,
            VertexConsumer vertexConsumer,
            int r, int g, int b, int a,
            Matrix4f pose, Matrix3f normal, Float3[] corners
    ) {
        addVertex(corners[corner], nx, ny, nz, vertexConsumer, r, g, b, a, pose, normal);
    }

    private static void addLine(
            Bounds.Interpolate cornerStart, Bounds.Interpolate cornerEnd,
            VertexConsumer vertexConsumer,
            int r, int g, int b, int a,
            Matrix4f pose, Matrix3f normal, Float3[] corners
    ) {
        Float3 start = corners[cornerStart.idx];
        Float3 end = corners[cornerEnd.idx];
        Float3 n = end.sub(start).normalized();

        addVertex(start, (float) n.x, (float) n.y, (float) n.z, vertexConsumer, r, g, b, a, pose, normal);
        addVertex(end, (float) n.x, (float) n.y, (float) n.z, vertexConsumer, r, g, b, a, pose, normal);
    }

    private static void renderLineBox(
            VertexConsumer vertexConsumer, PoseStack poseStack,
            float lx, float ly, float lz,
            float hx, float hy, float hz,
            int r, int g, int b, int a
    ) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        vertexConsumer.vertex(pose, lx, ly, lz).color(r, g, b, a).normal(normal, 1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, ly, lz).color(r, g, b, a).normal(normal, 1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, ly, lz).color(r, g, b, a).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, hy, lz).color(r, g, b, a).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, ly, lz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertexConsumer.vertex(pose, lx, ly, hz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertexConsumer.vertex(pose, hx, ly, lz).color(r, g, b, a).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, hy, lz).color(r, g, b, a).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, hy, lz).color(r, g, b, a).normal(normal, -1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, hy, lz).color(r, g, b, a).normal(normal, -1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, hy, lz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertexConsumer.vertex(pose, lx, hy, hz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertexConsumer.vertex(pose, lx, hy, hz).color(r, g, b, a).normal(normal, 0.0F, -1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, ly, hz).color(r, g, b, a).normal(normal, 0.0F, -1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, lx, ly, hz).color(r, g, b, a).normal(normal, 1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, ly, hz).color(r, g, b, a).normal(normal, 1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, ly, hz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, -1.0F).endVertex();
        vertexConsumer.vertex(pose, hx, ly, lz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, -1.0F).endVertex();
        vertexConsumer.vertex(pose, lx, hy, hz).color(r, g, b, a).normal(normal, 1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, hy, hz).color(r, g, b, a).normal(normal, 1.0F, 0.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, ly, hz).color(r, g, b, a).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, hy, hz).color(r, g, b, a).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
        vertexConsumer.vertex(pose, hx, hy, lz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
        vertexConsumer.vertex(pose, hx, hy, hz).color(r, g, b, a).normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }


    private void renderBlock(
            BufferBuilder builder, PoseStack poseStack,
            Float3 pos, Vec3 camPos, float deflate,
            int color, float alpha
    ) {
        renderBlock(
                builder, poseStack, pos, camPos, deflate,
                FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color),
                (int) (alpha * 0xFF)
        );
    }

    private void renderBlock(
            BufferBuilder builder,
            PoseStack poseStack,
            Float3 pos, Vec3 camPos, float deflate,
            int r, int g, int b, int a
    ) {
        Matrix4f m = poseStack.last().pose();
        Matrix3f rotation = poseStack.last().normal();
        float lx = (float) (pos.x + camPos.x) - 0.5f;
        float ly = (float) (pos.y + camPos.y) - 0.5f;
        float lz = (float) (pos.z + camPos.z) - 0.5f;
        float hx = lx + 1 - deflate;
        float hy = ly + 1 - deflate;
        float hz = lz + 1 - deflate;
        lx += deflate;
        ly += deflate;
        lz += deflate;
        builder.vertex(m, lx, ly, lz).color(r, g, b, a).normal(rotation, 0, 0, -1).endVertex();
        builder.vertex(m, lx, hy, lz).color(r, g, b, a).normal(rotation, 0, 0, -1).endVertex();
        builder.vertex(m, hx, hy, lz).color(r, g, b, a).normal(rotation, 0, 0, -1).endVertex();
        builder.vertex(m, hx, ly, lz).color(r, g, b, a).normal(rotation, 0, 0, -1).endVertex();

        builder.vertex(m, lx, ly, hz).color(r, g, b, a).normal(rotation, 0, 0, 1).endVertex();
        builder.vertex(m, hx, ly, hz).color(r, g, b, a).normal(rotation, 0, 0, 1).endVertex();
        builder.vertex(m, hx, hy, hz).color(r, g, b, a).normal(rotation, 0, 0, 1).endVertex();
        builder.vertex(m, lx, hy, hz).color(r, g, b, a).normal(rotation, 0, 0, 1).endVertex();

        builder.vertex(m, lx, ly, hz).color(r, g, b, a).normal(rotation, 0, -1, 0).endVertex();
        builder.vertex(m, lx, ly, lz).color(r, g, b, a).normal(rotation, 0, -1, 0).endVertex();
        builder.vertex(m, hx, ly, lz).color(r, g, b, a).normal(rotation, 0, -1, 0).endVertex();
        builder.vertex(m, hx, ly, hz).color(r, g, b, a).normal(rotation, 0, -1, 0).endVertex();

        builder.vertex(m, lx, hy, hz).color(r, g, b, a).normal(rotation, 0, 1, 0).endVertex();
        builder.vertex(m, hx, hy, hz).color(r, g, b, a).normal(rotation, 0, 1, 0).endVertex();
        builder.vertex(m, hx, hy, lz).color(r, g, b, a).normal(rotation, 0, 1, 0).endVertex();
        builder.vertex(m, lx, hy, lz).color(r, g, b, a).normal(rotation, 0, 1, 0).endVertex();

        builder.vertex(m, lx, ly, hz).color(r, g, b, a).normal(rotation, -1, 0, 0).endVertex();
        builder.vertex(m, lx, hy, hz).color(r, g, b, a).normal(rotation, -1, 0, 0).endVertex();
        builder.vertex(m, lx, hy, lz).color(r, g, b, a).normal(rotation, -1, 0, 0).endVertex();
        builder.vertex(m, lx, ly, lz).color(r, g, b, a).normal(rotation, -1, 0, 0).endVertex();

        builder.vertex(m, hx, ly, hz).color(r, g, b, a).normal(rotation, 1, 0, 0).endVertex();
        builder.vertex(m, hx, ly, lz).color(r, g, b, a).normal(rotation, 1, 0, 0).endVertex();
        builder.vertex(m, hx, hy, lz).color(r, g, b, a).normal(rotation, 1, 0, 0).endVertex();
        builder.vertex(m, hx, hy, hz).color(r, g, b, a).normal(rotation, 1, 0, 0).endVertex();
    }

    private void renderBlockOutline(
            VertexConsumer vertexConsumer,
            PoseStack poseStack,
            Float3 pos, Vec3 camPos,
            float r, float g, float b, float a
    ) {
        LevelRenderer.renderLineBox(
                poseStack, vertexConsumer,
                (int) pos.x + camPos.x + 0.2, (int) pos.y + camPos.y + 0.2, (int) pos.z + camPos.z + 0.2,
                (int) pos.x + camPos.x + 1 - 0.2, (int) pos.y + camPos.y + 1 - 0.2, (int) pos.z + camPos.z + 1 - 0.2,
                r, g, b, a
        );
    }

    private void renderPositionOutlines(VertexConsumer vertexConsumer, PoseStack poseStack, Vec3 camPos) {
        for (RenderInfo pos : positions) {
            if (pos.outlineAlpha > 0) {
                renderBlockOutline(
                        vertexConsumer, poseStack,
                        pos.pos, camPos, pos.deflate - 0.0001f,
                        pos.outlineColor, pos.outlineAlpha
                );
            }
        }
    }

    private void renderPositions(PoseStack poseStack, Vec3 camPos) {
        Tesselator tesselator = RenderSystem.renderThreadTesselator();

        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.enableDepthTest();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        //render alpha components without depth-write
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (RenderInfo p : positions) {
            renderBlock(bufferBuilder, poseStack, p.pos, camPos, p.deflate, p.color, p.alpha);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        BufferUploader.drawWithShader(bufferBuilder.end());


        //render to depth Buffer
        bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (RenderInfo p : positions) {
            renderBlock(bufferBuilder, poseStack, p.pos, camPos, p.deflate, 0, 0, 0, 1);
        }
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        BufferUploader.drawWithShader(bufferBuilder.end());


        //reset rendering system
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
    }

    @NotNull
    private BlockPos getTargetedBlock(Entity cameraEntity, int reach, int emptyDist) {
//        HitResult hitResult = cameraEntity.pick(reach, 0.0f, false);
//        if (hitResult.getType() == HitResult.Type.BLOCK) return ((BlockHitResult) hitResult).getBlockPos();
//
//        hitResult = cameraEntity.pick(reach, 0.0f, true);
//        if (hitResult.getType() == HitResult.Type.BLOCK) return ((BlockHitResult) hitResult).getBlockPos();

        return Float3.toBlockPos(cameraEntity.getEyePosition()
                                             .add(cameraEntity.getViewVector(1.0F).scale(emptyDist)));
    }
}
