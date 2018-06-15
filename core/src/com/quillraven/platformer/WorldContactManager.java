package com.quillraven.platformer;
/*
 * Created by Quillraven on 04.06.2018.
 *
 * MIT License
 *
 * Copyright (c) 2018 Quillraven
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.WorldManifold;
import com.badlogic.gdx.utils.Array;

/**
 * TODO add class description
 */

public class WorldContactManager implements ContactListener {
    private static final WorldContactManager instance = new WorldContactManager();
    private final Array<GameContactListener> listeners;

    private WorldContactManager() {
        this.listeners = new Array<>();
    }

    public static WorldContactManager getInstance() {
        return instance;
    }

    public void addGameContactListener(final GameContactListener listener) {
        this.listeners.add(listener);
    }

    public void removeGameContactListener(final GameContactListener listener) {
        this.listeners.removeValue(listener, false);
    }

    @Override
    public void beginContact(final Contact contact) {
        final Fixture fixtureA = contact.getFixtureA();
        final Fixture fixtureB = contact.getFixtureB();

        if ("foot".equals(fixtureA.getUserData()) && fixtureB.getFilterData().categoryBits == Platformer.BIT_GROUND) {
            for (GameContactListener listener : listeners) {
                listener.onBeginGroundContact((Entity) fixtureA.getBody().getUserData());
            }
        } else if ("foot".equals(fixtureB.getUserData()) && fixtureA.getFilterData().categoryBits == Platformer.BIT_GROUND) {
            for (GameContactListener listener : listeners) {
                listener.onBeginGroundContact((Entity) fixtureB.getBody().getUserData());
            }
        } else if ("body".equals(fixtureA.getUserData()) && fixtureB.getFilterData().categoryBits == Platformer.BIT_OBJECT) {
            for (GameContactListener listener : listeners) {
                listener.onBeginEntityContact((Entity) fixtureA.getBody().getUserData(), (Entity) fixtureB.getBody().getUserData());
            }
        } else if ("body".equals(fixtureB.getUserData()) && fixtureA.getFilterData().categoryBits == Platformer.BIT_OBJECT) {
            for (GameContactListener listener : listeners) {
                listener.onBeginEntityContact((Entity) fixtureA.getBody().getUserData(), (Entity) fixtureB.getBody().getUserData());
            }
        }
    }

    @Override
    public void endContact(final Contact contact) {
        final Fixture fixtureA = contact.getFixtureA();
        final Fixture fixtureB = contact.getFixtureB();

        if ("foot".equals(fixtureA.getUserData()) && fixtureB.getFilterData().categoryBits == Platformer.BIT_GROUND) {
            for (GameContactListener listener : listeners) {
                listener.onEndGroundContact((Entity) fixtureA.getBody().getUserData());
            }
        } else if ("foot".equals(fixtureB.getUserData()) && fixtureA.getFilterData().categoryBits == Platformer.BIT_GROUND) {
            for (GameContactListener listener : listeners) {
                listener.onEndGroundContact((Entity) fixtureB.getBody().getUserData());
            }
        } else if ("body".equals(fixtureA.getUserData()) && fixtureB.getFilterData().categoryBits == Platformer.BIT_OBJECT) {
            for (GameContactListener listener : listeners) {
                listener.onEndEntityContact((Entity) fixtureA.getBody().getUserData(), (Entity) fixtureB.getBody().getUserData());
            }
        } else if ("body".equals(fixtureB.getUserData()) && fixtureA.getFilterData().categoryBits == Platformer.BIT_OBJECT) {
            for (GameContactListener listener : listeners) {
                listener.onEndEntityContact((Entity) fixtureA.getBody().getUserData(), (Entity) fixtureB.getBody().getUserData());
            }
        }
    }

    @Override
    public void preSolve(final Contact contact, final Manifold oldManifold) {
        final Fixture fixtureA = contact.getFixtureA();
        final Fixture fixtureB = contact.getFixtureB();
        final Fixture playerFixture;
        final Fixture groundFixture;

        if ("body".equals(fixtureB.getUserData()) && fixtureA.getFilterData().categoryBits == Platformer.BIT_GROUND) {
            playerFixture = fixtureB;
            groundFixture = fixtureA;
        } else if ("body".equals(fixtureA.getUserData()) && fixtureB.getFilterData().categoryBits == Platformer.BIT_GROUND) {
            playerFixture = fixtureA;
            groundFixture = fixtureB;
        } else {
            return;
        }

        final WorldManifold manifold = contact.getWorldManifold();
        final Vector2[] points = manifold.getPoints();
        final Body playerBody = playerFixture.getBody();
        final Body groundBody = groundFixture.getBody();
        for (int i = 0; i < points.length; ++i) {
            final Vector2 groundVelocity = groundBody.getLinearVelocityFromWorldPoint(points[i]);
            final Vector2 playerVelocity = playerBody.getLinearVelocityFromWorldPoint(points[i]);
            // check should actually be < 0 but if the player moves up a slope then the velocity becomes > 0 and a "stutter movement" is happening
            // --> value 5 is used to jump through platforms and to walk up slopes normally
            if (groundBody.getLocalVector(playerVelocity.sub(groundVelocity)).y <= 5) {
                // point is moving into platform
                return;
            }
        }

        // player is moving upwards and a ground is over him --> walk through it
        contact.setEnabled(false);
    }

    @Override
    public void postSolve(final Contact contact, final ContactImpulse impulse) {
    }


    public interface GameContactListener {
        void onBeginGroundContact(final Entity entity);

        void onEndGroundContact(final Entity entity);

        void onBeginEntityContact(final Entity entityA, final Entity entityB);

        void onEndEntityContact(final Entity entityA, final Entity entityB);
    }
}
