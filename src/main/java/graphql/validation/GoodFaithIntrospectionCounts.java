package graphql.validation;

import graphql.Internal;
import graphql.introspection.GoodFaithIntrospection;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

@Internal
@NullMarked
public final class GoodFaithIntrospectionCounts {
    private int fieldCount;
    private int depth;
    private @Nullable Set<String> coordinates;

    // Initializes a fresh summary for one merged field group and records any restricted coordinate.
    // Checks the current depth before descending, so an overly deep query stops during traversal.
    public void enterField(@Nullable String coordinate, int parentDepth) {
        fieldCount = 1;
        depth = 1;
        if (coordinate != null) {
            coordinates = new LinkedHashSet<>();
            coordinates.add(coordinate);
        }
        checkDepth(parentDepth);
    }

    // Adds another subtree at the same depth: sum fields and take the greatest relative depth.
    // The supplied summary is left unchanged so cached subtrees can contribute at multiple positions.
    public void add(GoodFaithIntrospectionCounts other) {
        fieldCount += other.fieldCount;
        depth = Math.max(depth, other.depth);
        addCoordinates(other);
        checkLimits();
    }

    // Attaches the combined children to this field, adding their fields and one level to their depth.
    // Leaves the children's summary unchanged and checks the accumulated limits immediately.
    public void addChildren(GoodFaithIntrospectionCounts children) {
        fieldCount += children.fieldCount;
        depth = children.depth + 1;
        addCoordinates(children);
        checkLimits();
    }

    // Rejects a second merged occurrence of a restricted coordinate, including across aliases or parents.
    // Copies coordinates when needed so combining counts cannot modify a cached source summary.
    private void addCoordinates(GoodFaithIntrospectionCounts other) {
        if (other.coordinates == null) {
            return;
        }
        if (coordinates == null) {
            coordinates = new LinkedHashSet<>(other.coordinates);
            return;
        }
        for (String coordinate : other.coordinates) {
            if (!coordinates.add(coordinate)) {
                throw GoodFaithIntrospectionExceeded.tooManyFields(coordinate);
            }
        }
    }

    // Rejects a subtree or selection total as soon as it exceeds the field or relative-depth budget.
    private void checkLimits() {
        if (fieldCount > GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT) {
            throw GoodFaithIntrospectionExceeded.tooBigOperation("Query has " + fieldCount + " fields which exceeds maximum allowed "
                    + GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT);
        }
        checkDepth(0);
    }

    // Combines this subtree's relative depth with its position in the operation. This also checks
    // cached subtrees reused under a deeper parent without walking their children again.
    public void checkDepth(int parentDepth) {
        if (parentDepth + depth > GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT) {
            throw GoodFaithIntrospectionExceeded.tooBigOperation("Query depth " + (parentDepth + depth) + " exceeds maximum allowed depth "
                    + GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT);
        }
    }
}
