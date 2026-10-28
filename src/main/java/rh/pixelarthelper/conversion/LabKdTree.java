package rh.pixelarthelper.conversion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import rh.pixelarthelper.palette.PaletteEntry;
import rh.pixelarthelper.util.ColorUtil;

/** Three-dimensional k-d tree over CIELAB palette entries. */
public final class LabKdTree {

    private static final double NON_CUBE_PENALTY = 8.0;

    private final Node root;

    public LabKdTree(List<PaletteEntry> entries) {
        List<PaletteEntry> usable = new ArrayList<>();
        for (PaletteEntry entry : entries) {
            if (entry != null && entry.lab != null && entry.lab.length == 3) usable.add(entry);
        }
        this.root = build(usable.toArray(new PaletteEntry[0]), 0, usable.size(), 0);
    }

    public PaletteEntry nearest(double[] lab) {
        if (root == null) return null;
        Nearest nearest = new Nearest();
        visit(root, lab, nearest);
        return nearest.entry;
    }

    private Node build(PaletteEntry[] entries, int from, int to, int depth) {
        if (from >= to) return null;
        int axis = depth % 3;
        final int currentAxis = axis;
        Arrays.sort(entries, from, to, new Comparator<PaletteEntry>() {

            @Override
            public int compare(PaletteEntry a, PaletteEntry b) {
                return Double.compare(a.lab[currentAxis], b.lab[currentAxis]);
            }
        });
        int mid = (from + to) >>> 1;
        Node node = new Node(entries[mid], axis);
        node.left = build(entries, from, mid, depth + 1);
        node.right = build(entries, mid + 1, to, depth + 1);
        return node;
    }

    private void visit(Node node, double[] target, Nearest best) {
        if (node == null) return;

        double distance = ColorUtil.distanceSquared(target, node.entry.lab);
        double score = distance + (node.entry.fullCube ? 0.0 : NON_CUBE_PENALTY);
        if (best.entry == null || score < best.score) {
            best.entry = node.entry;
            best.score = score;
        }

        double delta = target[node.axis] - node.entry.lab[node.axis];
        Node near = delta < 0.0 ? node.left : node.right;
        Node far = delta < 0.0 ? node.right : node.left;
        visit(near, target, best);
        if (delta * delta < best.score) visit(far, target, best);
    }

    private static final class Node {

        final PaletteEntry entry;
        final int axis;
        Node left;
        Node right;

        Node(PaletteEntry entry, int axis) {
            this.entry = entry;
            this.axis = axis;
        }
    }

    private static final class Nearest {

        PaletteEntry entry;
        double score = Double.MAX_VALUE;
    }
}
