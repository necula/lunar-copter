package com.LunarCopter;

import java.util.Random;
import java.util.LinkedList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.KeyEvent;

public class CopterView extends SurfaceView implements SurfaceHolder.Callback {

	class Tile {
		public float x, y;
		public int size;

		public Tile(float x, float y) {
			this.x = x;
			this.y = y;
		}
		public Tile(float x, float y, int size) {
			this.x = x;
			this.y = y;
			this.size = size;
		}
	}

	static final int SCROLLSPEED = 5;
	static final int nTiles = 32; // Number of tiles
	static final int nBlocks = 2; // Number of blocks
	
	// Game states
	static final int READY = 0;
	static final int RUNNING = 1;
	static final int GAMEOVER = 2;

	static final String GAMEOVER1 = "Gravity's a bitch, ain't it? Try again!";
	static final String GAMEOVER2 = "Go watch some M.A.S.H. first";
	static final String READY1 = "Hold to go up";
	static final String READY2 = "Release to go down";

	LinkedList<Tile> tileList = new LinkedList<Tile>();
	LinkedList<Tile> blockList = new LinkedList<Tile>();

	Tile currTile, prevTile;
	// Tile generator variables
	int tileWidth, elapsedTiles = 0;
	float flyZoneHeight;
	float tileMaxHeight, tileMinHeight;
	float finalHeight = 0;
	float currentHeight = 0;

	Random random;

	private CopterThread _thread;
	private Paint normalPaint, textPaint;

	// Sound variables
	private SoundPool soundPool;
	private int goSound, explosionSound;
	boolean goSoundPlaying = false;
	int goStream;

	Bitmap backgroundImage, smokeImage, crashedHelicopter;
	LinkedList<Tile> smokeTrail = new LinkedList<Tile>();

	// Screen H/W
	int canvasHeight, canvasWidth;
	
	Drawable helicopter;
	float helicopterX = 0, helicopterY = 0;
	int helicopterWidth, helicopterHeight;

	boolean PRESSED = false; // True if keyDown or touch event
	final float ACC = -0.6f; // Acceleration
	final float GRV = 0.9f; // Gravity - well, not so lunar
	final float ACCLIMIT = -2f; // Acceleration limit
	final float GRVLIMIT = 3f; // Gravity limit
	float velocity = 0.0f; // Helicopter (vertical) speed

	// Scores
	float highScore = 0.0f;
	float currentScore = 0.0f;

	int state = READY; // Current game state

	boolean skipInput = false; // Sometimes we have to skip 1 onKeyUp/onTouchUp event
	String message; // Here we keep what text we draw on screen

	public CopterView(Context context) {
		super(context);

		random = new Random();
		
		backgroundImage = BitmapFactory.decodeResource(getResources(),
				R.drawable.background);
		smokeImage = BitmapFactory.decodeResource(getResources(),
				R.drawable.smoke);
		crashedHelicopter = BitmapFactory.decodeResource(getResources(),
				R.drawable.helicopter_crashed);
		
		helicopter = context.getResources().getDrawable(R.drawable.helicopter);
		helicopterWidth = helicopter.getIntrinsicWidth();
		helicopterHeight = helicopter.getIntrinsicHeight();
		
		soundPool = new SoundPool(16, AudioManager.STREAM_MUSIC, 100);
		goSound = soundPool.load(getContext(), R.raw.helicopter, 0);
		explosionSound = soundPool.load(getContext(), R.raw.explosion, 0);

		// Paint we use to draw tiles/blocks
		normalPaint = new Paint();
		normalPaint.setAntiAlias(true);
		normalPaint.setARGB(200, 200, 200, 200);
		
		// Paint we use to draw text
		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setARGB(255, 255, 0, 0);

		getHolder().addCallback(this);
		_thread = new CopterThread(this);
		setFocusable(true);
	}

