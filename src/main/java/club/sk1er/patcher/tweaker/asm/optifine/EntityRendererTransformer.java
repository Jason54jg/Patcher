/*
 * Copyright © 2020 by Sk1er LLC
 *
 * All rights reserved.
 *
 * Sk1er LLC
 * 444 S Fulton Ave
 * Mount Vernon, NY
 * sk1er.club
 */

package club.sk1er.patcher.tweaker.asm.optifine;

import club.sk1er.patcher.Patcher;
import club.sk1er.patcher.config.PatcherConfig;
import club.sk1er.patcher.tweaker.ClassTransformer;
import club.sk1er.patcher.tweaker.transform.PatcherTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.lwjgl.input.Mouse;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Iterator;
import java.util.ListIterator;

// By LlamaLad7
@SuppressWarnings("unused")
public class EntityRendererTransformer implements PatcherTransformer {
    private static final float normalModifier = 4f;
    public static boolean zoomed = false;
    private static float currentModifier = 4f;

    public static float getModifier() {
        if (!PatcherConfig.scrollToZoom) {
            return normalModifier;
        }

        int moved = Mouse.getDWheel();

        if (moved > 0) {
            currentModifier += 0.25f * currentModifier;
        } else if (moved < 0) {
            currentModifier -= 0.25f * currentModifier;
        }

        if (currentModifier < 0.8) {
            currentModifier = 0.8f;
        }

        if (currentModifier > 600) {
            currentModifier = 600f;
        }

        return currentModifier;
    }

    public static void resetCurrent() {
        currentModifier = normalModifier;
    }

    /**
     * The class name that's being transformed
     *
     * @return the class name
     */
    @Override
    public String[] getClassName() {
        return new String[]{"net.minecraft.client.renderer.EntityRenderer"};
    }

