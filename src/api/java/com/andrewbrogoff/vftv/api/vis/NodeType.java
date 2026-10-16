package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * A node type: an entry of the synced datapack registry {@code vftv:node_type} ({@link
 * VisKeys#NODE_TYPE_REGISTRY}), a node's aspects, clarity weights and spread rules. See the wands
 * spec, "Node types".
 *
 * <p>A marker in v1: it gives id and holder access only, and an addon reads the entry through its
 * JSON. Accessors may arrive in a minor as {@code default} methods.
 *
 * <p>Not for implementation: vftv implements this interface; an addon only reads it.
 */
@Stable
public interface NodeType {}