	public void init() {
		
		helicopterY = canvasHeight / 2;
		helicopterX = canvasWidth / 5;

		currentScore = 0;
		
		// Tile generator variables
		flyZoneHeight = ((float) canvasHeight) * 5 / 7;
		tileMaxHeight = ((float) canvasHeight) * 1 / 7;
		tileMinHeight = tileMaxHeight / 4;
		tileWidth = canvasWidth / (nTiles - 1);
		
		// Add start up tiles
		tileList.clear();
		for (int i = 0; i < nTiles + 1; i++) {

			tileList.add(new Tile(i * tileWidth, tileMaxHeight));
		}
		currentHeight = tileList.get(nTiles - 1).y;

		blockList.clear();
		smokeTrail.clear();

		state = READY;
	}
	
	// 0 - touch down/ key down
	// 1 - touch up/ key up
	public void processInput(int inputType) {
		if(inputType == 0) {
			switch (state) {
			case RUNNING: 
				goStream = soundPool.play(goSound, 1, 1, 0, 0, 0.5f); // Engine sound
				break;
			case READY: 
				state = RUNNING;
				break;
			case GAMEOVER: 
				// Do nothing
				break;
			}
			
			PRESSED = true;
		}
		else {
			soundPool.stop(goStream);
			PRESSED = false;
			
			if(skipInput) {
				skipInput = false;
				return;
			}
			
			switch (state) {
			case RUNNING:
				// Do nothing
				break;
			case READY:
				// Do nothing
				break;
			case GAMEOVER: {
				state = READY;
				init();
			}
				break;
			}
		}
	}
	
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		synchronized (getHolder()) {
			if (event.getAction() == MotionEvent.ACTION_DOWN)
				processInput(0);
			else if (event.getAction() == MotionEvent.ACTION_UP)
				processInput(1);
			
			return true;
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent msg) {
		
		synchronized (getHolder()) {
			processInput(0);
		}
		
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent msg) {
		synchronized (getHolder()) {
			processInput(1);
		}
		
		return true;
	}

	public void update() {

		if (state == RUNNING) {

			currentScore += 0.5f; // Update score

			if (PRESSED)
				velocity += ACC; // Going up
			else
				velocity += GRV; // Going down

			// Keep velocity within limits
			if (velocity < ACCLIMIT)
				velocity = ACCLIMIT;

			if (velocity > GRVLIMIT)
				velocity = GRVLIMIT;

			helicopterY += velocity; // Update position

			// Scroll tiles
			for (int i = 0; i < nTiles; i++) {
				tileList.get(i).x -= SCROLLSPEED;
			}

			// Need new tile?
			if (tileList.get(0).x < 0) {
				elapsedTiles++;
				
				// Generate tile height
				if (Math.abs(finalHeight - currentHeight) < 5) {
					int scale = random.nextInt(10);

					if (scale == 0) // 10% chance
						finalHeight = tileMinHeight;
					else if (scale == 1) // 10% chance
						finalHeight = tileMaxHeight;
					else if (scale > 1 && scale < 6) // 40% chance
						finalHeight += random.nextInt(50);
					else if (scale > 5 && scale < 10) // 40% chance
						finalHeight -= random.nextInt(50);

					if (finalHeight < tileMinHeight)
						finalHeight = tileMinHeight;
					if (finalHeight > tileMaxHeight)
						finalHeight = tileMaxHeight;
				}

				if (finalHeight - currentHeight > 0)
					currentHeight *= 1.2;
				else
					currentHeight *= 0.8;

				tileList.remove();
				tileList.add(new Tile(canvasWidth, currentHeight));
				
				// Once every half screen scrolled we put a new block
				if (elapsedTiles == nTiles / 2) {
					if (blockList.size() == 2)
						blockList.remove();
					blockList.add(new Tile(canvasWidth, tileList.getLast().y
							+ random.nextFloat() * (flyZoneHeight - 75),
							50 + random.nextInt(50)));
					elapsedTiles = 0;
				}
				
				// Add some smoke too
				smokeTrail.add(new Tile(helicopterX, helicopterY));

			}
			
			// Scroll blocks
			for (int i = 0; i < blockList.size(); i++) {
				blockList.get(i).x -= SCROLLSPEED;

			}
			// Scroll smoke
			for (Tile t : smokeTrail) {
				t.x -= SCROLLSPEED;
			}

			// Collision detection

			boolean collision = false;

			// First check tiles
			for (int i = 6; i < 10; i++) {
				float refY = tileList.get(i).y;
				if (helicopterY < refY
						|| ((helicopterY + helicopterHeight) > (refY + flyZoneHeight)))
					collision = true;
			}

			// Then check nearest block
			if (!blockList.isEmpty()) {
				Rect b = new Rect((int) blockList.get(0).x - tileWidth,
						(int) blockList.get(0).y, (int) blockList.get(0).x,
						(int) blockList.get(0).y + blockList.get(0).size);
				if (Rect.intersects(helicopter.getBounds(), b))
					collision = true;
			}

			if (collision) {
				soundPool.play(explosionSound, 1, 1, 0, 0, 1); // Boom!
				soundPool.stop(goStream); // Stop engine sound
				state = GAMEOVER; // Update state
				
				// New highscore?
				if (currentScore > highScore)
					highScore = currentScore;
				
				// Choose between 2 game over messages
				if (random.nextInt(2) == 0)
					message = GAMEOVER1;
				else
					message = GAMEOVER2;
				
				if(PRESSED)
					skipInput = true;
			}

		}

	}