    /**
     * Perform any asm in order to transform code
     *
     * @param classNode the transformed class node
     * @param name      the transformed class name
     */
    @Override
    public void transform(ClassNode classNode, String name) {
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "createdLightmap", "Z", null, null));

        for (MethodNode methodNode : classNode.methods) {
            String methodName = mapMethodName(classNode, methodNode);

            switch (methodName) {
                case "getFOVModifier":
                case "func_78481_a": {
                    int zoomActiveIndex = -1;

                    for (LocalVariableNode var : methodNode.localVariables) {
                        if (var.name.equals("zoomActive")) {
                            zoomActiveIndex = var.index;
                            break;
                        }
                    }

                    Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
                    LabelNode ifne = new LabelNode();
                    while (iterator.hasNext()) {
                        AbstractInsnNode thing = iterator.next();
                        if (checkNode(thing)) {
                            methodNode.instructions.insertBefore(thing, new FieldInsnNode(Opcodes.GETSTATIC, getPatcherConfigClass(), "normalZoomSensitivity", "Z")); // False instead of true
                            methodNode.instructions.insertBefore(thing, new InsnNode(Opcodes.ICONST_1));
                            methodNode.instructions.insertBefore(thing, new InsnNode(Opcodes.IXOR));
                            methodNode.instructions.insert(thing, callReset());
                            methodNode.instructions.remove(thing);
                        } else if (checkDivNode(thing)) {
                            methodNode.instructions.remove(thing.getPrevious());
                            methodNode.instructions.insertBefore(thing, getDivisor());
                        } else if (checkZoomActiveNode(thing, zoomActiveIndex)) {
                            methodNode.instructions.insertBefore(thing, setZoomed(zoomActiveIndex));
                        } else if (thing instanceof MethodInsnNode && thing.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            String methodInsnName = mapMethodNameFromNode((MethodInsnNode) thing);

                            if (methodInsnName.equals("getMaterial") || methodInsnName.equals("func_149688_o")) {
                                methodNode.instructions.insertBefore(thing.getPrevious(), createLabel(ifne));
                            }
                        } else if (thing instanceof LdcInsnNode && ((LdcInsnNode) thing).cst.equals(70.0f) && thing.getPrevious().getOpcode() == Opcodes.FMUL) {
                            methodNode.instructions.insert(thing.getNext().getNext().getNext(), setLabel(ifne));
                        }
                    }

                    break;
                }
                case "orientCamera":
                case "func_78467_g": {
                    ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

                    while (iterator.hasNext()) {
                        AbstractInsnNode next = iterator.next();

                        if (next instanceof LdcInsnNode && ((LdcInsnNode) next).cst.equals(-0.10000000149011612F)) {
                            methodNode.instructions.insertBefore(next, fixParallax());
                            methodNode.instructions.remove(next);
                        } else if (next instanceof MethodInsnNode && next.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            String methodInsnName = mapMethodNameFromNode((MethodInsnNode) next);

                            if (methodInsnName.equals("rayTraceBlocks") || methodInsnName.equals("func_72933_a")) {
                                ((MethodInsnNode) next).name = Patcher.isDevelopment() ? "rayTraceBlocks" : "func_147447_a";
                                ((MethodInsnNode) next).desc = FMLDeobfuscatingRemapper.INSTANCE.mapDesc(
                                    "(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;"
                                );

                                methodNode.instructions.insertBefore(next, changeMethodRedirect());
                            }
                        }
                    }
                    break;
                }
                case "updateLightmap":
                case "func_78472_g": {
                    methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), checkFullbright());

                    ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

                    while (iterator.hasNext()) {
                        AbstractInsnNode next = iterator.next();

                        if (next.getOpcode() == Opcodes.INVOKEVIRTUAL && next instanceof MethodInsnNode) {
                            String methodInsnName = mapMethodNameFromNode((MethodInsnNode) next);

                            if (methodInsnName.equals("endSection") || methodInsnName.equals("func_76319_b")) {
                                methodNode.instructions.insertBefore(next.getPrevious().getPrevious().getPrevious(), assignCreatedLightmap());
                            } else if (methodInsnName.equals("isPotionActive") || methodInsnName.equals("func_70644_a")) {
                                methodNode.instructions.insertBefore(next.getPrevious().getPrevious().getPrevious().getPrevious(), clampLightmap());
                            }
                        }
                    }
                    break;
                }
                case "renderStreamIndicator":
                case "func_152430_c":
                    clearInstructions(methodNode);
                    methodNode.instructions.insert(new InsnNode(Opcodes.RETURN));
                    break;

                case "renderWorldPass":
                case "func_175068_a": {
                    ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
                    while (iterator.hasNext()) {
                        AbstractInsnNode next = iterator.next();

                        switch (ClassTransformer.optifineVersion) {
                            case "I7": {
                                if (next instanceof TypeInsnNode) {
                                    if (FMLDeobfuscatingRemapper.INSTANCE.map(((TypeInsnNode) next).desc).equals("net/minecraft/client/renderer/culling/Frustum")) {
                                        while (true) {
                                            AbstractInsnNode insn = iterator.next();
                                            if (insn instanceof VarInsnNode) {
                                                methodNode.instructions.insert(insn, getStoreCameraInsn(((VarInsnNode) insn).var));
                                                break;
                                            }
                                        }
                                    }
                                }

                                break;
                            }

                            default:
                            case "L5": {
                                int cameraVar = -1;

                                for (LocalVariableNode var : methodNode.localVariables) {
                                    if (var.name.equals("icamera")) {
                                        cameraVar = var.index;
                                        break;
                                    }
                                }

                                if (next instanceof MethodInsnNode && next.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                                    String methodInsnName = mapMethodNameFromNode((MethodInsnNode) next);

                                    if (methodInsnName.equals("getRenderViewEntity") || methodInsnName.equals("func_175606_aa")) {
                                        next = next.getPrevious().getPrevious();

                                        methodNode.instructions.insertBefore(next, getStoreCameraInsn(cameraVar));
                                        break;
                                    }
                                }
                            }

                            break;
                        }
                    }

                    break;
                }

                case "func_181560_a":
                case "updateCameraAndRender": {
                    ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

                    while (iterator.hasNext()) {
                        AbstractInsnNode next = iterator.next();

                        if (next instanceof MethodInsnNode && next.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            String methodInsnName = mapMethodNameFromNode((MethodInsnNode) next);

                            if (methodInsnName.equals("renderGameOverlay") || methodInsnName.equals("func_175180_a")) {
                                methodNode.instructions.insertBefore(next.getNext(), toggleCullingStatus(false));

                                for (int i = 0; i < 9; i++) {
                                    next = next.getPrevious();
                                }

                                methodNode.instructions.insertBefore(next.getNext(), toggleCullingStatus(true));
                                break;
                            }
                        }
                    }

                    break;
                }

                case "func_78464_a":
                case "updateRenderer": {
                    ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();

                    while (iterator.hasNext()) {
                        AbstractInsnNode next = iterator.next();

                        if (next instanceof MethodInsnNode && next.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            String methodInsnName = mapMethodNameFromNode((MethodInsnNode) next);

                            if (methodInsnName.equals("getLightBrightness")) {
                                ((MethodInsnNode) next.getPrevious()).desc = "(Lnet/minecraft/util/Vec3;)V";
                                methodNode.instructions.insertBefore(next.getPrevious(), getEyePosition());
                                break;
                            }
                        }
                    }

                    break;
                }
            }
        }
    }

    private InsnList clampLightmap() {
        // using srg name in dev crashes? Ok Forge
        final String clamp_float = isDevelopment() ? "clamp_float" : "func_76131_a";
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.FLOAD, 12));
        list.add(new InsnNode(Opcodes.FCONST_0));
        list.add(new InsnNode(Opcodes.FCONST_1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/MathHelper", clamp_float, "(FFF)F", false));
        list.add(new VarInsnNode(Opcodes.FSTORE, 12));
        list.add(new VarInsnNode(Opcodes.FLOAD, 13));
        list.add(new InsnNode(Opcodes.FCONST_0));
        list.add(new InsnNode(Opcodes.FCONST_1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/MathHelper", clamp_float, "(FFF)F", false));
        list.add(new VarInsnNode(Opcodes.FSTORE, 13));
        list.add(new VarInsnNode(Opcodes.FLOAD, 14));
        list.add(new InsnNode(Opcodes.FCONST_0));
        list.add(new InsnNode(Opcodes.FCONST_1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraft/util/MathHelper", clamp_float, "(FFF)F", false));
        list.add(new VarInsnNode(Opcodes.FSTORE, 14));
        return list;
    }

    private InsnList getEyePosition() {
        // using srg name in dev crashes? Ok Forge
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.FCONST_1));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/entity/Entity",
            isDevelopment() ? "getPositionEyes" : "func_174824_e",
            "(F)Lnet/minecraft/util/Vec3;",
            false));
        return list;
    }

    private InsnList toggleCullingStatus(boolean status) {
        InsnList list = new InsnList();
        list.add(new InsnNode(status ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        list.add(new FieldInsnNode(Opcodes.PUTSTATIC, "club/sk1er/patcher/util/world/entity/culling/EntityCulling", "uiRendering", "Z"));
        return list;
    }

    private InsnList getStoreCameraInsn(int var) {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, var));
        list.add(new FieldInsnNode(Opcodes.PUTSTATIC,
            "club/sk1er/patcher/util/world/particles/ParticleCulling",
            "camera",
            "Lnet/minecraft/client/renderer/culling/ICamera;"));
        return list;
    }

    private InsnList setLabel(LabelNode ifne) {
        InsnList list = new InsnList();
        list.add(ifne);
        return list;
    }

    private InsnList createLabel(LabelNode ifne) {
        InsnList list = new InsnList();
        list.add(new FieldInsnNode(Opcodes.GETSTATIC, getPatcherConfigClass(), "removeWaterFov", "Z"));
        list.add(new JumpInsnNode(Opcodes.IFNE, ifne));
        return list;
    }

    private InsnList assignCreatedLightmap() {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new InsnNode(Opcodes.ICONST_1));
        list.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/renderer/EntityRenderer", "createdLightmap", "Z"));
        return list;
    }

    private InsnList checkFullbright() {
        InsnList list = new InsnList();
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "club/sk1er/patcher/util/FullbrightTicker", "isFullbright", "()Z", false));
        LabelNode ifeq = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFEQ, ifeq));
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/client/renderer/EntityRenderer", "createdLightmap", "Z"));
        list.add(new JumpInsnNode(Opcodes.IFEQ, ifeq));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(ifeq);
        return list;
    }

    private InsnList changeMethodRedirect() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.ICONST_0));
        list.add(new InsnNode(Opcodes.ICONST_1));
        list.add(new InsnNode(Opcodes.ICONST_1));
        return list;
    }

    private InsnList fixParallax() {
        InsnList list = new InsnList();
        list.add(new FieldInsnNode(Opcodes.GETSTATIC, getPatcherConfigClass(), "parallaxFix", "Z"));
        LabelNode ifeq = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.IFEQ, ifeq));
        list.add(new LdcInsnNode(0.05F));
        LabelNode gotoInsn = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.GOTO, gotoInsn));
        list.add(ifeq);
        list.add(new LdcInsnNode(-0.10000000149011612F));
        list.add(gotoInsn);
        return list;
    }

    private boolean checkNode(AbstractInsnNode node) {
        if (node.getNext() == null) return false;
        if (node.getOpcode() == Opcodes.ICONST_1) {
            AbstractInsnNode next = node.getNext();
            if (next.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode fieldInsn = (FieldInsnNode) next;
                return fieldInsn.name.equals("smoothCamera") || fieldInsn.name.equals("field_74326_T");
            }
        }
        return false;
    }

    private boolean checkDivNode(AbstractInsnNode node) {
        if (node.getOpcode() == Opcodes.FDIV) {
            if (node.getPrevious().getOpcode() == Opcodes.LDC) {
                LdcInsnNode prev = (LdcInsnNode) node.getPrevious();
                if (prev.cst instanceof Float) {
                    Float f = (Float) prev.cst;
                    return f.equals(4f);
                }
            }
        }
        return false;
    }

    private boolean checkZoomActiveNode(AbstractInsnNode node, int zoomActiveIndex) {
        if (node.getOpcode() == Opcodes.ILOAD) {
            VarInsnNode n = (VarInsnNode) node;
            if (n.var == zoomActiveIndex) {
                return node.getNext().getOpcode() == Opcodes.IFEQ;
            }
        }
        return false;
    }

    private InsnList getDivisor() {
        InsnList list = new InsnList();
        list.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "club/sk1er/patcher/tweaker/asm/optifine/EntityRendererTransformer",
                "getModifier",
                "()F",
                false)); // Call my method
        return list;
    }

    private InsnList callReset() {
        InsnList list = new InsnList();
        list.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "club/sk1er/patcher/tweaker/asm/optifine/EntityRendererTransformer",
                "resetCurrent",
                "()V",
                false)); // Call my method
        return list;
    }

    private InsnList setZoomed(int zoomActiveIndex) {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ILOAD, zoomActiveIndex));
        list.add(new FieldInsnNode(Opcodes.PUTSTATIC, "club/sk1er/patcher/tweaker/asm/optifine/EntityRendererTransformer", "zoomed", "Z"));
        return list;
    }
}
