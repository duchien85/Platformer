package com.quillraven.platformer.ecs.system;
/*
 * Created by Quillraven on 10.06.2018.
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

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.quillraven.platformer.ParticleEffectManager;
import com.quillraven.platformer.ecs.EntityEngine;
import com.quillraven.platformer.ecs.component.AnimationComponent;
import com.quillraven.platformer.ecs.component.Box2DComponent;
import com.quillraven.platformer.map.Map;
import com.quillraven.platformer.map.MapManager;
import com.quillraven.platformer.map.MapRenderer;
import com.quillraven.platformer.ui.AnimationManager;

import box2dLight.RayHandler;

import static com.quillraven.platformer.Platformer.PPM;

/**
 * TODO add class description and use SortedIteratingSystem
 */
public class GameRenderSystem extends RenderSystem implements MapManager.MapListener {
    private final static String TAG = GameRenderSystem.class.getSimpleName();
    private final MapRenderer mapRenderer;
    private final Family renderFamily;
    private final ComponentMapper<Box2DComponent> b2dCmpMapper;
    private final ComponentMapper<AnimationComponent> aniCmpMapper;
    private final RayHandler rayHandler;
    private float mapWidth;
    private float mapHeight;
    private int[] bgdLayerIdx;
    private int[] fgdLayerIdx;
    private int[] cloudIdx;
    private int[] groundIdx;

    public GameRenderSystem(final EntityEngine engine, final SpriteBatch spriteBatch, final RayHandler rayHandler, final ComponentMapper<Box2DComponent> b2dCmpMapper, final ComponentMapper<AnimationComponent> aniCmpMapper) {
        super(engine);
        this.rayHandler = rayHandler;
        MapManager.getInstance().addMapListener(this);
        mapRenderer = new MapRenderer(spriteBatch);
        this.renderFamily = Family.all(AnimationComponent.class, Box2DComponent.class).get();
        this.b2dCmpMapper = b2dCmpMapper;
        this.aniCmpMapper = aniCmpMapper;
    }

    @Override
    public void onRender(final SpriteBatch spriteBatch, final Camera camera, final float alpha) {
        final ImmutableArray<Entity> animatedEntities = engine.getEntitiesFor(renderFamily);

        final Entity player = engine.getPlayer();
        if (player != null) {
            final Box2DComponent b2dCmp = b2dCmpMapper.get(player);
            final AnimationComponent aniCmp = aniCmpMapper.get(player);
            final Vector2 playerPos = b2dCmp.body.getPosition();
            final float invertAlpha = 1.0f - alpha;
            final float x = (playerPos.x * alpha + b2dCmp.positionBeforeUpdate.x * invertAlpha) - (aniCmp.width / PPM / 2);
            final float y = (playerPos.y * alpha + b2dCmp.positionBeforeUpdate.y * invertAlpha) - (aniCmp.height / PPM / 2);
            final float camWidth = camera.viewportWidth * 0.5f;
            final float camHeight = camera.viewportHeight * 0.5f;
            camera.position.set(Math.min(mapWidth - camWidth, Math.max(x, camWidth)), Math.min(mapHeight - camHeight, Math.max(y, camHeight)), 0);
            camera.update();
        }

        spriteBatch.begin();
        if (mapRenderer.getMap() != null) {
            mapRenderer.setView((OrthographicCamera) camera);
            mapRenderer.render(bgdLayerIdx);

            // paralax map effect with clouds
            final float camX = camera.position.x;
            camera.position.x *= 0.7f;
            camera.update();
            mapRenderer.setView((OrthographicCamera) camera);
            mapRenderer.render(cloudIdx);

            camera.position.x = camX;
            camera.update();
            mapRenderer.setView((OrthographicCamera) camera);
            mapRenderer.render(groundIdx);
        }

        for (final Entity entity : animatedEntities) {
            final Box2DComponent b2dCmp = b2dCmpMapper.get(entity);
            final Vector2 position = b2dCmp.body.getPosition();
            final AnimationComponent aniCmp = aniCmpMapper.get(entity);
            final float invertAlpha = 1.0f - alpha;

            // calculate interpolated position for rendering
            final float x = (position.x * alpha + b2dCmp.positionBeforeUpdate.x * invertAlpha) - (b2dCmp.width * 0.5f);
            final float y = (position.y * alpha + b2dCmp.positionBeforeUpdate.y * invertAlpha) - (b2dCmp.height * 0.5f);

            final Animation<Sprite> animation = AnimationManager.getInstance().getAnimation(aniCmp.aniType);
            final Sprite frame = animation.getKeyFrame(aniCmp.animationTime, true);
            frame.setColor(Color.WHITE);
            frame.setFlip(aniCmp.flipHoricontal, false);
            frame.setOriginCenter();
            if (b2dCmp.body.getLinearVelocity().y >= 5) {
                // jumping
                frame.setRotation(0);
            } else {
                frame.setRotation(b2dCmp.numGroundContactsLeft == 0 && b2dCmp.numGroundContactsRight > 0 ? 40 : b2dCmp.numGroundContactsLeft > 0 && b2dCmp.numGroundContactsRight == 0 ? 320 : 0);
            }
            frame.setBounds(x - (aniCmp.width - b2dCmp.width) * 0.5f, frame.getRotation() == 0 ? y - 2f / PPM + aniCmp.offsetY : y - b2dCmp.height * 0.2f + aniCmp.offsetY, aniCmp.width, aniCmp.height);

            spriteBatch.draw(frame.getTexture(), frame.getVertices(), 0, 20);
        }

        if (mapRenderer.getMap() != null) {
            mapRenderer.render(fgdLayerIdx);
        }

        // render particle effects
        final Array<ParticleEffectPool.PooledEffect> effects = ParticleEffectManager.getInstance().getEffects();
        for (int i = effects.size - 1; i >= 0; --i) {
            final ParticleEffectPool.PooledEffect effect = effects.get(i);
            effect.draw(spriteBatch, Gdx.graphics.getDeltaTime());
            if (effect.isComplete()) {
                effect.free();
                effects.removeIndex(i);
            }
        }
        spriteBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        spriteBatch.end();

        rayHandler.setCombinedMatrix((OrthographicCamera) camera);
        rayHandler.updateAndRender();
    }

    @Override
    public void onMapChanged(final Map map, final TiledMap tiledMap) {
        Gdx.app.debug(TAG, "Changing map for MapRenderer: " + map.getMapType());
        mapWidth = map.getWidth();
        mapHeight = map.getHeight();
        bgdLayerIdx = new int[map.getBackgroundLayerIndex().length];
        for (int i = 0; i < map.getBackgroundLayerIndex().length; ++i) {
            bgdLayerIdx[i] = map.getBackgroundLayerIndex()[i];
        }
        fgdLayerIdx = new int[map.getForegroundLayerIndex().length];
        for (int i = 0; i < map.getForegroundLayerIndex().length; ++i) {
            fgdLayerIdx[i] = map.getForegroundLayerIndex()[i];
        }
        cloudIdx = new int[map.getCloudsIdx().length];
        for (int i = 0; i < map.getCloudsIdx().length; ++i) {
            cloudIdx[i] = map.getCloudsIdx()[i];
        }
        groundIdx = new int[map.getGroundIdx().length];
        for (int i = 0; i < map.getGroundIdx().length; ++i) {
            groundIdx[i] = map.getGroundIdx()[i];
        }
        mapRenderer.setMap(tiledMap);
    }

    @Override
    public void onDispose() {
        mapRenderer.dispose();
    }
}