	@Override
	public void onDraw(Canvas canvas) {

		// Draw background
		canvas.drawBitmap(backgroundImage, 0, 0, null);

		// Draw tiles
		currTile = tileList.get(0);
		canvas.drawRect(currTile.x - tileWidth, 0, currTile.x, currTile.y,
				normalPaint);
		canvas.drawRect(currTile.x - tileWidth, flyZoneHeight + currTile.y,
				currTile.x, canvasHeight, normalPaint);
		prevTile = currTile;
		for (int i = 1; i < nTiles + 1; i++) {
			currTile = tileList.get(i);
			canvas.drawRect(prevTile.x, 0, currTile.x, currTile.y, normalPaint);
			canvas.drawRect(prevTile.x, flyZoneHeight + currTile.y, currTile.x,
					canvasHeight, normalPaint);
			prevTile = currTile;
		}

		// Draw blocks
		for (Tile t : blockList)
			canvas.drawRect(t.x - tileWidth, t.y, t.x, t.y + t.size,
					normalPaint);

		// Draw smoke trail
		for (Tile t : smokeTrail)
			canvas.drawBitmap(smokeImage, t.x, t.y, null);

		if (state == RUNNING || state == READY) {
			canvas.save();
			// Rotate if under acceleration
			if (PRESSED)
				canvas.rotate(-10, (int) helicopterX + helicopterWidth / 2,
						(int) helicopterY + helicopterHeight / 2);
			// Draw helicopter
			helicopter.setBounds((int) helicopterX, (int) helicopterY,
					(int) helicopterX + helicopterWidth, (int) helicopterY
							+ helicopterHeight);
			helicopter.draw(canvas);
			canvas.restore();
		} else if (state == GAMEOVER) {
			canvas.drawBitmap(crashedHelicopter, (int) helicopterX,
					(int) helicopterY, null);
		}

		canvas.drawText("Score: " + ((int) currentScore),
				(int) (canvasWidth * 0.1), canvasHeight - 20, textPaint);
		canvas.drawText("High Score: " + ((int) highScore),
				(int) (canvasWidth * 0.75), canvasHeight - 20, textPaint);

		if (state == GAMEOVER) {

			canvas.drawText(message, canvasWidth / 2 - message.length() * 2,
					canvasHeight / 2, textPaint);
		} else if (state == READY) {
			canvas.drawText(READY1, canvasWidth / 2 - READY1.length() * 2,
					canvasHeight / 2 - 10, textPaint);
			canvas.drawText(READY2, canvasWidth / 2 - READY2.length() * 2,
					canvasHeight / 2 + 10, textPaint);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		synchronized (holder) {
			canvasWidth = width;
			canvasHeight = height;

			backgroundImage = Bitmap.createScaledBitmap(backgroundImage, width,
					height, true);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!_thread.isAlive()) {
			_thread = new CopterThread(this);
		}
		_thread.setRunning(true);
		_thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		_thread.setRunning(false);
		while (retry) {
			try {
				_thread.join();
				retry = false;
			} catch (InterruptedException e) {
				
			}
		}
	}
}