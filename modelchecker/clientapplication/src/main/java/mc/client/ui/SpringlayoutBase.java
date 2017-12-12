/*
 * Copyright (c) 2003, The JUNG Authors
 * All rights reserved.
 *
 * This software is open-source under the BSD license; see either "license.txt"
 * or https://github.com/jrtom/jung/blob/master/LICENSE for a description.
 */
package mc.client.ui;

import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.util.ConcurrentModificationException;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.algorithms.layout.util.RandomLocationTransformer;
import edu.uci.ics.jung.algorithms.util.IterativeContext;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Pair;
import mc.client.graph.GraphNode;

/**
 * The SpringLayout package represents a visualization of a set of nodes. The
 * SpringLayout, which is initialized with a Graph, assigns X/Y locations to
 * each node. When called <code>relax()</code>, the SpringLayout moves the
 * visualization forward one step.
 *
 * @author Danyel Fisher
 * @author Joshua O'Madadhain
 */
public class SpringlayoutBase<V, E> extends AbstractLayout<V,E> implements IterativeContext {

    protected double stretch = 0.70;
    protected Function<? super E, Integer> lengthFunction;
    protected int repulsion_range_sq = 100 * 100;
    protected double force_multiplier = 1.0 / 3.0;
    protected boolean done = false;

    protected LoadingCache<V, SpringVertexData> springVertexData =
            CacheBuilder.newBuilder().build(new CacheLoader<V, SpringVertexData>() {
                public SpringVertexData load(V vertex) {
                    return new SpringVertexData();
                }
            });

    /**
     * Constructor for a SpringLayout for a raw graph with associated
     * dimension--the input knows how big the graph is. Defaults to the unit
     * length function.
     * @param g the graph on which the layout algorithm is to operate
     */
    @SuppressWarnings("unchecked")
    public SpringlayoutBase(Graph<V,E> g) {
        this(g, (Function<E,Integer>)Functions.<Integer>constant(30));
    }

    /**
     * Constructor for a SpringLayout for a raw graph with associated component.
     *
     * @param g the graph on which the layout algorithm is to operate
     * @param length_function provides a length for each edge
     */
    public SpringlayoutBase(Graph<V,E> g, Function<? super E, Integer> length_function)
    {
        super(g);
        this.lengthFunction = length_function;
    }

    /**
     * @return the current value for the stretch parameter
     */
    public double getStretch() {
        return stretch;
    }

    @Override
    public void setSize(Dimension size) {
        if(initialized == false)
            setInitializer(new RandomLocationTransformer<V>(size));
        super.setSize(size);
    }

    /**
     * <p>Sets the stretch parameter for this instance.  This value
     * specifies how much the degrees of an edge's incident vertices
     * should influence how easily the endpoints of that edge
     * can move (that is, that edge's tendency to change its length).
     *
     * <p>The default value is 0.70.  Positive values less than 1 cause
     * high-degree vertices to move less than low-degree vertices, and
     * values &gt; 1 cause high-degree vertices to move more than
     * low-degree vertices.  Negative values will have unpredictable
     * and inconsistent results.
     * @param stretch the stretch parameter
     */
    public void setStretch(double stretch) {
        this.stretch = stretch;
    }

    public int getRepulsionRange() {
        return (int)(Math.sqrt(repulsion_range_sq));
    }

    /**
     * Sets the node repulsion range (in drawing area units) for this instance.
     * Outside this range, nodes do not repel each other.  The default value
     * is 100.  Negative values are treated as their positive equivalents.
     * @param range the maximum repulsion range
     */
    public void setRepulsionRange(int range) {
        this.repulsion_range_sq = range * range;
    }

    public double getForceMultiplier() {
        return force_multiplier;
    }

    /**
     * Sets the force multiplier for this instance.  This value is used to
     * specify how strongly an edge "wants" to be its default length
     * (higher values indicate a greater attraction for the default length),
     * which affects how much its endpoints move at each timestep.
     * The default value is 1/3.  A value of 0 turns off any attempt by the
     * layout to cause edges to conform to the default length.  Negative
     * values cause long edges to get longer and short edges to get shorter; use
     * at your own risk.
     * @param force an energy field created by all living things that binds the galaxy together
     */
    public void setForceMultiplier(double force) {
        this.force_multiplier = force;
    }

    public void initialize() {



    }

    /**
     * Relaxation step. Moves all nodes a smidge.
     */
    public void step() {
        try {
            for(V v : getGraph().getVertices()) {
                SpringVertexData svd = springVertexData.getUnchecked(v);
                if (svd == null) {
                    continue;
                }
                svd.dx /= 4;
                svd.dy /= 4;
                svd.edgedx = svd.edgedy = 0;
                svd.repulsiondx = svd.repulsiondy = 0;
            }
        } catch(ConcurrentModificationException cme) {
            step();
        }

        relaxEdges();
        calculateRepulsion();
        moveNodes();
    }

    protected void relaxEdges() {
        try {
            for(E e : getGraph().getEdges()) {
                Pair<V> endpoints = getGraph().getEndpoints(e);
                V v1 = endpoints.getFirst();
                V v2 = endpoints.getSecond();

                Point2D p1 = apply(v1);
                Point2D p2 = apply(v2);
                if(p1 == null || p2 == null) continue;
                double vx = p1.getX() - p2.getX();
                double vy = p1.getY() - p2.getY();
                double len = Math.sqrt(vx * vx + vy * vy);

                double desiredLen = 100;

                // round from zero, if needed [zero would be Bad.].
                len = (len == 0) ? .0001 : len;

                double f = force_multiplier * (desiredLen - len) / len;

                f = f * Math.pow(stretch, (getGraph().degree(v1) + getGraph().degree(v2) - 2));

                // the actual movement distance 'dx' is the force multiplied by the
                // distance to go.
                double dx = f * vx;
                double dy = f * vy;
                SpringVertexData v1D, v2D;
                v1D = springVertexData.getUnchecked(v1);
                v2D = springVertexData.getUnchecked(v2);

                v1D.edgedx += dx;
                v1D.edgedy += dy;
                v2D.edgedx += -dx;
                v2D.edgedy += -dy;
            }
        } catch(ConcurrentModificationException cme) {
            relaxEdges();
        }
    }

    protected void calculateRepulsion() {
        try {
            for (V v : getGraph().getVertices()) {
                if (isLocked(v)) continue;

                SpringVertexData svd = springVertexData.getUnchecked(v);
                if(svd == null) continue;
                double dx = 0, dy = 0;

                for (V v2 : getGraph().getVertices()) {
                    if (v == v2 || !((GraphNode)v).getAutomata().equals(((GraphNode)v2).getAutomata())) continue;
                    Point2D p = apply(v);
                    Point2D p2 = apply(v2);
                    if(p == null || p2 == null) continue;
                    double vx = p.getX() - p2.getX();
                    double vy = p.getY() - p2.getY();
                    double distanceSq = p.distanceSq(p2);
                    if (distanceSq == 0) {
                        dx += Math.random();
                        dy += Math.random();
                    } else if (distanceSq < repulsion_range_sq) {
                        double factor = 1;

                        dx += factor * vx / distanceSq;
                        dy += factor * vy / distanceSq;
                    }
                }
                double dlen = dx * dx + dy * dy;
                if (dlen > 0) {
                    dlen = Math.sqrt(dlen) / 2;
                    svd.repulsiondx += dx / dlen;
                    svd.repulsiondy += dy / dlen;
                }
            }
        } catch(ConcurrentModificationException cme) {
            calculateRepulsion();
        }
    }

    protected void moveNodes()
    {
        synchronized (getSize()) {
            try {
                for (V v : getGraph().getVertices()) {
                    if (isLocked(v)) continue;
                    SpringVertexData vd = springVertexData.getUnchecked(v);
                    if(vd == null) continue;
                    Point2D xyd = apply(v);

                    vd.dx += vd.repulsiondx + vd.edgedx;
                    vd.dy += vd.repulsiondy + vd.edgedy;

                    // keeps nodes from moving any faster than 5 per time unit
                    xyd.setLocation(xyd.getX()+Math.max(-5, Math.min(5, vd.dx)),
                            xyd.getY()+Math.max(-5, Math.min(5, vd.dy)));

                }
            } catch(ConcurrentModificationException cme) {
                moveNodes();
            }
        }
    }

    protected static class SpringVertexData {
        protected double edgedx;
        protected double edgedy;
        protected double repulsiondx;
        protected double repulsiondy;

        /** movement speed, x */
        protected double dx;

        /** movement speed, y */
        protected double dy;
    }


    /**
     * Used for changing the size of the layout in response to a component's size.
     */
    public class SpringDimensionChecker extends ComponentAdapter {
        @Override
        public void componentResized(ComponentEvent e) {
            setSize(e.getComponent().getSize());
        }
    }

    /**
     * @return true
     */
    public boolean isIncremental() {
        return true;
    }

    /**
     * @return false
     */
    public boolean done() {
        return done;
    }

    //Means we can continue
    public void setDone(boolean val) {done = val;}

    /**
     * No effect.
     */
    public void reset() {
    }
}